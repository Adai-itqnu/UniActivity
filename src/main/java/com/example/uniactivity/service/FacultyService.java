package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.FacultyDto;
import com.example.uniactivity.dto.admin.FacultyResponseDto;
import com.example.uniactivity.entity.Faculty;
import com.example.uniactivity.enums.CommonStatus;
import com.example.uniactivity.exception.DuplicateException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.FacultyMapper;
import com.example.uniactivity.repository.FacultyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository facultyRepository;
    private final FacultyMapper facultyMapper;

    public List<FacultyResponseDto> getAllFaculties() {
        return facultyRepository.findAll().stream()
                .map(facultyMapper::toResponseDto)
                .toList();
    }

    public List<FacultyResponseDto> getActiveFaculties() {
        return facultyRepository.findByStatus(CommonStatus.ACTIVE).stream()
                .map(facultyMapper::toResponseDto)
                .toList();
    }
    
    public long countFaculties() {
        return facultyRepository.count();
    }

    public FacultyResponseDto getFacultyById(Long id) {
        return facultyMapper.toResponseDto(findById(id));
    }

    @Transactional
    public FacultyResponseDto createFaculty(FacultyDto dto) {
        if (facultyRepository.existsByCode(dto.getCode())) {
            throw new DuplicateException("Mã khoa", dto.getCode());
        }
        Faculty faculty = facultyMapper.toEntity(dto);
        return facultyMapper.toResponseDto(facultyRepository.save(faculty));
    }

    @Transactional
    public FacultyResponseDto updateFaculty(Long id, FacultyDto dto) {
        Faculty faculty = findById(id);
        facultyMapper.updateEntity(dto, faculty);
        return facultyMapper.toResponseDto(facultyRepository.save(faculty));
    }

    @Transactional
    public void deleteFaculty(Long id) {
        facultyRepository.delete(findById(id));
    }
    
    private Faculty findById(Long id) {
        return facultyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khoa", id));
    }
}
