package org.example.controller;

import org.example.entity.Book;
import org.example.service.BookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/public/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // GET /api/public/books/lang/{lang}
    @GetMapping("/lang/{lang}")
    public ResponseEntity<List<Book>> getBooksByLang(@PathVariable String lang) {
        List<Book> books = bookService.getOnsaleBooksByLang(lang);
        return ResponseEntity.ok(books);
    }

    // GET /api/public/books/search?keyword={keyword}
    @GetMapping("/search")
    public ResponseEntity<List<Book>> searchBooks(@RequestParam String keyword) {
        List<Book> books = bookService.searchOnsaleBooks(keyword);
        return ResponseEntity.ok(books);
    }

    // GET /api/public/books/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        return bookService.getOnsaleBookById(id)
                // 修正：使用 Lambda 表達式避免方法引用歧義
                .map(book -> ResponseEntity.ok(book))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
