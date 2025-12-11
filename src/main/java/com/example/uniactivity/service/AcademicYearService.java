package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.AcademicYearDto;
import com.example.uniactivity.dto.admin.AcademicYearResponseDto;
import com.example.uniactivity.entity.AcademicYear;
import com.example.uniactivity.enums.CommonStatus;
import com.example.uniactivity.exception.DuplicateException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.AcademicYearMapper;
import com.example.uniactivity.repository.AcademicYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearMapper academicYearMapper;

    public List<AcademicYearResponseDto> getAllAcademicYears() {
        return academicYearRepository.findAllByOrderByStartYearDesc().stream()
                .map(academicYearMapper::toResponseDto)
                .toList();
    }

    public List<AcademicYearResponseDto> getActiveAcademicYears() {
        return academicYearRepository.findByStatus(CommonStatus.ACTIVE).stream()
                .map(academicYearMapper::toResponseDto)
                .toList();
    }
    
    public long countAcademicYears() {
        return academicYearRepository.count();
    }

    public AcademicYearResponseDto getAcademicYearById(Long id) {
        return academicYearMapper.toResponseDto(findById(id));
    }

    @Transactional
    public AcademicYearResponseDto createAcademicYear(AcademicYearDto dto) {
        if (academicYearRepository.existsByCode(dto.getCode())) {
            throw new DuplicateException("Mã khóa học", dto.getCode());
        }
        AcademicYear entity = academicYearMapper.toEntity(dto);
        return academicYearMapper.toResponseDto(academicYearRepository.save(entity));
    }

    @Transactional
    public AcademicYearResponseDto updateAcademicYear(Long id, AcademicYearDto dto) {
        AcademicYear entity = findById(id);
        academicYearMapper.updateEntity(dto, entity);
        return academicYearMapper.toResponseDto(academicYearRepository.save(entity));
    }

    @Transactional
    public void deleteAcademicYear(Long id) {
        academicYearRepository.delete(findById(id));
    }
    
    private AcademicYear findById(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khóa học", id));
    }
}
