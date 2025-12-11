package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.SemesterDto;
import com.example.uniactivity.dto.admin.SemesterResponseDto;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.SemesterMapper;
import com.example.uniactivity.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final SemesterMapper semesterMapper;

    public List<SemesterResponseDto> getAllSemesters() {
        return semesterRepository.findAll().stream()
                .map(semesterMapper::toResponseDto)
                .toList();
    }

    public SemesterResponseDto getSemesterById(Long id) {
        return semesterMapper.toResponseDto(findById(id));
    }
    
    public SemesterResponseDto getCurrentSemester() {
        Semester entity = semesterRepository.findByIsCurrentTrue();
        return entity != null ? semesterMapper.toResponseDto(entity) : null;
    }
    
    public long countSemesters() {
        return semesterRepository.count();
    }

    @Transactional
    public SemesterResponseDto createSemester(SemesterDto dto) {
        Semester entity = semesterMapper.toEntity(dto);
        if (Boolean.TRUE.equals(entity.getIsCurrent())) {
            clearCurrentSemester();
        }
        return semesterMapper.toResponseDto(semesterRepository.save(entity));
    }

    @Transactional
    public SemesterResponseDto updateSemester(Long id, SemesterDto dto) {
        Semester entity = findById(id);
        
        if (Boolean.TRUE.equals(dto.getIsCurrent()) && !Boolean.TRUE.equals(entity.getIsCurrent())) {
            clearCurrentSemester();
        }
        
        semesterMapper.updateEntity(dto, entity);
        return semesterMapper.toResponseDto(semesterRepository.save(entity));
    }
    
    @Transactional
    public void setCurrentSemester(Long id) {
        clearCurrentSemester();
        Semester semester = findById(id);
        semester.setIsCurrent(true);
        semesterRepository.save(semester);
    }
    
    @Transactional
    public void deleteSemester(Long id) {
        semesterRepository.delete(findById(id));
    }
    
    private Semester findById(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Học kỳ", id));
    }
    
    private void clearCurrentSemester() {
        Semester current = semesterRepository.findByIsCurrentTrue();
        if (current != null) {
            current.setIsCurrent(false);
            semesterRepository.save(current);
        }
    }
}
