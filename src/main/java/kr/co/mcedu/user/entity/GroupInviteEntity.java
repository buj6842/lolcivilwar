package kr.co.mcedu.user.entity;

import kr.co.mcedu.group.entity.GroupEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@DynamicInsert
@DynamicUpdate
@Entity(name = "group_invite")
@Table(schema = "lol", name = "group_invite")
@EntityListeners(AuditingEntityListener.class)
@SequenceGenerator(sequenceName = "group_invite_seq", initialValue = 1, allocationSize = 1, name = "group_invite_seq_gen", schema = "lol")
public class GroupInviteEntity {
    @Id
    @Column(name = "group_invite_seq")
    @GeneratedValue(generator = "group_invite_seq_gen", strategy = GenerationType.SEQUENCE)
    private Long groupInviteSeq;
    @ManyToOne
    @JoinColumn(name = "group_seq", referencedColumnName = "group_seq")
    private GroupEntity group;
    @ManyToOne
    @JoinColumn(name = "user_seq", referencedColumnName = "user_seq")
    private WebUserEntity user;
    @CreatedDate
    private LocalDateTime invitedDate;
    @LastModifiedDate
    private LocalDateTime modifiedDate;
    @ManyToOne
    @JoinColumn(name = "invited_user_seq", referencedColumnName = "user_seq")
    private WebUserEntity invitedUser;
    private Boolean inviteResult;
    private Boolean expireResult;
}
