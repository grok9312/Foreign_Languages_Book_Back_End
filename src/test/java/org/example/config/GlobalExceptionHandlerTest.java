package org.example.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("處理 RuntimeException：應回傳 400 與錯誤訊息")
    void testHandleRuntimeExceptions() {
        // Arrange
        String errorMessage = "庫存不足";
        RuntimeException ex = new RuntimeException(errorMessage);

        // Act
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleRuntimeExceptions(ex);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, response.getBody().get("message"));
    }

    @Test
    @DisplayName("處理驗證錯誤：應回傳 400 與欄位錯誤細節")
    void testHandleValidationExceptions() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        // 模擬一個欄位錯誤 (例如 email 格式錯誤)
        FieldError fieldError = new FieldError("userDto", "email", "Email 格式不正確");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        // Act
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleValidationExceptions(ex);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, String> errors = response.getBody();
        assertNotNull(errors);
        assertTrue(errors.containsKey("email"));
        assertEquals("Email 格式不正確", errors.get("email"));
    }
}
