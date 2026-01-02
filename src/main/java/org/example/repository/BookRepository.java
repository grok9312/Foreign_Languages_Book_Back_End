package org.example.repository;

import org.example.entity.Book;
import org.example.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * 檢查 ISBN 是否已存在於資料庫中
     */
    boolean existsByIsbn(String isbn);

    /**
     * 根據語言且上架狀態為 true 查詢書籍
     * 修正：方法名應為 findByLangAndIsOnsaleTrue 以匹配 Book Entity 中的 isOnsale 欄位
     */
    List<Book> findByLangAndIsOnsaleTrue(Language lang);

    /**
     * 新增：根據關鍵字模糊查詢書籍標題或作者，且書籍必須是上架狀態
     * @param keyword 查詢關鍵字
     * @return 匹配的書籍列表
     */
    @Query("SELECT b FROM Book b WHERE b.isOnsale = true AND (LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Book> searchBooks(@Param("keyword") String keyword);
}
