package kr.co.mcedu.group.service;

import kr.co.mcedu.config.exception.AccessDeniedException;
import kr.co.mcedu.config.exception.ServiceException;
import kr.co.mcedu.group.entity.GroupEntity;
import kr.co.mcedu.group.model.GroupResponse;
import kr.co.mcedu.group.model.GroupSaveRequest;
import kr.co.mcedu.group.model.request.*;
import kr.co.mcedu.group.model.response.CustomUserSynergyResponse;
import kr.co.mcedu.group.model.response.GroupAuthResponse;
import kr.co.mcedu.group.model.response.PersonalResultResponse;
import kr.co.mcedu.match.model.response.MatchHistoryResponse;

import java.util.List;

public interface GroupService {
    GroupEntity getGroup(Long groupSeq) throws ServiceException;

    Long makeGroup(GroupSaveRequest groupSaveRequest) throws ServiceException;

    List<GroupResponse> findMyGroups() throws AccessDeniedException;

    void addSummonerInGroup(CustomUserSaveRequest customUserSaveRequest) throws ServiceException;

    void deleteSummonerInGroup(CustomUserDeleteRequest customUserDeleteRequest) throws ServiceException;

    void modifySummonerInGroup(CustomUserModifyRequest customUserModifyRequest) throws ServiceException;

    CustomUserSynergyResponse calculateSynergy(CustomUserSynergyRequest customUserSynergyRequest)
            throws ServiceException;

    MatchHistoryResponse getMatches(Long groupSeq, Integer pageNum);

    Object deleteMatch(Long matchSeq);

    List<GroupAuthResponse> getAuthUserList(Long groupSeq);

    void linkSummoner(LinkSummonerRequest request);

    PersonalResultResponse getPersonalResult(PersonalResultRequest request);
}