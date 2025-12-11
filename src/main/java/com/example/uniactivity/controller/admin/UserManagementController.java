package com.example.uniactivity.controller.admin;

import com.example.uniactivity.dto.admin.UserDto;
import com.example.uniactivity.dto.admin.UserResponseDto;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.service.AcademicYearService;
import com.example.uniactivity.service.FacultyService;
import com.example.uniactivity.service.StudentClassService;
import com.example.uniactivity.service.UserManagementService;
import com.example.uniactivity.exception.ValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final StudentClassService studentClassService;
    private final FacultyService facultyService;
    private final AcademicYearService academicYearService;

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userManagementService.getAllUsers());
        model.addAttribute("classes", studentClassService.getAllClasses());
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("academicYears", academicYearService.getActiveAcademicYears());
        model.addAttribute("roles", Role.values());
        return "admin/user-list";
    }

    // ========== REST API ==========

    @GetMapping("/api")
    @ResponseBody
    public List<UserResponseDto> getAllUsers() {
        return userManagementService.getAllUsers();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public UserResponseDto getUserById(@PathVariable Long id) {
        return userManagementService.getUserById(id);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userManagementService.createUser(dto));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<UserResponseDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userManagementService.updateUser(id, dto));
    }
    
    @PostMapping("/api/{id}/toggle-status")
    @ResponseBody
    public ResponseEntity<Void> toggleUserStatus(@PathVariable Long id) {
        userManagementService.toggleUserStatus(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/api/{id}/reset-password")
    @ResponseBody
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            throw new ValidationException("Mật khẩu phải có ít nhất 6 ký tự");
        }
        userManagementService.resetPassword(id, newPassword);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userManagementService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}
