package org.example.controller; // 1. 確保 Package 路徑包含 org.example

import org.example.ForeignLanguagesBookApplication;
import org.example.controller.AdminBookController;
import org.example.entity.Book;
import org.example.service.BookService;
import org.example.service.JwtService; // 2. 導入 JwtService
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.example.repository.UserRepository; // 🌟 確保導入這個
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // 3. 導入 Security 測試工具
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
// 4. 指定啟動類別，解決 Unable to find a @SpringBootConfiguration 問題
@WebMvcTest(AdminBookController.class)
@ContextConfiguration(classes = ForeignLanguagesBookApplication.class)
class AdminBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    // 🌟 修正點 1: Mock 掉 JwtService
    @MockBean
    private JwtService jwtService;

    // 🌟 修正點 2: Mock 掉 UserRepository，解決 Log 中的 Parameter 1 error
    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN") // 6. 模擬管理員權限，否則會噴 403 Forbidden
    @DisplayName("🧪 異常路徑：當 Service 報錯時，Controller 應回傳 400 Bad Request")
    void shouldReturnBadRequestWhenServiceFails() throws Exception {
        when(bookService.createBook(any())).thenThrow(new RuntimeException("無效的語言分類: XYZ"));

        mockMvc.perform(post("/api/admin/books")
                        .with(csrf()) // 🌟 加入這行
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test Book\", \"lang\":\"XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("無效的語言分類: XYZ"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("🧪 正常路徑：成功切換書籍上下架狀態應回傳 200 OK")
    void shouldUpdateBookStatusSuccessfully() throws Exception {
        when(bookService.updateBookStatus(eq(1L), any(Boolean.class)))
                .thenReturn(new Book());

        mockMvc.perform(patch("/api/admin/books/1/status")
                        .with(csrf()) // 🌟 加入這行
                        .param("onsale", "true"))
                .andExpect(status().isOk());
    }
}