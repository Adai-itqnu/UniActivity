package com.example.uniactivity.controller.admin;

import com.example.uniactivity.dto.admin.SemesterDto;
import com.example.uniactivity.dto.admin.SemesterResponseDto;
import com.example.uniactivity.service.SemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @GetMapping
    public String listSemesters(Model model) {
        model.addAttribute("semesters", semesterService.getAllSemesters());
        return "admin/semester-list";
    }

    // ========== REST API ==========

    @GetMapping("/api")
    @ResponseBody
    public List<SemesterResponseDto> getAllSemesters() {
        return semesterService.getAllSemesters();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public SemesterResponseDto getSemesterById(@PathVariable Long id) {
        return semesterService.getSemesterById(id);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<SemesterResponseDto> createSemester(@Valid @RequestBody SemesterDto dto) {
        return ResponseEntity.ok(semesterService.createSemester(dto));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<SemesterResponseDto> updateSemester(@PathVariable Long id, @Valid @RequestBody SemesterDto dto) {
        return ResponseEntity.ok(semesterService.updateSemester(id, dto));
    }
    
    @PostMapping("/api/{id}/set-current")
    @ResponseBody
    public ResponseEntity<Void> setCurrentSemester(@PathVariable Long id) {
        semesterService.setCurrentSemester(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteSemester(@PathVariable Long id) {
        semesterService.deleteSemester(id);
        return ResponseEntity.ok().build();
    }
}
