package org.example.service;

import org.example.dto.BookRequest;
import org.example.entity.Book;
import org.example.entity.Language;
import org.example.repository.BookRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    private static StringBuilder reportBuilder = new StringBuilder();

    @BeforeAll
    static void initReport() {
        reportBuilder.append("<html><head><meta charset='UTF-8'><title>進階邊界測試報告</title>")
                .append("<style>body{font-family:sans-serif;padding:20px;} .pass{color:green;font-weight:bold;} table{border-collapse:collapse;width:100%;} th,td{border:1px solid #ccc;padding:10px;text-align:left;} th{background:#f4f4f4;}</style>")
                .append("</head><body><h1>全端自動化測試：邊界與邏輯專項報告</h1>")
                .append("<p>執行時間: ").append(LocalDateTime.now()).append("</p>")
                .append("<table><tr><th>測試項目</th><th>測試數據</th><th>預期結果</th><th>耗時</th><th>狀態</th></tr>");
    }

    @AfterAll
    static void exportReport() {
        reportBuilder.append("</table></body></html>");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("Boundary_Test_Report.html"))) {
            writer.write(reportBuilder.toString());
            System.out.println("成功更新報告：Boundary_Test_Report.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("測試正常新增書籍 (Happy Path)")
    void testCreateBookSuccess() {
        long start = System.currentTimeMillis();
        BookRequest request = new BookRequest();
        request.setTitle("Java 程式設計");
        request.setIsbn("1234567890123");
        request.setPrice(BigDecimal.valueOf(500));
        // 修正：使用 Enum 中存在的語言
        request.setLang("ENGLISH");

        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenReturn(new Book());

        assertDoesNotThrow(() -> bookService.createBook(request));

        addReportRow("正常新增書籍 (成功路徑)", "Price=500, Title=Java...", "成功寫入資料庫", start);
    }

    @Test
    @DisplayName("測試價格邊界：負數攔截")
    void testCreateBookWithNegativePrice() {
        long start = System.currentTimeMillis();
        BookRequest request = new BookRequest();
        request.setPrice(BigDecimal.valueOf(-1));
        request.setTitle("測試書籍");
        request.setLang("ENGLISH"); // 補上語言，避免 NullPointerException

        assertThrows(RuntimeException.class, () -> bookService.createBook(request));

        addReportRow("價格邊界攔截", "Price = -1", "拋出異常並拒絕存檔", start);
    }

    @Test
    @DisplayName("測試標題邊界：空白標題攔截")
    void testCreateBookWithEmptyTitle() {
        long start = System.currentTimeMillis();
        BookRequest request = new BookRequest();
        request.setTitle(""); // 空白標題
        request.setPrice(BigDecimal.valueOf(100));
        request.setLang("ENGLISH"); // 補上語言

        // 註：這需要在 BookService 裡補上 request.getTitle().isEmpty() 的檢查
        assertThrows(RuntimeException.class, () -> bookService.createBook(request));

        addReportRow("標題邊界攔截", "Title = '' (Empty)", "禁止創建無標題書籍", start);
    }

    @Test
    @DisplayName("測試 ISBN 重複攔截")
    void testCreateBookWithDuplicateIsbn() {
        long start = System.currentTimeMillis();
        BookRequest request = new BookRequest();
        request.setIsbn("123456");
        request.setTitle("Some Title"); // 補上標題
        request.setLang("ENGLISH"); // 補上語言
        when(bookRepository.existsByIsbn("123456")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> bookService.createBook(request));

        addReportRow("ISBN 重複攔截", "Isbn = 123456", "拋出重複異常", start);
    }

    // 輔助方法：簡化報告寫入
    private void addReportRow(String name, String data, String expected, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        reportBuilder.append("<tr>")
                .append("<td>").append(name).append("</td>")
                // 修正：將 'a' 改為 'append'
                .append("<td>").append(data).append("</td>")
                .append("<td>").append(expected).append("</td>")
                .append("<td>").append(duration).append(" ms</td>")
                .append("<td><span class='pass'>✅ PASSED</span></td>")
                .append("</tr>");
    }
}
