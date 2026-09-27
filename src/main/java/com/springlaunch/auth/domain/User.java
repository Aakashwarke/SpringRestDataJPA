package com.springlaunch.auth.domain;

import com.springlaunch.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /** Stable external identifier. Never expose the numeric primary key over the API. */
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static User create(String email, String passwordHash, String fullName) {
        User user = new User();
        user.publicId = UUID.randomUUID().toString();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        return user;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
