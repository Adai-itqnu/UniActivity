package com.example.uniactivity.controller.admin;

import com.example.uniactivity.service.AcademicYearService;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.FacultyService;
import com.example.uniactivity.service.SemesterService;
import com.example.uniactivity.service.StudentClassService;
import com.example.uniactivity.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FacultyService facultyService;
    private final StudentClassService studentClassService;
    private final UserManagementService userManagementService;
    private final AcademicYearService academicYearService;
    private final ActivityService activityService;
    private final SemesterService semesterService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Main statistics cards
        model.addAttribute("totalFaculties", facultyService.countFaculties());
        model.addAttribute("totalClasses", studentClassService.countClasses());
        model.addAttribute("totalStudents", userManagementService.countStudents());
        model.addAttribute("totalUsers", userManagementService.countAllUsers());
        model.addAttribute("totalAcademicYears", academicYearService.countAcademicYears());
        model.addAttribute("totalActivities", activityService.countActivities());
        model.addAttribute("activeActivities", activityService.countActiveActivities());
        model.addAttribute("totalSemesters", semesterService.countSemesters());
        
        // Detail lists for breakdown view
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("recentActivities", activityService.getRecentActivities(5));
        model.addAttribute("classes", studentClassService.getAllClasses());
        model.addAttribute("currentSemester", semesterService.getCurrentSemester());
        
        return "admin/dashboard";
    }
}
