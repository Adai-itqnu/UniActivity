package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ClassJoinRequestService;
import com.example.uniactivity.service.PointRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for manager dashboard
 */
@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerHomeController {

    private final UserRepository userRepository;
    private final ClassJoinRequestService classJoinRequestService;
    private final PointRequestService pointRequestService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        
        if (currentUser.getStudentClass() != null) {
            // Count members in class
            long memberCount = userRepository.findByStudentClass(currentUser.getStudentClass()).size();
            model.addAttribute("memberCount", memberCount);
            
            // Count pending join requests
            long pendingJoinRequests = classJoinRequestService.getPendingRequestCount(currentUser.getStudentClass());
            model.addAttribute("pendingJoinRequests", pendingJoinRequests);
            
            // Count pending point requests
            long pendingPointRequests = pointRequestService.getPendingRequestCount(currentUser.getStudentClass());
            model.addAttribute("pendingPointRequests", pendingPointRequests);
        } else {
            model.addAttribute("memberCount", 0);
            model.addAttribute("pendingJoinRequests", 0);
            model.addAttribute("pendingPointRequests", 0);
        }
        
        return "manager/dashboard";
    }
}
