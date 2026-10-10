package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장애학생·도우미에게만 쌓인다 (관리자는 알림 없음). 사용자가 지우지 않는다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient_id_read_at", columnList = "recipient_id, read_at"),
                @Index(name = "idx_notifications_recipient_id_id", columnList = "recipient_id, id")
        }
)
public class Notification extends BaseTimeEntity {

    public static final int MESSAGE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Account recipient;

    @Column(nullable = false, length = 40)
    private NotificationType type;

    // 서버가 만든 문장. 장애 유형·특이사항·메모는 넣지 않는다
    @Column(nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    @Column(nullable = false, length = 20)
    private NotificationTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    private LocalDateTime readAt;

    public Notification(Account recipient, NotificationType type, String message,
                        NotificationTargetType targetType, Long targetId) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    // 이미 읽은 알림은 처음 읽은 시각을 유지한다
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
