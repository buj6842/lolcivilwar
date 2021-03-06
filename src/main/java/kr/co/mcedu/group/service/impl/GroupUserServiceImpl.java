package kr.co.mcedu.group.service.impl;

import com.querydsl.core.QueryResults;
import kr.co.mcedu.common.model.PageRequest;
import kr.co.mcedu.common.model.PageWrapper;
import kr.co.mcedu.config.exception.AccessDeniedException;
import kr.co.mcedu.config.exception.AlreadyDataExistException;
import kr.co.mcedu.config.exception.DataNotExistException;
import kr.co.mcedu.config.exception.ServiceException;
import kr.co.mcedu.group.entity.GroupAuthEntity;
import kr.co.mcedu.group.entity.GroupAuthEnum;
import kr.co.mcedu.group.entity.GroupEntity;
import kr.co.mcedu.group.model.request.*;
import kr.co.mcedu.group.model.response.GroupAuthResponse;
import kr.co.mcedu.group.model.response.GroupInviteHistoryResponse;
import kr.co.mcedu.group.repository.GroupManageRepository;
import kr.co.mcedu.group.service.GroupUserService;
import kr.co.mcedu.user.entity.GroupInviteEntity;
import kr.co.mcedu.user.entity.UserAlarmEntity;
import kr.co.mcedu.user.entity.WebUserEntity;
import kr.co.mcedu.user.model.UserAlarmType;
import kr.co.mcedu.user.repository.UserAlarmRepository;
import kr.co.mcedu.user.repository.WebUserRepository;
import kr.co.mcedu.user.service.UserAlarmService;
import kr.co.mcedu.user.service.WebUserService;
import kr.co.mcedu.utils.LocalCacheManager;
import kr.co.mcedu.utils.SessionUtils;
import kr.co.mcedu.utils.StringUtils;
import kr.co.mcedu.utils.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static kr.co.mcedu.group.entity.GroupAuthEnum.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GroupUserServiceImpl
        implements GroupUserService {

    private final GroupManageRepository groupManageRepository;
    private final WebUserRepository webUserRepository;
    private final UserAlarmRepository userAlarmRepository;

    private final WebUserService webUserService;
    private final UserAlarmService userAlarmService;

    private final LocalCacheManager cacheManager;

    /**
     * ???????????? ????????????
     * @param request ????????????
     */
    @Override
    @Transactional
    public void expelUser(final GroupExpelRequest request) throws ServiceException {
        GroupAuthEnum groupAuth = SessionUtils.getGroupAuth(request.getGroupSeq());

        if (groupAuth.getOrder() < MANAGER.getOrder()) {
            throw new AccessDeniedException("????????? ???????????????.");
        }

        Optional<GroupAuthEntity> groupOpt = groupManageRepository.getGroupAuthByGroupSeqAndUserSeq(request.getGroupSeq(), request.getUserSeq());
        GroupAuthEntity groupAuthEntity = groupOpt.orElseThrow(DataNotExistException::new);

        if (groupAuth.getOrder() <= groupAuthEntity.getGroupAuth().getOrder()) {
            throw new AccessDeniedException("????????? ???????????????.");
        }

        groupManageRepository.delete(groupAuthEntity);
        webUserService.pushRefreshedUser(request.getUserSeq());
    }

    /**
     * ????????? ????????????
     * @param request ????????????
     */
    @Override
    @Transactional
    public void inviteUser(final GroupInviteRequest request) throws ServiceException {
        if (StringUtils.isEmpty(request.getLolcwTag())) {
            throw new ServiceException("????????? ???????????????");
        }
        
        GroupAuthEnum groupAuth = SessionUtils.getGroupAuth(request.getGroupSeq());

        if (groupAuth.getOrder() < MANAGER.getOrder()) {
            throw new AccessDeniedException("????????? ???????????????.");
        }

        Optional<WebUserEntity> entityOptional = webUserRepository.findWebUserEntityByLolcwTag(request.getLolcwTag());
        WebUserEntity inviteUser = entityOptional.orElseThrow(() -> new DataNotExistException("???????????? ?????? ??????????????????."));
        if (!inviteUser.getUserSeq().equals(request.getUserSeq())) {
            throw new ServiceException("????????? ???????????????.");
        }

        if(groupManageRepository.getGroupAuthByGroupSeqAndUserSeq(request.getGroupSeq(), request.getUserSeq()).isPresent()) {
            throw new AlreadyDataExistException("?????? ?????????????????? ??????????????????.");
        }

        Optional<GroupInviteEntity> alreadyInviteCheck = groupManageRepository.getAlreadyInviteCheck(request.getGroupSeq(), request.getUserSeq());
        if (alreadyInviteCheck.isPresent()) {
            if (Boolean.FALSE.equals(alreadyInviteCheck.get().getExpireResult())) {
                throw new ServiceException("???????????? ?????? ????????? ?????? ???????????????.");
            }
            LocalDateTime available = alreadyInviteCheck.get().getModifiedDate().plusDays(1);
            Pair<String, Long> pair = TimeUtils.diffFromCurrent(available);
            String unit = pair.getFirst();
            Long between = pair.getSecond();

            throw new ServiceException("?????? ???????????? ???????????? : " + between + unit);
        }

        WebUserEntity currentUserEntity = webUserService.findWebUserEntity(SessionUtils.getUserSeq());

        GroupEntity groupEntity = groupManageRepository.findByIdFetch(request.getGroupSeq())
                                                       .orElseThrow(DataNotExistException::new);

        GroupInviteEntity groupInviteEntity = new GroupInviteEntity();
        groupInviteEntity.setGroup(groupEntity);
        groupInviteEntity.setInvitedUser(inviteUser);
        groupInviteEntity.setUser(currentUserEntity);
        groupInviteEntity.setExpireResult(false);

        groupInviteEntity = groupManageRepository.save(groupInviteEntity);

        userAlarmService.sendInviteAlarm(inviteUser, groupInviteEntity);

        cacheManager.invalidGroupInviteHistoryCache(groupEntity.getGroupSeq().toString());
    }

    /**
     * ????????? ????????????
     * @param request
     * @throws ServiceException
     * @return
     */
    @Override
    @Transactional
    public String replyInviteMessage(ReplyInviteRequest request) throws ServiceException{
        UserAlarmEntity userAlarmEntity = userAlarmRepository.findById(Optional.ofNullable(request.getAlarmSeq()).orElse(0L))
                                                             .orElseThrow(DataNotExistException::new);
        if (!userAlarmEntity.getWebUserEntity().getUserSeq().equals(SessionUtils.getUserSeq())) {
            throw new AccessDeniedException("????????? ????????????.");
        }
        GroupInviteEntity groupInviteEntity = userAlarmEntity.getGroupInviteEntity();
        if (!request.isValidRequest() || userAlarmEntity.getAlarmType() != UserAlarmType.INVITE || groupInviteEntity == null
                || !request.getInviteSeq().equals(groupInviteEntity.getGroupInviteSeq())) {
            throw new AccessDeniedException();
        }

        userAlarmEntity.setIsRead(true);
        if (Boolean.TRUE.equals(groupInviteEntity.getExpireResult())) {
            return "EXPIRED";
        }

        boolean inviteReplyResult = "Y".equals(request.getResult());
        groupInviteEntity.setInviteResult(inviteReplyResult);
        groupInviteEntity.setExpireResult(true);

        cacheManager.invalidGroupInviteHistoryCache(groupInviteEntity.getGroup().getGroupSeq().toString());
        cacheManager.invalidAlarmCountCache(SessionUtils.getId());
        if (inviteReplyResult) {
            modifyUserGroupAuth(groupInviteEntity.getGroup(), userAlarmEntity.getWebUserEntity(), USER);
            SessionUtils.refreshAccessToken();
        }

        return "SUCCESS";
    }

    private void modifyUserGroupAuth(GroupEntity group, WebUserEntity webUserEntity, GroupAuthEnum auth) {
        Optional<GroupAuthEntity> groupAuthEntityOpt = groupManageRepository.getGroupAuthByGroupSeqAndUserSeq(group.getGroupSeq(), webUserEntity.getUserSeq());
        GroupAuthEntity groupAuthEntity = groupAuthEntityOpt.orElseGet(GroupAuthEntity::new);
        groupAuthEntity.setGroup(group);
        groupAuthEntity.setWebUser(webUserEntity);
        groupAuthEntity.setGroupAuth(auth);
        groupManageRepository.save(groupAuthEntity);
    }

    /**
     * ???????????? ????????? ???????????? 
     * @param groupSeq ????????????
     * @return ???????????? ?????????
     */
    @Override
    @Transactional
    public List<GroupAuthResponse> getAuthUserList(Long groupSeq) throws ServiceException {
        SessionUtils.groupManageableAuthCheck(groupSeq);
        List<GroupAuthEntity> groupAuthEntities = groupManageRepository.getGroupAuthByGroupSeq(groupSeq);
        return groupAuthEntities.stream().map(GroupAuthResponse::new)
                                .sorted(Comparator.comparing(o -> ((GroupAuthResponse) o).getGroupAuth().getOrder()).reversed())
                                .collect(Collectors.toList());
    }

    /**
     * ???????????? ????????? ????????????
     * @param groupSeq ????????????
     * @param page ????????? 0 ->
     * @return ????????????
     */
    @Override
    @Transactional
    public PageWrapper<GroupInviteHistoryResponse> getInviteUserHistory(final Long groupSeq, Integer page) throws AccessDeniedException {
        SessionUtils.groupManageableAuthCheck(groupSeq);
        if (page == null) {
            page = 0;
        }
        PageWrapper<GroupInviteHistoryResponse> cachedPageWrapper = cacheManager.getGroupInviteHistoryCache(groupSeq.toString()).get(page);
        if (cachedPageWrapper != null) {
            log.info("GetFrom GroupInviteHistoryCache : {} , {}", groupSeq, page);
            return cachedPageWrapper;
        }

        PageRequest pageRequest = new PageRequest(page, 10);
        QueryResults<GroupInviteEntity> groupInviteHistory = groupManageRepository.getGroupInviteHistory(groupSeq, pageRequest);
        PageWrapper<GroupInviteEntity> result = PageWrapper.of(groupInviteHistory);
        PageWrapper<GroupInviteHistoryResponse> responsePageWrapper = result.change(GroupInviteHistoryResponse::new);
        cacheManager.putGroupInviteHistoryCache(groupSeq.toString(), page, responsePageWrapper);
        return responsePageWrapper;
    }

    /**
     * ???????????? ???????????? list
     * @param request
     * @throws ServiceException
     */
    @Override
    @Transactional
    public void modifyUserAuth(final GroupAuthChangeRequest request) throws ServiceException {
        GroupAuthEnum myAuth = SessionUtils.groupManageableAuthCheck(request.getGroupSeq());
        if (CollectionUtils.isEmpty(request.getTargets())) {
            throw new DataNotExistException("????????? ????????? ???????????? ????????????.");
        }
        Map<Long, GroupAuthEntity> groupAuthEntityMap =
                groupManageRepository.getGroupAuthByGroupSeq(request.getGroupSeq())
                                     .stream()
                                     .collect(Collectors.toMap(it -> it.getWebUser().getUserSeq(), it -> it));
        for (AuthChangeRequest target : request.getTargets()) {
            Long targetUserSeq = target.getUserSeq();
            GroupAuthEntity groupAuthEntity = groupAuthEntityMap.get(targetUserSeq);
            if (groupAuthEntity == null) {
                throw new DataNotExistException();
            }

            GroupAuthEnum changedAuth = target.getChangedAuth();
            long mySeq = SessionUtils.getUserSeq();
            if ((myAuth != OWNER && changedAuth.getOrder() > myAuth.getOrder()) || target.getUserSeq() == mySeq) {
                throw new ServiceException("????????? ???????????????.");
            }

            if (groupAuthEntity.getGroupAuth().getOrder() >= myAuth.getOrder()) {
                throw new AccessDeniedException("????????? ???????????????.");
            }

            groupAuthEntity.setGroupAuth(changedAuth);
            if (changedAuth == OWNER) {
                log.info("{} group change owner  {} -> {}", request.getGroupSeq(), mySeq, targetUserSeq);
                groupAuthEntityMap.get(mySeq).setGroupAuth(MANAGER);
                SessionUtils.refreshAccessToken();
            }

            webUserService.pushRefreshedUser(targetUserSeq);
        }
    }

    @Override
    @Transactional
    public void cancelInvite(Long groupInviteSeq) throws ServiceException {
        GroupInviteEntity groupInviteEntity = groupManageRepository.getGroupInvite(groupInviteSeq).orElseThrow(DataNotExistException::new);
        SessionUtils.groupManageableAuthCheck(groupInviteEntity.getGroup().getGroupSeq());
        groupInviteEntity.setExpireResult(true);

        cacheManager.invalidGroupInviteHistoryCache(groupInviteEntity.getGroup().getGroupSeq().toString());
    }
}
