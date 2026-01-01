package org.example.controller; // 1. 確保 Package 路徑包含 org.example

import org.example.controller.BookController;
import org.example.entity.Book;
import org.example.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.example.service.JwtService;         // 🌟 新增
import org.example.repository.UserRepository;   // 🌟 新增
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;
    // 🌟 必須補上這兩個 MockBean，否則啟動時 JwtAuthenticationFilter 會找不到依賴
    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;
    @Test
    @WithMockUser
    @DisplayName("🧪 前台測試：根據語言查詢書籍應回傳 200 與資料內容")
    void shouldReturnBooksByLanguage() throws Exception {
        // 準備模擬資料
        Book book1 = new Book();
        book1.setTitle("日語檢定 N1 必勝");
        List<Book> mockBooks = Arrays.asList(book1);

        // 模擬 Service 行為
        when(bookService.getOnsaleBooksByLang("japanese")).thenReturn(mockBooks);

        // 執行請求並驗證
        mockMvc.perform(get("/api/public/books/lang/japanese"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("日語檢定 N1 必勝"));
    }
}