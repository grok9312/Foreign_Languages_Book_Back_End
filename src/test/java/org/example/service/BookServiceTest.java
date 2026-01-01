package org.example.service; // 1. 確保 Package 路徑包含 org.example

import org.example.dto.BookRequest;
import org.example.repository.BookRepository;
import org.example.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    @DisplayName("測試有效與無效的語言解析")
    void testParseLanguage() {
        // 1. 測試正常解析（不分大小寫）
        assertDoesNotThrow(() -> {
            // 這裡需要透過反射或間接調用，因為 parseLanguage 是 private
            // 或者測試調用它的 public 方法 getOnsaleBooksByLang("french")
            bookService.getOnsaleBooksByLang("french");
        });

        // 2. 測試無效語言（Klingon）應拋出異常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            bookService.getOnsaleBooksByLang("klingon");
        });
        assertEquals("無效的語言分類: klingon", exception.getMessage());
    }

    @Test
    @DisplayName("測試 ISBN 重複時應拒絕創建書籍")
    void testCreateBookWithDuplicateIsbn() {
        BookRequest request = new BookRequest();
        request.setIsbn("123456789");
        request.setLang("ENGLISH");

        // 模擬 Repository：當檢查此 ISBN 時回傳 true (已存在)
        when(bookRepository.existsByIsbn("123456789")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            bookService.createBook(request);
        });

        assertEquals("ISBN 國際標準書號已存在", exception.getMessage());
        // 確保 save 方法從未被調用（保證資料品質）
        verify(bookRepository, never()).save(any());
    }
}