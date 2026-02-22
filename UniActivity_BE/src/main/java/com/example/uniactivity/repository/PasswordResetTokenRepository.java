package com.example.uniactivity.repository;

import com.example.uniactivity.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // Tìm OTP mới nhất chưa hết hạn và chưa dùng cho email
    Optional<PasswordResetToken> findFirstByEmailAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    // Xóa các token đã hết hạn (dọn dẹp)
    void deleteByExpiryTimeBefore(LocalDateTime now);
}
