package com.renterp.domain.property.service;

import com.renterp.domain.property.repository.PropertyRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JoinCodeGeneratorTest {

    @Test
    void codeIsBuiltFromNameAndCity() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.existsByJoinCode(anyString())).thenReturn(false);
        String code = new JoinCodeGenerator(repo).newCode("Shrestha Residency", "Kathmandu");
        assertTrue(code.matches("^SR-KTH-\\d{4}$"), code);
    }

    @Test
    void oddNamesStillGiveAValidCode() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.existsByJoinCode(anyString())).thenReturn(false);
        JoinCodeGenerator g = new JoinCodeGenerator(repo);
        for (String[] in : new String[][]{{"घर", null}, {"A", "P"}, {"  ", ""}, {"Café Ñandú", "Pokhara-15"}}) {
            assertTrue(g.newCode(in[0], in[1]).matches("^[A-Z]{2}-[A-Z]{3}-\\d{4}$"), in[0] + " / " + in[1]);
        }
        assertEquals("CN", JoinCodeGenerator.initials("Café Ñandú", 2));
        assertEquals("PKH", JoinCodeGenerator.cityPart("Pokhara"));
    }

    @Test
    void aTakenCodeIsNotReused() {
        PropertyRepository repo = mock(PropertyRepository.class);
        when(repo.existsByJoinCode(anyString())).thenReturn(true, true, false);
        new JoinCodeGenerator(repo).newCode("Shrestha Residency", "Kathmandu");
        verify(repo, times(3)).existsByJoinCode(anyString());
    }
}
