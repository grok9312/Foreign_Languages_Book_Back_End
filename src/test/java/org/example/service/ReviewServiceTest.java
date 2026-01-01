package org.example.service;

import org.example.dto.ReviewResponse;
import org.example.entity.Book;
import org.example.entity.Review;
import org.example.entity.User;
import org.example.repository.BookRepository;
import org.example.repository.ReviewRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewService reviewService;

    private User mockUser;
    private Book mockBook;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setUserId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setUsername("Test User");

        mockBook = new Book();
        mockBook.setBookId(101L);
        mockBook.setTitle("Test Book");
    }

    // ==========================================
    // 測試 addReview (新增評論)
    // ==========================================

    @Test
    @DisplayName("新增評論成功：資料正確")
    void testAddReview_Success() {
        // Arrange
        String email = "test@example.com";
        Integer bookId = 101;
        Integer rating = 5;
        String content = "Great book!";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(101L)).thenReturn(Optional.of(mockBook));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setReviewId(1);
            return r;
        });

        // Act
        Review result = reviewService.addReview(email, bookId, rating, content);

        // Assert
        assertNotNull(result);
        assertEquals(rating, result.getRating());
        assertEquals(content, result.getContent());
        assertEquals(mockUser, result.getUser());
        assertEquals(mockBook, result.getBook());
        
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("新增評論失敗：評分超出範圍 (大於 5)")
    void testAddReview_RatingTooHigh() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(anyLong())).thenReturn(Optional.of(mockBook));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            reviewService.addReview("test@example.com", 101, 6, "Too good!");
        });

        assertEquals("評分必須在 1 到 5 之間", exception.getMessage());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("新增評論失敗：評分超出範圍 (小於 1)")
    void testAddReview_RatingTooLow() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(anyLong())).thenReturn(Optional.of(mockBook));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            reviewService.addReview("test@example.com", 101, 0, "Bad!");
        });

        assertEquals("評分必須在 1 到 5 之間", exception.getMessage());
    }

    @Test
    @DisplayName("新增評論失敗：用戶不存在")
    void testAddReview_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reviewService.addReview("unknown@example.com", 101, 5, "Content");
        });

        assertEquals("找不到該用戶", exception.getMessage());
    }

    // ==========================================
    // 測試 getReviewsByBookId (查詢評論)
    // ==========================================

    @Test
    @DisplayName("查詢評論成功：應正確轉換 DTO 並包含用戶名")
    void testGetReviewsByBookId_Success() {
        // Arrange
        Review review = new Review();
        review.setReviewId(1);
        review.setContent("Nice");
        review.setRating(4);
        review.setCreatedAt(LocalDateTime.now());
        review.setUser(mockUser); // 設定關聯用戶
        review.setBook(mockBook);

        when(reviewRepository.findByBook_BookIdOrderByCreatedAtDesc(101)).thenReturn(List.of(review));

        // Act
        List<ReviewResponse> responses = reviewService.getReviewsByBookId(101);

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Test User", responses.get(0).getUsername()); // 驗證有抓到名字
        assertEquals(4, responses.get(0).getRating());
    }

    @Test
    @DisplayName("查詢評論成功：若用戶為空 (資料異常)，應顯示匿名讀者")
    void testGetReviewsByBookId_NullUser() {
        // Arrange
        Review review = new Review();
        review.setReviewId(2);
        review.setContent("Anonymous review");
        review.setRating(3);
        review.setUser(null); // 模擬用戶資料遺失

        when(reviewRepository.findByBook_BookIdOrderByCreatedAtDesc(101)).thenReturn(List.of(review));

        // Act
        List<ReviewResponse> responses = reviewService.getReviewsByBookId(101);

        // Assert
        assertEquals("匿名讀者", responses.get(0).getUsername()); // 驗證防呆邏輯
    }
}
