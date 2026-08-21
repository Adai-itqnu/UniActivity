package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.StudentClassDto;
import com.example.uniactivity.dto.admin.StudentClassResponseDto;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.mapper.StudentClassMapper;
import com.example.uniactivity.repository.AcademicYearRepository;
import com.example.uniactivity.repository.FacultyRepository;
import com.example.uniactivity.repository.StudentClassRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentClassServiceTest {

    @Mock StudentClassRepository studentClassRepository;
    @Mock FacultyRepository facultyRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock UnifiedCodePolicy codePolicy;
    @Spy StudentClassMapper studentClassMapper = new StudentClassMapper();
    @InjectMocks StudentClassService service;

    @Test
    void createClassRetriesCollisionAndPersistsSharedPolicyCode() {
        StudentClassDto dto = classDto();
        StudentClass entity = new StudentClass();
        entity.setId(10L);
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        when(studentClassRepository.existsByCode(dto.getCode())).thenReturn(false);
        when(studentClassMapper.toEntity(dto)).thenReturn(entity);
        when(studentClassRepository.save(entity)).thenReturn(entity);
        when(codePolicy.generateRandomCode()).thenReturn("B2C3D4", "A7K9P2");
        when(studentClassRepository.existsByJoinCode(anyString()))
                .thenAnswer(invocation -> "B2C3D4".equals(invocation.getArgument(0)));

        StudentClassResponseDto response = service.createClass(dto);

        assertEquals("A7K9P2", entity.getJoinCode());
        assertEquals("A7K9P2", response.getJoinCode());
        verify(studentClassRepository).existsByJoinCode("B2C3D4");
        verify(studentClassRepository).existsByJoinCode("A7K9P2");
    }

    @Test
    void regenerateJoinCodeRetriesCollisionAndPersistsSharedPolicyCode() {
        StudentClass entity = new StudentClass();
        entity.setId(10L);
        when(studentClassRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(studentClassRepository.save(entity)).thenReturn(entity);
        when(codePolicy.generateRandomCode()).thenReturn("B2C3D4", "A7K9P2");
        when(studentClassRepository.existsByJoinCode(anyString()))
                .thenAnswer(invocation -> "B2C3D4".equals(invocation.getArgument(0)));

        StudentClassResponseDto response = service.regenerateJoinCode(10L);

        assertEquals("A7K9P2", entity.getJoinCode());
        assertEquals("A7K9P2", response.getJoinCode());
        verify(studentClassRepository).existsByJoinCode("B2C3D4");
        verify(studentClassRepository).existsByJoinCode("A7K9P2");
    }

    @Test
    void regenerateJoinCodeStopsAfterOneThousandCollisionsWithoutSaving() {
        StudentClass entity = new StudentClass();
        entity.setId(10L);
        when(studentClassRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(codePolicy.generateRandomCode()).thenReturn("A7K9P2");
        when(studentClassRepository.existsByJoinCode("A7K9P2")).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.regenerateJoinCode(10L)
        );

        assertEquals("Không thể tạo mã tham gia lớp duy nhất", exception.getMessage());
        verify(codePolicy, times(1_000)).generateRandomCode();
        verify(studentClassRepository, times(1_000)).existsByJoinCode("A7K9P2");
        verify(studentClassRepository, never()).save(any());
    }

    private StudentClassDto classDto() {
        StudentClassDto dto = new StudentClassDto();
        dto.setCode("KTPM01");
        dto.setName("KTPM 01");
        return dto;
    }
}
