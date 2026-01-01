package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.ReviewResponse;
import org.example.entity.Book;
import org.example.entity.Review;
import org.example.entity.User;
import org.example.repository.BookRepository;
import org.example.repository.ReviewRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    // ReviewService.java
    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByBookId(Integer bookId) {
        // 1. 從資料庫抓出 Entity 列表
        List<Review> reviews = reviewRepository.findByBook_BookIdOrderByCreatedAtDesc(bookId);

        // 2. 將 Entity 轉換為 ReviewResponse DTO
        return reviews.stream().map(review -> {
            ReviewResponse dto = new ReviewResponse();
            dto.setReviewId(review.getReviewId());
            dto.setContent(review.getContent());
            dto.setRating(review.getRating());

            // 🌟 關鍵修正：從關聯的 User 物件中取出真正的名字 (getRealName)
            if (review.getUser() != null) {
                dto.setUsername(review.getUser().getRealName());
            } else {
                dto.setUsername("匿名讀者");
            }

            dto.setCreatedAt(review.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    // 新增評論（包含商業邏輯檢查）
    @Transactional
    public Review addReview(String email, Integer bookId, Integer rating, String content) {
        // 1. 查找用戶
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("找不到該用戶"));

        // 2. 查找書籍
        Book book = bookRepository.findById(Long.valueOf(bookId))
                .orElseThrow(() -> new RuntimeException("找不到該書籍"));

        // 3. 商業邏輯：檢查評分範圍
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("評分必須在 1 到 5 之間");
        }

        // 4. 建立實體並儲存
        Review review = Review.builder()
                .user(user)
                .book(book)
                .rating(rating)
                .content(content)
                .build();

        return reviewRepository.save(review);
    }
}
