package com.example.uniactivity.controller.admin;

import com.example.uniactivity.dto.admin.AcademicYearDto;
import com.example.uniactivity.dto.admin.AcademicYearResponseDto;
import com.example.uniactivity.service.AcademicYearService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @GetMapping
    public String listAcademicYears(Model model) {
        model.addAttribute("academicYears", academicYearService.getAllAcademicYears());
        return "admin/academic-year-list";
    }

    // ========== REST API ==========

    @GetMapping("/api")
    @ResponseBody
    public List<AcademicYearResponseDto> getAllAcademicYears() {
        return academicYearService.getAllAcademicYears();
    }

    @GetMapping("/api/active")
    @ResponseBody
    public List<AcademicYearResponseDto> getActiveAcademicYears() {
        return academicYearService.getActiveAcademicYears();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public AcademicYearResponseDto getAcademicYearById(@PathVariable Long id) {
        return academicYearService.getAcademicYearById(id);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<AcademicYearResponseDto> createAcademicYear(@Valid @RequestBody AcademicYearDto dto) {
        return ResponseEntity.ok(academicYearService.createAcademicYear(dto));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<AcademicYearResponseDto> updateAcademicYear(@PathVariable Long id, @Valid @RequestBody AcademicYearDto dto) {
        return ResponseEntity.ok(academicYearService.updateAcademicYear(id, dto));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteAcademicYear(@PathVariable Long id) {
        academicYearService.deleteAcademicYear(id);
        return ResponseEntity.ok().build();
    }
}
