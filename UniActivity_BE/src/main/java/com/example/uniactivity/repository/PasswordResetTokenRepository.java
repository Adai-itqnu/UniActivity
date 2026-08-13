package com.example.uniactivity.repository;

import com.example.uniactivity.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // Tìm OTP mới nhất chưa hết hạn và chưa dùng cho email (backward compatible)
    Optional<PasswordResetToken> findFirstByEmailAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    // Tìm OTP theo type (EMAIL_VERIFY hoặc PASSWORD_RESET)
    Optional<PasswordResetToken> findFirstByEmailAndTypeAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
            String email, String type, LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
            String email, String type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken token set token.used = true "
            + "where token.email = :email and token.type = :type and token.used = false")
    int invalidateUnusedTokens(@Param("email") String email, @Param("type") String type);

    // Đếm số OTP đã gửi gần đây (chống spam)
    long countByEmailAndTypeAndCreatedAtAfter(String email, String type, LocalDateTime after);

    // Xóa các token đã hết hạn (dọn dẹp)
    void deleteByExpiryTimeBefore(LocalDateTime now);
}
