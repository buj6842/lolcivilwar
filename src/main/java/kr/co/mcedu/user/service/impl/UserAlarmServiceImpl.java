package kr.co.mcedu.user.service.impl;

import kr.co.mcedu.config.exception.AccessDeniedException;
import kr.co.mcedu.config.exception.DataNotExistException;
import kr.co.mcedu.config.exception.ServiceException;
import kr.co.mcedu.user.entity.UserAlarmEntity;
import kr.co.mcedu.user.entity.WebUserEntity;
import kr.co.mcedu.user.model.UserAlarmMessage;
import kr.co.mcedu.user.model.UserAlarmType;
import kr.co.mcedu.user.repository.UserAlarmRepository;
import kr.co.mcedu.user.service.UserAlarmService;
import kr.co.mcedu.user.service.WebUserService;
import kr.co.mcedu.utils.LocalCacheManager;
import kr.co.mcedu.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * user alarm service
 */
@Service
@RequiredArgsConstructor
public class UserAlarmServiceImpl implements UserAlarmService {
    private final LocalCacheManager localCacheManager;
    private final WebUserService webUserService;
    private final UserAlarmRepository userAlarmRepository;

    /**
     * 일반 메시지 알람 전송
     */
    @Override
    @Transactional(rollbackOn = Exception.class)
    public void sendAlarm(Long userSeq, String message) throws AccessDeniedException {
        UserAlarmEntity userAlarmEntity = new UserAlarmEntity();
        WebUserEntity webUserEntity = webUserService.findWebUserEntity(userSeq);
        userAlarmEntity.setWebUserEntity(webUserEntity);
        userAlarmEntity.setMessage(message);
        userAlarmEntity.setAlarmType(UserAlarmType.MESSAGE);
        userAlarmRepository.save(userAlarmEntity);
        localCacheManager.getAlarmCountCache().invalidate(webUserEntity.getUserId());
    }

    /**
     * 일반 메시지 알람 전송
     */
    @Override
    @Transactional(rollbackOn = Exception.class)
    public void sendAlarm(WebUserEntity webUserEntity, String message) {
        UserAlarmEntity userAlarmEntity = new UserAlarmEntity();
        userAlarmEntity.setWebUserEntity(webUserEntity);
        userAlarmEntity.setMessage(message);
        userAlarmEntity.setAlarmType(UserAlarmType.MESSAGE);
        userAlarmRepository.save(userAlarmEntity);
        localCacheManager.getAlarmCountCache().invalidate(webUserEntity.getUserId());
    }

    /**
     * 안읽은 Alarm 갯수
     */
    @Override
    public Long getUnreadAlarmCount() throws AccessDeniedException {
        String userId = SessionUtils.getId();
        Long count = localCacheManager.getAlarmCountCache().getIfPresent(userId);
        if (count == null) {
            WebUserEntity webUserEntity = webUserService.findWebUserEntityByUserId(userId);
            count = userAlarmRepository.countUnreadAlarm(webUserEntity);
            localCacheManager.getAlarmCountCache().put(userId, count);
        }
        return count;
    }

    /**
     * 안읽은 Alarm List
     */
    @Override
    public List<UserAlarmMessage> getUnreadMessage() throws AccessDeniedException {
        long userSeq = SessionUtils.getUserSeq();
        if (userSeq == 0L) {
            throw new AccessDeniedException();
        }
        return userAlarmRepository.getUnreadMessage(userSeq)
                                  .stream()
                                  .map(UserAlarmMessage::new)
                                  .collect(Collectors.toList());
    }

    @Override
    public Object readMessage(final Long alarmSeq) throws ServiceException {
        long userSeq = SessionUtils.getUserSeq();
        if (alarmSeq == null || userSeq == 0L) {
            throw new AccessDeniedException();
        }
        UserAlarmEntity entity = userAlarmRepository.findById(alarmSeq).orElseThrow(DataNotExistException::new);
        if (Optional.ofNullable(entity.getWebUserEntity()).map(WebUserEntity::getUserSeq).orElse(0L) != userSeq) {
            throw new AccessDeniedException();
        }
        entity.read();
        userAlarmRepository.save(entity);
        localCacheManager.getAlarmCountCache().invalidate(SessionUtils.getId());
        return "success";
    }
}