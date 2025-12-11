package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controller for generating and exporting reports
 */
@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerReportController {

    private final ReportService reportService;

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        return "manager/reports";
    }

    // ========== REPORTS API ==========

    @GetMapping("/api/reports/members")
    public ResponseEntity<byte[]> exportMembersReport(@AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            User currentUser = userDetails.getUser();
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().build();
            }

            byte[] excelData = reportService.generateClassMembersReport(currentUser.getStudentClass());
            String filename = "DanhSachThanhVien_" + currentUser.getStudentClass().getCode() + "_" + 
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/reports/point-requests")
    public ResponseEntity<byte[]> exportPointRequestsReport(@AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            User currentUser = userDetails.getUser();
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().build();
            }

            byte[] excelData = reportService.generatePointRequestsReport(currentUser.getStudentClass());
            String filename = "YeuCauDiem_" + currentUser.getStudentClass().getCode() + "_" + 
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
