package org.example.service;

import org.example.dto.CartItemRequest;
import org.example.entity.Book;
import org.example.entity.CartItem;
import org.example.entity.User;
import org.example.repository.BookRepository;
import org.example.repository.CartItemRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private User mockUser;
    private Book mockBook;
    private CartItem mockCartItem;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setUserId(1L);
        mockUser.setUsername("testuser");

        mockBook = new Book();
        mockBook.setBookId(101L);
        mockBook.setTitle("測試書籍");
        mockBook.setPrice(new BigDecimal("100.00"));
        mockBook.setStock(10); // 庫存 10

        mockCartItem = new CartItem();
        mockCartItem.setCartItemId(50L);
        mockCartItem.setUser(mockUser);
        mockCartItem.setBook(mockBook);
        mockCartItem.setQuantity(2);
    }

    // ==========================================
    // 測試 addOrUpdateCartItem (新增或更新購物車)
    // ==========================================

    @Test
    @DisplayName("新增商品：當購物車內無此商品時，應建立新紀錄")
    void testAddOrUpdateCartItem_NewItem() {
        // Arrange
        CartItemRequest req = new CartItemRequest();
        req.setBookId(101L);
        req.setQuantity(2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(101L)).thenReturn(Optional.of(mockBook));
        // 模擬購物車內尚未有此商品
        when(cartItemRepository.findByUserUserIdAndBookBookId(1L, 101L)).thenReturn(Optional.empty());
        
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem savedItem = invocation.getArgument(0);
            savedItem.setCartItemId(999L); // 模擬 DB 生成 ID
            return savedItem;
        });

        // Act
        CartItem result = cartService.addOrUpdateCartItem(1L, req);

        // Assert
        assertNotNull(result);
        assertEquals(999L, result.getCartItemId());
        assertEquals(2, result.getQuantity());
        assertEquals(mockBook, result.getBook());
        assertEquals(mockUser, result.getUser());

        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("更新商品：當購物車內已有此商品時，應更新數量")
    void testAddOrUpdateCartItem_UpdateExisting() {
        // Arrange
        CartItemRequest req = new CartItemRequest();
        req.setBookId(101L);
        req.setQuantity(5); // 更新數量為 5

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(101L)).thenReturn(Optional.of(mockBook));
        // 模擬購物車內已有此商品 (原本數量 2)
        when(cartItemRepository.findByUserUserIdAndBookBookId(1L, 101L)).thenReturn(Optional.of(mockCartItem));
        
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CartItem result = cartService.addOrUpdateCartItem(1L, req);

        // Assert
        assertEquals(5, result.getQuantity()); // 驗證數量已更新
        assertEquals(mockCartItem.getCartItemId(), result.getCartItemId()); // ID 應不變

        verify(cartItemRepository).save(mockCartItem);
    }

    @Test
    @DisplayName("加入購物車失敗：庫存不足")
    void testAddOrUpdateCartItem_InsufficientStock() {
        // Arrange
        mockBook.setStock(3); // 庫存只有 3

        CartItemRequest req = new CartItemRequest();
        req.setBookId(101L);
        req.setQuantity(5); // 想買 5

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(101L)).thenReturn(Optional.of(mockBook));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cartService.addOrUpdateCartItem(1L, req);
        });

        assertTrue(exception.getMessage().contains("庫存不足"));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("加入購物車失敗：數量不合法 (<= 0)")
    void testAddOrUpdateCartItem_InvalidQuantity() {
        // Arrange
        CartItemRequest req = new CartItemRequest();
        req.setBookId(101L);
        req.setQuantity(0); // 無效數量

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cartService.addOrUpdateCartItem(1L, req);
        });

        assertEquals("購買數量必須大於 0", exception.getMessage());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("加入購物車失敗：書籍不存在")
    void testAddOrUpdateCartItem_BookNotFound() {
        // Arrange
        CartItemRequest req = new CartItemRequest();
        req.setBookId(999L);
        req.setQuantity(1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cartService.addOrUpdateCartItem(1L, req);
        });

        assertEquals("書籍不存在", exception.getMessage());
    }

    // ==========================================
    // 測試 deleteCartItem (刪除購物車商品)
    // ==========================================

    @Test
    @DisplayName("刪除成功：屬於該用戶的購物車項目")
    void testDeleteCartItem_Success() {
        // Arrange
        Long userId = 1L;
        Long cartItemId = 50L;

        // 模擬該項目存在且屬於該用戶
        when(cartItemRepository.existsByCartItemIdAndUserUserId(cartItemId, userId)).thenReturn(true);

        // Act
        cartService.deleteCartItem(userId, cartItemId);

        // Assert
        verify(cartItemRepository, times(1)).deleteById(cartItemId);
    }

    @Test
    @DisplayName("刪除失敗：試圖刪除不屬於自己的項目 (越權存取)")
    void testDeleteCartItem_Unauthorized() {
        // Arrange
        Long userId = 1L;
        Long otherUserCartItemId = 60L;

        // 模擬該項目不存在或是屬於別人 (exists 回傳 false)
        when(cartItemRepository.existsByCartItemIdAndUserUserId(otherUserCartItemId, userId)).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cartService.deleteCartItem(userId, otherUserCartItemId);
        });

        assertEquals("購物車明細不存在或不屬於當前會員", exception.getMessage());
        verify(cartItemRepository, never()).deleteById(anyLong());
    }
}
