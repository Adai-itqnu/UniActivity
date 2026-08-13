package com.example.uniactivity.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class to create admin account
 * Run this class to generate BCrypt hash for admin password
 */
public class AdminAccountCreator {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // Mã hóa password "admin123" (bạn có thể thay đổi)
        String password = "admin123";
        String hashedPassword = encoder.encode(password);
        
        System.out.println("=".repeat(80));
        System.out.println("ADMIN ACCOUNT CREATION SCRIPT");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hashedPassword);
        System.out.println();
        System.out.println("SQL Statement để tạo admin account:");
        System.out.println("-".repeat(80));
        
        String sql = String.format(
            "INSERT INTO users (username, password_hash, full_name, email, role, status, provider, created_at) " +
            "VALUES ('admin', '%s', 'Administrator', 'admin@uniactivity.edu.vn', 'ADMIN', 'ACTIVE', 'LOCAL', NOW());",
            hashedPassword
        );
        
        System.out.println(sql);
        System.out.println();
        System.out.println("Hoặc chạy lệnh MySQL:");
        System.out.println("-".repeat(80));
        System.out.println("mysql -u root -p uni_activitydb");
        System.out.println("Sau đó paste câu SQL trên vào");
        System.out.println("=".repeat(80));
    }
}
