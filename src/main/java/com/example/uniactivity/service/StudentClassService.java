package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.StudentClassDto;
import com.example.uniactivity.dto.admin.StudentClassResponseDto;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.exception.DuplicateException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.StudentClassMapper;
import com.example.uniactivity.repository.AcademicYearRepository;
import com.example.uniactivity.repository.FacultyRepository;
import com.example.uniactivity.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentClassService {

    private final StudentClassRepository studentClassRepository;
    private final FacultyRepository facultyRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentClassMapper studentClassMapper;

    public List<StudentClassResponseDto> getAllClasses() {
        return studentClassRepository.findAll().stream()
                .map(studentClassMapper::toResponseDto)
                .toList();
    }
    
    public long countClasses() {
        return studentClassRepository.count();
    }

    public StudentClassResponseDto getClassById(Long id) {
        return studentClassMapper.toResponseDto(findById(id));
    }

    @Transactional
    public StudentClassResponseDto createClass(StudentClassDto dto) {
        if (studentClassRepository.existsByCode(dto.getCode())) {
            throw new DuplicateException("Mã lớp", dto.getCode());
        }
        
        StudentClass entity = studentClassMapper.toEntity(dto);
        setRelations(entity, dto);
        entity.setJoinCode(generateJoinCode());
        
        return studentClassMapper.toResponseDto(studentClassRepository.save(entity));
    }

    @Transactional
    public StudentClassResponseDto updateClass(Long id, StudentClassDto dto) {
        StudentClass entity = findById(id);
        studentClassMapper.updateEntity(dto, entity);
        setRelations(entity, dto);
        
        return studentClassMapper.toResponseDto(studentClassRepository.save(entity));
    }
    
    @Transactional
    public StudentClassResponseDto regenerateJoinCode(Long id) {
        StudentClass entity = findById(id);
        entity.setJoinCode(generateJoinCode());
        return studentClassMapper.toResponseDto(studentClassRepository.save(entity));
    }

    @Transactional
    public void deleteClass(Long id) {
        studentClassRepository.delete(findById(id));
    }
    
    private StudentClass findById(Long id) {
        return studentClassRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lớp", id));
    }
    
    private void setRelations(StudentClass entity, StudentClassDto dto) {
        entity.setFaculty(dto.getFacultyId() != null 
                ? facultyRepository.findById(dto.getFacultyId())
                        .orElseThrow(() -> new NotFoundException("Khoa", dto.getFacultyId()))
                : null);
        
        entity.setAcademicYear(dto.getAcademicYearId() != null 
                ? academicYearRepository.findById(dto.getAcademicYearId())
                        .orElseThrow(() -> new NotFoundException("Khóa học", dto.getAcademicYearId()))
                : null);
    }
    
    private String generateJoinCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (studentClassRepository.existsByJoinCode(code));
        return code;
    }
}
