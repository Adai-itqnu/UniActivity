package com.example.uniactivity.controller.admin;

import com.example.uniactivity.dto.activity.*;
import com.example.uniactivity.enums.ActivityScope;
import com.example.uniactivity.enums.ActivityStatus;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.AcademicYearService;
import com.example.uniactivity.service.FacultyService;
import com.example.uniactivity.service.ScoringRulesService;
import com.example.uniactivity.service.SemesterService;
import com.example.uniactivity.service.StudentClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final SemesterService semesterService;
    private final FacultyService facultyService;
    private final AcademicYearService academicYearService;
    private final StudentClassService studentClassService;
    private final ScoringRulesService scoringRulesService;

    @GetMapping
    public String listActivities(Model model) {
        model.addAttribute("activities", activityService.getAllActivities());
        model.addAttribute("semesters", semesterService.getAllSemesters());
        model.addAttribute("scopes", ActivityScope.values());
        model.addAttribute("statuses", ActivityStatus.values());
        model.addAttribute("scoringRulesJson", scoringRulesService.getScoringRules().toString());
        // For ActivitySlot modal
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("academicYears", academicYearService.getActiveAcademicYears());
        model.addAttribute("classes", studentClassService.getAllClasses());
        return "admin/activity-list";
    }
    
    @GetMapping("/{id}")
    public String viewActivityDetail(@PathVariable Long id, Model model) {
        var activity = activityService.getActivityById(id);
        var slots = activityService.getSlotsByActivity(id);
        
        model.addAttribute("activity", activity);
        model.addAttribute("slots", slots);
        model.addAttribute("scoreOptions", activityService.getScoreOptionsByActivity(id));
        
        // Calculate slot statistics
        int totalMaxSlots = slots.stream().mapToInt(s -> s.getMaxQuantity() != null ? s.getMaxQuantity() : 0).sum();
        int totalRegistered = slots.stream().mapToInt(s -> s.getCurrentQuantity() != null ? s.getCurrentQuantity() : 0).sum();
        int totalRemaining = totalMaxSlots - totalRegistered;
        
        model.addAttribute("totalMaxSlots", totalMaxSlots);
        model.addAttribute("totalRegistered", totalRegistered);
        model.addAttribute("totalRemaining", totalRemaining);
        
        // Check deadline
        boolean isDeadlinePassed = activity.getRegistrationDeadline() != null 
            && activity.getRegistrationDeadline().isBefore(java.time.LocalDateTime.now());
        model.addAttribute("isDeadlinePassed", isDeadlinePassed);
        
        // For editing
        model.addAttribute("scopes", ActivityScope.values());
        model.addAttribute("statuses", ActivityStatus.values());
        model.addAttribute("scoringRulesJson", scoringRulesService.getScoringRules().toString());
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("classes", studentClassService.getAllClasses());
        return "admin/activity-detail";
    }
    
    @GetMapping("/create")
    public String createActivityWizard(Model model) {
        model.addAttribute("scopes", ActivityScope.values());
        model.addAttribute("statuses", ActivityStatus.values());
        model.addAttribute("scoringRulesJson", scoringRulesService.getScoringRules().toString());
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("classes", studentClassService.getAllClasses());
        return "admin/activity-create";
    }

    // ========== Activity REST API ==========

    @GetMapping("/api")
    @ResponseBody
    public List<ActivityResponseDto> getAllActivities() {
        return activityService.getAllActivities();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ActivityResponseDto getActivityById(@PathVariable Long id) {
        return activityService.getActivityById(id);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<ActivityResponseDto> createActivity(@Valid @RequestBody ActivityDto dto) {
        return ResponseEntity.ok(activityService.createActivity(dto));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<ActivityResponseDto> updateActivity(@PathVariable Long id, @Valid @RequestBody ActivityDto dto) {
        return ResponseEntity.ok(activityService.updateActivity(id, dto));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return ResponseEntity.ok().build();
    }

    // ========== Activity Slot REST API ==========

    @GetMapping("/api/{activityId}/slots")
    @ResponseBody
    public List<ActivitySlotResponseDto> getSlotsByActivity(@PathVariable Long activityId) {
        return activityService.getSlotsByActivity(activityId);
    }

    @PostMapping("/api/{activityId}/slots")
    @ResponseBody
    public ResponseEntity<ActivitySlotResponseDto> createSlot(@PathVariable Long activityId, @Valid @RequestBody ActivitySlotDto dto) {
        return ResponseEntity.ok(activityService.createSlot(activityId, dto));
    }

    @DeleteMapping("/api/slots/{slotId}")
    @ResponseBody
    public ResponseEntity<Void> deleteSlot(@PathVariable Long slotId) {
        activityService.deleteSlot(slotId);
        return ResponseEntity.ok().build();
    }

    // ========== Score Option REST API ==========

    @GetMapping("/api/{activityId}/score-options")
    @ResponseBody
    public List<ScoreOptionResponseDto> getScoreOptionsByActivity(@PathVariable Long activityId) {
        return activityService.getScoreOptionsByActivity(activityId);
    }

    @PostMapping("/api/{activityId}/score-options")
    @ResponseBody
    public ResponseEntity<ScoreOptionResponseDto> createScoreOption(@PathVariable Long activityId, @Valid @RequestBody ScoreOptionDto dto) {
        return ResponseEntity.ok(activityService.createScoreOption(activityId, dto));
    }

    @DeleteMapping("/api/score-options/{scoreOptionId}")
    @ResponseBody
    public ResponseEntity<Void> deleteScoreOption(@PathVariable Long scoreOptionId) {
        activityService.deleteScoreOption(scoreOptionId);
        return ResponseEntity.ok().build();
    }

    // Banner upload API
    @PostMapping("/api/upload-banner")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> uploadBanner(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", "File không được để trống"));
            }
            
            // Save to resources/uploads/activities
            String basePath = System.getProperty("user.dir");
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(basePath, "src", "main", "resources", "uploads", "activities");
            java.nio.file.Files.createDirectories(uploadPath);
            
            // Generate unique filename
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String fileName = java.util.UUID.randomUUID().toString().substring(0, 8) + extension;
            java.nio.file.Path filePath = uploadPath.resolve(fileName);
            
            java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            String bannerUrl = "/uploads/activities/" + fileName;
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("bannerUrl", bannerUrl));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
