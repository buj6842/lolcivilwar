package kr.co.mcedu.group.service.impl;

import kr.co.mcedu.config.exception.AccessDeniedException;
import kr.co.mcedu.config.exception.DataNotExistException;
import kr.co.mcedu.config.exception.ServiceException;
import kr.co.mcedu.group.entity.CustomUserEntity;
import kr.co.mcedu.group.entity.GroupAuthEnum;
import kr.co.mcedu.group.entity.GroupEntity;
import kr.co.mcedu.group.entity.SynergyModel;
import kr.co.mcedu.group.model.CustomUserDto;
import kr.co.mcedu.group.model.request.GroupResultRequest;
import kr.co.mcedu.group.model.request.MostChampionRequest;
import kr.co.mcedu.group.model.request.PersonalResultRequest;
import kr.co.mcedu.group.model.response.*;
import kr.co.mcedu.group.repository.CustomUserRepository;
import kr.co.mcedu.group.repository.GroupManageRepository;
import kr.co.mcedu.group.repository.MatchDataRepository;
import kr.co.mcedu.group.service.GroupResultService;
import kr.co.mcedu.match.entity.CustomMatchEntity;
import kr.co.mcedu.match.entity.MatchAttendeesEntity;
import kr.co.mcedu.match.model.CustomMatchDto;
import kr.co.mcedu.match.model.MatchAttendeesDto;
import kr.co.mcedu.match.model.response.MatchHistoryResponse;
import kr.co.mcedu.match.repository.MatchRepository;
import kr.co.mcedu.riot.service.RiotDataService;
import kr.co.mcedu.utils.LocalCacheManager;
import kr.co.mcedu.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupResultServiceImpl
        implements GroupResultService {

    private final LocalCacheManager cacheManager;
    private final GroupManageRepository groupManageRepository;
    private final CustomUserRepository customUserRepository;
    private final MatchRepository matchRepository;
    private final MatchDataRepository matchDataRepository;
    private final RiotDataService riotDataService;

    @Override
    @Transactional
    public List<CustomUserResponse> getRankData(GroupResultRequest request) throws ServiceException {
        GroupAuthEnum myGroupAuth = SessionUtils.getGroupAuth(request.getGroupSeq());
        if (!GroupAuthEnum.isViewAbleAuth(myGroupAuth)) {
            throw new AccessDeniedException("????????? ???????????????.");
        }

        return getCustomUserBySeason(request);
    }

    @Override
    @Transactional
    public List<CustomUserResponse> getCustomUserBySeason(final GroupResultRequest request)
            throws DataNotExistException {
        Optional<GroupEntity> groupEntityOpt = groupManageRepository.findByIdFetch(request.getGroupSeq());
        GroupEntity groupEntity = groupEntityOpt.orElseThrow(DataNotExistException::new);
        Map<Long, CustomUserResponse> map = groupEntity.getCustomUser().stream().collect(Collectors.toMap(CustomUserEntity::getSeq, CustomUserEntity::toCustomUserResponse));

        List<CustomMatchDto> customMatchDtos = groupManageRepository.getCustomMatchByGroupSeqAndSeasonSeq(
                request.getGroupSeq(), request.getSeasonSeq());
        customMatchDtos.forEach(customMatchDto -> customMatchDto.getMatchAttendees().forEach(
                matchAttendeesDto -> Optional.ofNullable(matchAttendeesDto.getCustomUser()).map(CustomUserDto::getSeq).map(map::get).ifPresent(target -> {

                    Pair<Integer, Integer> pair = target.getPositionWinRate()
                                                        .getOrDefault(matchAttendeesDto.getPosition(), Pair.of(0, 0));
                    Pair<Integer, Integer> newPair = Pair.of(pair.getFirst() + 1,
                            matchAttendeesDto.isMatchResult() ? pair.getSecond() + 1 : pair.getSecond());
                    target.getPositionWinRate().put(matchAttendeesDto.getPosition(), newPair);

                    target.totalIncrease();
                    if (matchAttendeesDto.isMatchResult()) {
                        target.winIncrease();
                    }
                    LocalDateTime createDateOrNow = Optional.ofNullable(matchAttendeesDto.getCreatedDate())
                                                            .orElseGet(LocalDateTime::now);
                    boolean isBeforeCreateDateOrNow = Optional.ofNullable(target.getLastDate())
                                                              .map(localDateTime -> localDateTime.isBefore(
                                                                      createDateOrNow)).orElse(true);
                    if (isBeforeCreateDateOrNow) {
                        target.setLastDate(createDateOrNow);
                    }
                })));
        return new ArrayList<>(map.values());
    }

    @Override
    @Transactional
    public List<CustomUserResponse> getMatchAttendees(final GroupResultRequest request) throws ServiceException {
        GroupAuthEnum myGroupAuth = SessionUtils.getGroupAuth(request.getGroupSeq());
        if (!GroupAuthEnum.isViewAbleAuth(myGroupAuth)) {
            throw new AccessDeniedException("????????? ???????????????.");
        }
        Optional<GroupEntity> groupEntityOpt = groupManageRepository.findByIdFetch(request.getGroupSeq());
        if (!groupEntityOpt.isPresent()) {
            throw new DataNotExistException();
        }

        return groupManageRepository.getMatchAttendeesByGroupSeqAndSeasonSeq(request.getGroupSeq(), request.getSeasonSeq());
    }

    /**
     * ?????????/?????? ??????
     * @param groupResultRequest ?????? request
     * @return ?????? ??????
     * @throws ServiceException
     */
    @Override
    @Transactional
    public CustomUserSynergyResponse calculateSynergy(GroupResultRequest groupResultRequest) throws ServiceException {
        Long customUserSeq = Optional.ofNullable(groupResultRequest.getCustomUserSeq()).orElse(0L);
        Long seasonSeq = groupResultRequest.getSeasonSeq();
        CustomUserSynergyResponse result = cacheManager.getSynergy(customUserSeq + "_" + seasonSeq);
        Long requestGroupSeq = groupResultRequest.getGroupSeq();
        if (result != null && Objects.equals(result.getGroupSeq(), requestGroupSeq)) {
            log.info("GetFrom SynergyCache : {}", groupResultRequest);
            return result;
        }
        Optional<CustomUserEntity> userEntityOpt = customUserRepository.findById(customUserSeq);
        if (!userEntityOpt.isPresent()) {
            throw new DataNotExistException("????????? ???????????????.");
        }
        CustomUserEntity entity = userEntityOpt.get();
        Optional<GroupEntity> groupEntity = Optional.ofNullable(entity.getGroup());
        if (!groupEntity.isPresent() || !groupEntity.get().getGroupSeq().equals(requestGroupSeq)) {
            throw new ServiceException("????????? ???????????????.");
        }

        List<MatchAttendeesEntity> matchList = matchRepository.findAllByCustomUserEntityWithSeasonSeq(entity, seasonSeq);
        Map<Long, SynergyModel> synergy = new HashMap<>();
        Map<Long, SynergyModel> badSynergy = new HashMap<>();
        // ?????? ????????? ????????? ?????? ??????????????? ??????
        List<Long> matchSeqs = matchList.stream().map(MatchAttendeesEntity::getCustomMatch)
                                        .map(CustomMatchEntity::getMatchSeq).collect(Collectors.toList());
        Map<Long, List<MatchAttendeesEntity>> matchMap = matchRepository.findAllByCustomMatchs(matchSeqs).stream()
                                                                        .collect(Collectors.groupingBy(it -> it.getCustomMatch().getMatchSeq()));
        matchList.forEach(target -> {
            List<MatchAttendeesEntity> allList = matchMap.getOrDefault(target.getCustomMatch().getMatchSeq(), Collections.emptyList());

            allList.stream()
                   .filter(matchAttendeesEntity -> !matchAttendeesEntity.getAttendeesSeq().equals(target.getAttendeesSeq()))
                   .forEach(matchAttendeesEntity -> {
                       Map<Long, SynergyModel> targetSynergy;
                       if (matchAttendeesEntity.getTeam().equals(target.getTeam())) {
                           targetSynergy = synergy;
                       } else {
                           targetSynergy = badSynergy;
                       }
                       Long userSeq = Optional.ofNullable(matchAttendeesEntity.getCustomUserEntity())
                                              .map(CustomUserEntity::getSeq).orElse(0L);
                       SynergyModel synergyModel = targetSynergy.computeIfAbsent(userSeq, a -> new SynergyModel());
                       synergyModel.add(matchAttendeesEntity);
                       targetSynergy.put(userSeq, synergyModel);
                   });
        });
        result = new CustomUserSynergyResponse();
        result.setGroupSeq(requestGroupSeq);
        result.setSeasonSeq(seasonSeq);
        result.getSynergy().addAll(synergy.values());
        result.getBadSynergy().addAll(badSynergy.values());

        cacheManager.putSynergyCache(customUserSeq + "_" + seasonSeq, result);
        return result;
    }

    @Transactional
    @Override
    public MatchHistoryResponse getMatches(Long groupSeq, Integer pageNum) throws AccessDeniedException {
        SessionUtils.groupAuthorityCheck(groupSeq, GroupAuthEnum::isViewAbleAuth);
        Map<Integer, MatchHistoryResponse> map = cacheManager.getMatchHistory(groupSeq.toString());
        Optional<MatchHistoryResponse> result = Optional.ofNullable(map.get(pageNum));
        if (result.isPresent()) {
            log.info("GetFrom MatchHistoryCache : {} , {}", groupSeq, pageNum);
            return result.get();
        }

        Page<CustomMatchDto> page = matchRepository.findByGroup_GroupSeqOrderByMatchSeqDesc(groupSeq, PageRequest.of(pageNum, 10));

        MatchHistoryResponse matchHistoryResponse = this.setMatchHistoryResponse(page);

        map.put(pageNum, matchHistoryResponse);
        cacheManager.putMatchHistoryCache(groupSeq.toString(), map);

        return matchHistoryResponse;
    }

    private MatchHistoryResponse setMatchHistoryResponse(Page<CustomMatchDto> page) {
        MatchHistoryResponse matchHistoryResponse = new MatchHistoryResponse();
        matchHistoryResponse.setTotalPage(page.getTotalPages());
        final AtomicLong matchNumber = new AtomicLong(page.getTotalElements() - ((long) page.getNumber() * page.getSize()));
        List<Long> matchSeqs = page.get().map(CustomMatchDto::getMatchSeq).collect(Collectors.toList());
        Map<Long, List<MatchAttendeesDto>> matchAttendeesMap =
                groupManageRepository.getMatchAttendees(matchSeqs)
                                     .stream()
                                     .collect(Collectors.groupingBy(it -> it.getCustomMatch().getMatchSeq()));

        page.get().forEach(it -> {
            List<String> aList = new ArrayList<>();
            List<String> bList = new ArrayList<>();

            List<MatchAttendeesDto> matchAttendees = matchAttendeesMap.getOrDefault(it.getMatchSeq(), Collections.emptyList());
            matchAttendees.forEach(attendeesDto -> {
                List<String> currentTeamList;
                if ("A".equals(attendeesDto.getTeam())) {
                    currentTeamList = aList;
                } else {
                    currentTeamList = bList;
                }
                String nickname = Optional.ofNullable(attendeesDto.getCustomUser())
                                          .map(CustomUserDto::getNickname).orElse("");
                currentTeamList.add(nickname);
            });

            MatchHistoryResponse.MatchHistoryElement matchHistoryElement = new MatchHistoryResponse.MatchHistoryElement();
            matchHistoryElement.setMatchNumber(matchNumber.getAndDecrement());
            matchHistoryElement.setDate(Optional.ofNullable(it.getCreatedDate())
                                                .map(localDateTime -> localDateTime.format(
                                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                                .orElse(""));
            matchHistoryElement.setSeasonName(it.getGroupSeason().getSeasonName());
            matchHistoryElement.setAList(aList);
            matchHistoryElement.setBList(bList);
            MatchAttendeesDto matchAttendee = matchAttendees.get(0);
            String winner = matchAttendee.getTeam();
            if(!matchAttendee.isMatchResult()) {
                winner = MatchHistoryResponse.teamFlip(winner);
            }
            matchHistoryElement.setWinner(winner);
            matchHistoryElement.setMatchSeq(Optional.ofNullable(it.getMatchSeq()).orElse(0L));
            matchHistoryResponse.getList().add(matchHistoryElement);
        });

        return matchHistoryResponse;
    }

    @Override
    @Transactional
    public PersonalResultResponse getPersonalResult(PersonalResultRequest request) throws Exception {
        SessionUtils.groupAuthorityCheck(request.getGroupSeq(), GroupAuthEnum::isViewAbleAuth);

        if (Objects.isNull(request.getPage())) {
            throw new ServiceException("???????????? ?????? ??????????????????.");
        }

        Map<Integer, PersonalResultResponse> map = cacheManager.getPersonalResultHistory(request.getCustomUserSeq().toString());

        Optional<PersonalResultResponse> result = Optional.ofNullable(map.get(request.getPage()));
        if (result.isPresent()) {
            log.info("GetFrom tPersonalResultHistoryCache : {} , {}", request.getCustomUserSeq(), request.getPage());
            return result.get();
        }
        Optional<CustomUserEntity> customUser = groupManageRepository.customUserFetch(request.getCustomUserSeq());
        if (!customUser.isPresent()) {
            throw new DataNotExistException();
        }
        CustomUserEntity customUserEntity = customUser.get();

        Page<MatchAttendeesEntity> attendeesPage = groupManageRepository.findAllPersonalMatchResult(customUserEntity, PageRequest.of(request.getPage(), 10));
        PersonalResultResponse personalResultResponse = new PersonalResultResponse().setPage(attendeesPage);
        for (PersonalResultResponse.PersonalResultElement personalResultElement : personalResultResponse.getList()) {
            personalResultElement.setPickChampionUrl(riotDataService.getChampionImageUrlById(personalResultElement.getPickChampion()));
            personalResultElement.setMatchChampionUrl(riotDataService.getChampionImageUrlById(personalResultElement.getMatchChampion()));
        }

        map.put(request.getPage(), personalResultResponse);
        cacheManager.putPersonalResultHistory(request.getCustomUserSeq().toString(), map);
        return personalResultResponse;
    }

    @Override
    @Transactional
    public CustomUserMostResponse getMostChampion(MostChampionRequest request) {
        CustomUserMostResponse mostChampionCache = cacheManager.getMostChampionCache(request.getCacheKey());
        if(mostChampionCache != null) {
            return mostChampionCache;
        }
        List<MostChampionResponse> getList = matchDataRepository.findMostChampion(request);

        //????????? ????????? , ????????? ?????? map
        Map<Long, PlayChampionCounter> playedCountMap = new HashMap<>();
        for (MostChampionResponse mostChampionResponse : getList) {
            Long championId = mostChampionResponse.getChampionId();
            PlayChampionCounter counter = playedCountMap.getOrDefault(championId, new PlayChampionCounter(championId));
            if(mostChampionResponse.getMatchResult()) {
                counter.win();
            } else {
                counter.lose();
            }
            playedCountMap.put(championId, counter);
        }
        //????????? ????????? ???
        List<PlayChampionCounter> mostList = playedCountMap.values().stream()
                .sorted(Comparator.comparing(PlayChampionCounter::getTotal).reversed())
                .limit(3)
                .peek(playChampionCounter -> {
                    playChampionCounter.setChampionName(riotDataService.getChampionName(playChampionCounter.getChampionId()));
                    playChampionCounter.setChampionImageUrl(riotDataService.getChampionImageUrl(playChampionCounter.getChampionName()));
                })
                .collect(Collectors.toList());
        //??????
        List<PlayChampionCounter> rateList = playedCountMap.values().stream()
                .sorted(Comparator.comparing(PlayChampionCounter::getRate).reversed())
                .limit(3)
                .peek(playChampionCounter -> {
                    playChampionCounter.setChampionName(riotDataService.getChampionName(playChampionCounter.getChampionId()));
                    playChampionCounter.setChampionImageUrl(riotDataService.getChampionImageUrl(playChampionCounter.getChampionName()));
                })
                .collect(Collectors.toList());
        //?????? 5??????
        List<MostChampionResponse> collect = getList.stream()
                .limit(5)
                .peek(mostChampionResponse -> {
                    mostChampionResponse.setChampionName(riotDataService.getChampionName(mostChampionResponse.getChampionId()));
                    mostChampionResponse.setChampionImageUrl(riotDataService.getChampionImageUrl(mostChampionResponse.getChampionName()));
                })
                .collect(Collectors.toList());

        CustomUserMostResponse customUserMostResponse = new CustomUserMostResponse(mostList, rateList, collect);
        cacheManager.putMostChampionCache(request.getCacheKey(), customUserMostResponse);
        return customUserMostResponse;
    }
}
