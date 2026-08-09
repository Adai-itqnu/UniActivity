package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Lấy thông tin user từ Google
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(oAuth2User);
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            // Bắt tất cả lỗi khác (DB constraint, etc.) và chuyển thành OAuth2AuthenticationException
            log.error("Lỗi xử lý đăng nhập Google: {}", ex.getMessage(), ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("processing_error", "Lỗi xử lý tài khoản Google: " + ex.getMessage(), null),
                    ex
            );
        }
    }

    /**
     * Xử lý thông tin OAuth2 user: tìm hoặc tạo user trong database.
     */
    OAuth2User processOAuth2User(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");
        String name = oAuth2User.getAttribute("name");
        String googleId = oAuth2User.getAttribute("sub");
        String avatarUrl = oAuth2User.getAttribute("picture");

        if (email == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found", "Không tìm thấy email từ tài khoản Google", null)
            );
        }

        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_verified", "Google email must be verified", null)
            );
        }

        log.info("Đăng nhập Google cho email: {}", email);

        Optional<User> userOptional = userRepository.findByEmail(email);

        User user;
        if (userOptional.isPresent()) {
            // User đã tồn tại — cập nhật Google ID và avatar nếu cần
            user = userOptional.get();
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
            }
            if (user.getAvatarUrl() == null && avatarUrl != null) {
                user.setAvatarUrl(avatarUrl);
            }
            // Google đã xác thực email → auto verified
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Cập nhật user hiện tại: {} (role: {})", user.getUsername(), user.getRole());
        } else {
            // Tạo user mới từ Google
            user = new User();
            user.setEmail(email);
            user.setUsername("google_" + googleId);
            user.setFullName(name != null ? name : email);
            user.setRole(Role.STUDENT);
            user.setStatus(UserStatus.ACTIVE);
            user.setGoogleId(googleId);
            user.setProvider("GOOGLE");
            user.setAvatarUrl(avatarUrl);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            // Google đã xác thực email → auto verified
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Tạo user mới từ Google: {} (email: {})", user.getUsername(), email);
        }

        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }
}
