package com.example.uniactivity.controller.admin;

import com.example.uniactivity.dto.admin.StudentClassDto;
import com.example.uniactivity.dto.admin.StudentClassResponseDto;
import com.example.uniactivity.service.AcademicYearService;
import com.example.uniactivity.service.FacultyService;
import com.example.uniactivity.service.QrCodeService;
import com.example.uniactivity.service.StudentClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/classes")
@RequiredArgsConstructor
public class StudentClassController {

    private final StudentClassService studentClassService;
    private final FacultyService facultyService;
    private final AcademicYearService academicYearService;
    private final QrCodeService qrCodeService;

    @GetMapping
    public String listClasses(Model model) {
        model.addAttribute("classes", studentClassService.getAllClasses());
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("academicYears", academicYearService.getActiveAcademicYears());
        return "admin/class-list";
    }

    // ========== REST API ==========

    @GetMapping("/api")
    @ResponseBody
    public List<StudentClassResponseDto> getAllClasses() {
        return studentClassService.getAllClasses();
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public StudentClassResponseDto getClassById(@PathVariable Long id) {
        return studentClassService.getClassById(id);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<StudentClassResponseDto> createClass(@Valid @RequestBody StudentClassDto dto) {
        return ResponseEntity.ok(studentClassService.createClass(dto));
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<StudentClassResponseDto> updateClass(@PathVariable Long id, @Valid @RequestBody StudentClassDto dto) {
        return ResponseEntity.ok(studentClassService.updateClass(id, dto));
    }
    
    @PostMapping("/api/{id}/regenerate-code")
    @ResponseBody
    public ResponseEntity<StudentClassResponseDto> regenerateJoinCode(@PathVariable Long id) {
        return ResponseEntity.ok(studentClassService.regenerateJoinCode(id));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteClass(@PathVariable Long id) {
        studentClassService.deleteClass(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Generate QR code image for class join code using ZXing
     * Only admin/manager can access this endpoint
     */
    @GetMapping("/api/{id}/qrcode")
    public ResponseEntity<byte[]> getQrCode(@PathVariable Long id) {
        try {
            StudentClassResponseDto classDto = studentClassService.getClassById(id);
            String joinCode = classDto.getJoinCode();
            
            if (joinCode == null || joinCode.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            byte[] qrImage = qrCodeService.generateQrCode(joinCode);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("inline", "qr_" + classDto.getCode() + ".png");
            
            return new ResponseEntity<>(qrImage, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Download QR code as attachment
     */
    @GetMapping("/api/{id}/qrcode/download")
    public ResponseEntity<byte[]> downloadQrCode(@PathVariable Long id) {
        try {
            StudentClassResponseDto classDto = studentClassService.getClassById(id);
            String joinCode = classDto.getJoinCode();
            
            if (joinCode == null || joinCode.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            byte[] qrImage = qrCodeService.generateQrCode(joinCode, 400, 400);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", "QR_" + classDto.getCode() + ".png");
            
            return new ResponseEntity<>(qrImage, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
