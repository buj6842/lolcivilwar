package kr.co.mcedu.user.model;

import kr.co.mcedu.user.entity.UserAlarmEntity;
import kr.co.mcedu.utils.TimeUtils;
import lombok.Getter;

import java.util.Optional;

/**
 * 웹 노출용 알림 객체
 */
@Getter
public class UserAlarmMessage {
    private final Long alarmSeq;
    private final String message;
    private final String viewTime;
    public UserAlarmMessage(UserAlarmEntity userAlarmEntity) {
        this.alarmSeq = Optional.ofNullable(userAlarmEntity.getAlarmSeq()).orElse(0L);
        this.message = userAlarmEntity.getMessage();
        this.viewTime = TimeUtils.convertViewTime(userAlarmEntity.getCreatedDate());
    }
}