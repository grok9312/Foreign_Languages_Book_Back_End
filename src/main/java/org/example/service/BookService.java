package org.example.service;

import org.example.dto.BookRequest;
import org.example.entity.Book;
import org.example.entity.Language;
import org.example.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    private Language parseLanguage(String langString) {
        if (langString == null || langString.trim().isEmpty()) {
            throw new RuntimeException("語言分類不可為空");
        }
        try {
            return Language.valueOf(langString.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("無效的語言分類: " + langString);
        }
    }

    // --- 前台查詢邏輯 ---

    public List<Book> getOnsaleBooksByLang(String lang) {
        Language language = parseLanguage(lang);
        return bookRepository.findByLangAndIsOnsaleTrue(language);
    }

    public List<Book> searchOnsaleBooks(String keyword) {
        return bookRepository.searchBooks(keyword);
    }

    public Optional<Book> getOnsaleBookById(Long id) {
        Optional<Book> book = bookRepository.findById(id);
        if (book.isPresent() && book.get().getIsOnsale()) {
            return book;
        }
        return Optional.empty();
    }

    // --- 後台管理邏輯 ---

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @Transactional
    public Book createBook(BookRequest request) {
        // 標題驗證
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("書名不可為空");
        }
        // 價格驗證
        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("價格不可為負數");
        }
        if (bookRepository.existsByIsbn(request.getIsbn())) {
            throw new RuntimeException("ISBN 國際標準書號已存在");
        }

        Language bookLang = parseLanguage(request.getLang());

        Book newBook = new Book();
        newBook.setTitle(request.getTitle());
        newBook.setAuthor(request.getAuthor());
        newBook.setIsbn(request.getIsbn());
        newBook.setPrice(request.getPrice());
        newBook.setStock(request.getStock());
        newBook.setDescription(request.getDescription());
        newBook.setImageUrl(request.getImageUrl());
        newBook.setLang(bookLang);

        newBook.setIsOnsale(request.getIsOnsale() != null ? request.getIsOnsale() : false);
        newBook.setPublishedDate(request.getPublishedDate() != null ? request.getPublishedDate() : LocalDate.now());

        return bookRepository.save(newBook);
    }

    @Transactional
    public Book updateBook(Long id, BookRequest request) {
        // 標題驗證
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("書名不可為空");
        }
        // 價格驗證
        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("價格不可為負數");
        }

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("書籍 ID: " + id + " 未找到"));

        Language bookLang = parseLanguage(request.getLang());

        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setPrice(request.getPrice());
        book.setStock(request.getStock());
        book.setDescription(request.getDescription());
        book.setLang(bookLang);
        book.setImageUrl(request.getImageUrl());
        book.setPublishedDate(request.getPublishedDate());
        book.setIsOnsale(request.getIsOnsale() != null ? request.getIsOnsale() : book.getIsOnsale());

        return bookRepository.save(book);
    }

    @Transactional
    public Book updateBookStatus(Long id, boolean onsale) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("書籍 ID: " + id + " 未找到"));

        book.setIsOnsale(onsale);
        return bookRepository.save(book);
    }
}
