package kr.co.mcedu.group.service.impl;

import kr.co.mcedu.broker.EventBroker;
import kr.co.mcedu.broker.model.RefreshSummonerEvent;
import kr.co.mcedu.config.exception.AccessDeniedException;
import kr.co.mcedu.config.exception.AlreadyDataExistException;
import kr.co.mcedu.config.exception.DataNotExistException;
import kr.co.mcedu.config.exception.ServiceException;
import kr.co.mcedu.group.entity.*;
import kr.co.mcedu.group.model.GroupAuthDto;
import kr.co.mcedu.group.model.GroupResponse;
import kr.co.mcedu.group.model.GroupSaveRequest;
import kr.co.mcedu.group.model.request.*;
import kr.co.mcedu.group.model.response.CustomUserResponse;
import kr.co.mcedu.group.model.response.GroupSeasonResponse;
import kr.co.mcedu.group.repository.CustomUserRepository;
import kr.co.mcedu.group.repository.GroupAuthRepository;
import kr.co.mcedu.group.repository.GroupManageRepository;
import kr.co.mcedu.group.repository.GroupRepository;
import kr.co.mcedu.group.service.GroupResultService;
import kr.co.mcedu.group.service.GroupService;
import kr.co.mcedu.match.entity.CustomMatchEntity;
import kr.co.mcedu.match.repository.CustomMatchRepository;
import kr.co.mcedu.match.repository.MatchAttendeesRepository;
import kr.co.mcedu.summoner.entity.SummonerEntity;
import kr.co.mcedu.summoner.service.SummonerService;
import kr.co.mcedu.user.entity.WebUserEntity;
import kr.co.mcedu.user.service.WebUserService;
import kr.co.mcedu.utils.LocalCacheManager;
import kr.co.mcedu.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private final GroupRepository groupRepository;
    private final MatchAttendeesRepository matchAttendeesRepository;
    private final CustomUserRepository customUserRepository;
    private final CustomMatchRepository customMatchRepository;
    private final LocalCacheManager cacheManager;
    private final GroupAuthRepository groupAuthRepository;
    private final WebUserService webUserService;
    private final SummonerService summonerService;
    private final GroupManageRepository groupManageRepository;
    private final GroupResultService groupResultService;
    private final EventBroker broker;

    /**
     * groupSeq??? ???????????? ?????? GroupEntity ?????????
     */
    @Override
    public GroupEntity getGroup(Long groupSeq) throws ServiceException {
        GroupAuthEnum groupAuth = SessionUtils.getGroupAuth(groupSeq);
        if (groupAuth == GroupAuthEnum.NONE) {
            throw new AccessDeniedException("????????? ????????????.");
        }
        Optional<GroupEntity> group = groupManageRepository.findByIdFetch(groupSeq);
        if (!group.isPresent()) {
            throw new DataNotExistException("?????? ???????????????.");
        }
        return group.get();
    }

    /**
     * ?????? ??????
     */
    @Transactional
    @Override
    public Long makeGroup(GroupSaveRequest groupSaveRequest) throws ServiceException {
        String userId = SessionUtils.getId();
        WebUserEntity webUserEntity = webUserService.findWebUserEntityByUserId(userId);

        List<GroupAuthEntity> ownerList = groupAuthRepository.findAllByWebUserAndGroupAuth(webUserEntity.getUserSeq(),
                GroupAuthEnum.OWNER);
        if (!ownerList.isEmpty()) {
            throw new AlreadyDataExistException("?????? ???????????? ???????????????.");
        }

        // TODO: ???????????? ?????? ?????? ?????? ??????
        GroupAuthEntity groupAuth = new GroupAuthEntity();
        groupAuth.setWebUser(webUserEntity);
        groupAuth.setGroupAuth(GroupAuthEnum.OWNER);
        GroupEntity groupEntity = groupSaveRequest.toEntity();
        groupEntity.addGroupAuth(groupAuth);
        groupEntity.setOwner(userId);
        GroupEntity result = groupRepository.save(groupEntity);
        GroupSeasonEntity groupSeasonEntity = GroupSeasonEntity.defaultSeason(webUserEntity, result);
        groupManageRepository.save(groupSeasonEntity);
        log.info(result.toString());
        return result.getGroupSeq();
    }

    /**
     * ?????? ?????? ?????? ????????????
     */
    @Transactional
    @Override
    public List<GroupResponse> findMyGroupsWithDefaultSeason() {
        Map<Long, GroupAuthDto> groupAuth = SessionUtils.getGroupAuth();
        List<GroupEntity> groupEntities = groupManageRepository.getGroupEntities(groupAuth.keySet());
        ArrayList<GroupResponse> list = new ArrayList<>();
        groupEntities.forEach(groupEntity -> {
            GroupResponse groupResponse = new GroupResponse();
            Optional<GroupSeasonEntity> defaultGroupSeason = groupManageRepository.getGroupSeasonsByGroupSeqs(
                                                                                          Collections.singleton(groupEntity.getGroupSeq())).stream()
                                                                                  .filter(GroupSeasonEntity::getDefaultSeason)
                                                                                  .findFirst();
            defaultGroupSeason.ifPresent(groupResponse::setSeasonEntity);
            groupResponse.setGroupEntity(groupEntity);
            try {
                List<CustomUserResponse> customUserResponses = groupResultService.getCustomUserBySeason(new GroupResultRequest(groupEntity.getGroupSeq(), groupResponse.getDefaultSeason().getSeasonSeq()));
                groupResponse.setCustomUser(customUserResponses);
            } catch (DataNotExistException exception) {
                log.error("", exception);
                groupResponse.setCustomUser(Collections.emptyList());
            }
            groupResponse.setAuth(groupAuth.get(groupEntity.getGroupSeq()).getGroupAuth());

            list.add(groupResponse);
        });
        list.forEach(groupResponse -> groupResponse.getCustomUser().parallelStream().forEach(customUserResponse -> {
            if (!customUserResponse.isRefreshTarget()) {
                return;
            }
            broker.pushEvent(new RefreshSummonerEvent(customUserResponse.getAccountId()));
        }));
        return list;
    }

    /**
     * ????????? ????????? ???????????? ??????
     * @param customUserSaveRequest ???????????? Request
     */
    @Transactional
    @Override
    public void addSummonerInGroup(CustomUserSaveRequest customUserSaveRequest) throws ServiceException {
        SessionUtils.groupManageableAuthCheck(customUserSaveRequest.getGroupSeq());

        GroupEntity group = this.getGroup(customUserSaveRequest.getGroupSeq());
        if (group.getCustomUser().stream().anyMatch(it -> it.getNickname().equals(customUserSaveRequest.getNickname()))){
            throw new AlreadyDataExistException("?????? ????????? ??????????????????.");
        }

        CustomUserEntity customUser = customUserSaveRequest.toEntity();
        customUser.setGroup(group);
        group.addCustomUser(customUser);
        groupRepository.save(group);
    }

    /**
     * ??????????????? ?????? ??????
     * @param customUserDeleteRequest ?????? ?????? Request
     */
    @Transactional
    @Override
    public void deleteSummonerInGroup(CustomUserDeleteRequest customUserDeleteRequest) throws ServiceException {
        SessionUtils.groupManageableAuthCheck(customUserDeleteRequest.getGroupSeq());

        GroupEntity groupEntity = this.getGroup(customUserDeleteRequest.getGroupSeq());
        List<CustomUserEntity> list = groupEntity.getCustomUser().stream()
                                                 .filter(it -> customUserDeleteRequest.getUserSeqArray()
                                                                                      .contains(it.getSeq()))
                                                 .collect(Collectors.toList());
        list.forEach(it -> {
            matchAttendeesRepository.deleteAllByCustomUserEntity(it);
            groupEntity.removeCustomUser(it);
        });
        groupRepository.save(groupEntity);
    }

    @Transactional
    @Override
    public void modifySummonerInGroup(CustomUserModifyRequest customUserModifyRequest) throws ServiceException {
        SessionUtils.groupManageableAuthCheck(customUserModifyRequest.getGroupSeq());

        GroupEntity groupEntity = this.getGroup(customUserModifyRequest.getGroupSeq());
        if (groupEntity.getCustomUser().stream().anyMatch(it -> it.getNickname().equals(customUserModifyRequest.getNickname())
                && it.getSeq() != customUserModifyRequest.getCustomUserSeq()
        )) {
            throw new AlreadyDataExistException("?????? ?????? ??????????????????.");
        }

        groupEntity.getCustomUser().forEach(it -> {
            if (it.getSeq() != customUserModifyRequest.getCustomUserSeq()) {
                return;
            }

            if (!it.getSummonerName().equals(customUserModifyRequest.getSummonerId())) {
                it.setSummonerEntity(null);
            }
            it.setNickname(customUserModifyRequest.getNickname());
            it.setSummonerName(customUserModifyRequest.getSummonerId());
        });

        groupRepository.save(groupEntity);
    }

    /**
     * ?????? ??????
     * @param matchSeq ?????? ??????
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object deleteMatch(Long matchSeq) throws ServiceException {
        Optional<CustomMatchEntity> match = customMatchRepository.findById(matchSeq);
        if (!match.isPresent()) {
            throw new ServiceException("?????? ?????? ?????? ???????????????.");
        }
        CustomMatchEntity matchEntity = match.get();
        SessionUtils.groupManageableAuthCheck(matchEntity.getGroup().getGroupSeq());
        Long seasonSeq = matchEntity.getGroupSeason().getGroupSeasonSeq();
        matchEntity.getMatchAttendees().forEach(it -> {
            Long customUserSeq = Optional.ofNullable(it.getCustomUserEntity()).map(CustomUserEntity::getSeq).orElse(0L);
            cacheManager.invalidSynergyCache(customUserSeq + "_" + seasonSeq);
        });
        customMatchRepository.delete(matchEntity);
        String cacheKey = (Optional.ofNullable(matchEntity.getGroup()).map(GroupEntity::getGroupSeq).orElse(0L)).toString();
        cacheManager.invalidMatchHistoryCache(cacheKey);
        matchEntity.getMatchAttendees().forEach(it -> cacheManager.getPersonalResultHistoryCache().invalidate(
                Optional.ofNullable(it.getCustomUserEntity()).map(CustomUserEntity::getSeq).orElse(0L).toString()));

        return "success";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkSummoner(LinkSummonerRequest request) throws DataNotExistException {
        Optional<CustomUserEntity> customUser = customUserRepository.findById(request.getUserSeq());
        if (!customUser.isPresent() || !customUser.map(CustomUserEntity::getGroup).isPresent()) {
            throw new DataNotExistException();
        }
        CustomUserEntity customUserEntity = customUser.get();
        GroupEntity groupEntity = customUserEntity.getGroup();
        if (!groupEntity.getGroupSeq().equals(request.getGroupSeq())) {
            throw new DataNotExistException();
        }
        SummonerEntity summoner = summonerService.findByAccountId(request.getAccountId());
        customUserEntity.setSummonerEntity(summoner);
        customUserRepository.save(customUserEntity);
    }

    @Override
    @Transactional
    public void saveTierPoint(final List<SaveTierPointRequest> request) throws AccessDeniedException {
        if (CollectionUtils.isEmpty(request)) {
            return;
        }

        SessionUtils.groupManageableAuthCheck(request.get(0).getGroupSeq());

        List<Long> userSeqs = request.stream().map(SaveTierPointRequest::getUserSeq).collect(Collectors.toList());

        List<CustomUserEntity> customUserEntities = customUserRepository.findAllById(userSeqs);
        if (customUserEntities.isEmpty()) {
            return;
        }

        Map<Long, CustomUserEntity> customUserEntityMap = customUserEntities.stream().collect(
                Collectors.toMap(CustomUserEntity::getSeq, it -> it));

        request.forEach(it -> {
            CustomUserEntity customUserEntity = customUserEntityMap.get(it.getUserSeq());
            if (customUserEntity == null || !Objects.equals(customUserEntity.getGroup().getGroupSeq(), it.getGroupSeq())) {
                return;
            }
            customUserEntity.setTierPoint(Optional.ofNullable(it.getTierPoint()).orElse(0L));
        });
    }

    @Override
    @Transactional
    public GroupSeasonEntity getGroupSeasonEntity(Long seasonSeq) throws DataNotExistException {
        if (seasonSeq == null) {
            throw new DataNotExistException("??????????????? ?????????????????????.");
        }

        List<GroupSeasonEntity> groupSeasons = groupManageRepository.getGroupSeasons(Collections.singleton(seasonSeq));

        if (groupSeasons.isEmpty()) {
            throw new DataNotExistException("??????????????? ?????????????????????.");
        }

        return groupSeasons.get(0);
    }

    @Override
    @Transactional
    public List<GroupResponse> findMyGroups() {
        Map<Long, GroupAuthDto> groupAuth = SessionUtils.getGroupAuth();
        List<GroupEntity> groupEntities = groupManageRepository.getGroupEntities(groupAuth.keySet());
        ArrayList<GroupResponse> list = new ArrayList<>();
        groupEntities.forEach(groupEntity -> {
            GroupResponse groupResponse = new GroupResponse();
            List<GroupSeasonEntity> groupSeasonEntities = groupManageRepository.getGroupSeasonsByGroupSeqs(Collections.singleton(groupEntity.getGroupSeq()));
            Optional<GroupSeasonEntity> defaultGroupSeason = groupSeasonEntities.stream()
                                                                                .filter(GroupSeasonEntity::getDefaultSeason)
                                                                                .findFirst();
            defaultGroupSeason.ifPresent(groupResponse::setSeasonEntity);
            groupResponse.setSeasons(groupSeasonEntities.stream().map(GroupSeasonEntity::toResponse)
                                                        .sorted(Comparator.comparing(GroupSeasonResponse::getSeasonSeq).reversed())
                                                        .collect(Collectors.toList()));
            groupResponse.setGroupEntity(groupEntity);
            groupResponse.setAuth(groupAuth.get(groupEntity.getGroupSeq()).getGroupAuth());
            list.add(groupResponse);
        });
        return list;
    }

}