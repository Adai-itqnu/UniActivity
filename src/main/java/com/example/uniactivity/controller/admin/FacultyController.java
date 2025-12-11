package com.example.uniactivity.controller.admin;

import com.example.uniactivity.dto.admin.FacultyDto;
import com.example.uniactivity.dto.admin.FacultyResponseDto;
import com.example.uniactivity.service.FacultyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/faculties")
@RequiredArgsConstructor
public class FacultyController {

    private final FacultyService facultyService;

    @GetMapping
    public String listFaculties(Model model) {
        model.addAttribute("faculties", facultyService.getAllFaculties());
        return "admin/faculty-list";
    }

    // ========== REST API ==========

    @GetMapping("/api")
    @ResponseBody
    public List<FacultyResponseDto> getAllFaculties() {
        return facultyService.getAllFaculties();
    }

    @GetMapping("/api/active")
    @ResponseBody
    public List<FacultyResponseDto> getActiveFaculties() {
        return facultyService.getActiveFaculties();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public FacultyResponseDto getFacultyById(@PathVariable Long id) {
        return facultyService.getFacultyById(id);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<FacultyResponseDto> createFaculty(@Valid @RequestBody FacultyDto dto) {
        return ResponseEntity.ok(facultyService.createFaculty(dto));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<FacultyResponseDto> updateFaculty(@PathVariable Long id, @Valid @RequestBody FacultyDto dto) {
        return ResponseEntity.ok(facultyService.updateFaculty(id, dto));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteFaculty(@PathVariable Long id) {
        facultyService.deleteFaculty(id);
        return ResponseEntity.ok().build();
    }
}
