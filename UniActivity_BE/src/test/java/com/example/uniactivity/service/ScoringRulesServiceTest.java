package com.example.uniactivity.service;

import com.example.uniactivity.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoringRulesServiceTest {

    private ScoringRulesService service;

    @BeforeEach
    void setUp() {
        service = new ScoringRulesService(new ObjectMapper());
        service.loadRules();
    }

    @Test
    void rejectsNonFiniteGpa() {
        assertThrows(ValidationException.class,
                () -> service.calculateAcademicScore(Double.NaN, 8));
        assertThrows(ValidationException.class,
                () -> service.calculateAcademicScore(8, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsGpaOutsideZeroToTen() {
        assertThrows(ValidationException.class,
                () -> service.calculateAcademicScore(-0.1, 8));
        assertThrows(ValidationException.class,
                () -> service.calculateAcademicScore(8, 10.1));
    }

    @Test
    void exposesExplicitClaimedScoreCeilings() {
        assertEquals(22, service.getMaximumClaimedScore("1.1"));
        assertEquals(5, service.getMaximumClaimedScore("1.3"));
        assertEquals(10, service.getMaximumClaimedScore("6.1"));
        assertThrows(ValidationException.class,
                () -> service.getMaximumClaimedScore("unknown"));
    }
}
