package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.PointRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing training point requests
 */
@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerPointController {

    private final PointRequestService pointRequestService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @GetMapping("/point-requests")
    public String pointRequests(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        
        if (currentUser.getStudentClass() != null) {
            List<PointRequest> pendingRequests = pointRequestService.getPendingRequestsForClass(currentUser.getStudentClass());
            model.addAttribute("pendingRequests", pendingRequests);
        }
        
        return "manager/point-requests";
    }

    // ========== API ==========

@PostMapping("/api/point-requests/{id}/approve")
    @ResponseBody
    public ResponseEntity<?> approvePointRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String comment = body != null ? body.get("comment") : null;
        pointRequestService.approveRequest(id, userDetails.getUser(), comment);
        return ResponseEntity.ok(Map.of("message", "Đã duyệt yêu cầu điểm"));
    }

    @PostMapping("/api/point-requests/{id}/reject")
    @ResponseBody
    public ResponseEntity<?> rejectPointRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String comment = body != null ? body.get("comment") : null;
        pointRequestService.rejectRequest(id, userDetails.getUser(), comment);
        return ResponseEntity.ok(Map.of("message", "Đã từ chối yêu cầu điểm"));
    }
}
