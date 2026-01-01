package org.example.service;

import org.example.dto.CheckoutRequest;
import org.example.dto.CheckoutResponseDTO;
import org.example.entity.*;
import org.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private User mockUser;
    private Book mockBook;
    private CartItem mockCartItem;

    @BeforeEach
    void setUp() {
        // 初始化共用的測試數據
        mockUser = new User();
        mockUser.setUserId(1L);
        mockUser.setUsername("testuser");

        mockBook = new Book();
        mockBook.setBookId(101L);
        mockBook.setTitle("測試書籍");
        mockBook.setPrice(new BigDecimal("100.00"));
        mockBook.setStock(10); // 初始庫存 10
        mockBook.setIsOnsale(true);

        mockCartItem = new CartItem();
        mockCartItem.setCartItemId(50L);
        mockCartItem.setUser(mockUser);
        mockCartItem.setBook(mockBook);
        mockCartItem.setQuantity(2); // 購買 2 本
    }

    // ==========================================
    // 測試 checkout (結帳)
    // ==========================================

    @Test
    @DisplayName("結帳成功：應建立訂單、扣除庫存並清空購物車")
    void testCheckout_Success() {
        // Arrange (準備數據)
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod("CREDIT_CARD");
        req.setRecipientName("Test Receiver");
        req.setShippingAddress("Test Address");
        req.setRecipientPhone("0912345678");

        List<CartItem> cartItems = new ArrayList<>();
        cartItems.add(mockCartItem);

        // 模擬 Repository 行為
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(cartItemRepository.findByUserUserId(1L)).thenReturn(cartItems);
        // 模擬存檔後回傳帶有 ID 的 Order
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(999L);
            return order;
        });

        // Act (執行測試)
        CheckoutResponseDTO response = orderService.checkout(1L, req);

        // Assert (驗證結果)
        assertNotNull(response);
        assertEquals(999L, response.getOrderId());
        assertEquals("結帳成功！訂單 ID: 999", response.getMessage());

        // 驗證庫存是否減少 (10 - 2 = 8)
        assertEquals(8, mockBook.getStock());
        verify(bookRepository, times(1)).save(mockBook);

        // 驗證購物車是否被清空
        verify(cartItemRepository, times(1)).deleteAll(cartItems);
    }

    @Test
    @DisplayName("結帳失敗：庫存不足應拋出異常")
    void testCheckout_InsufficientStock() {
        // Arrange
        mockBook.setStock(1); // 庫存只有 1
        mockCartItem.setQuantity(2); // 想買 2

        List<CartItem> cartItems = Collections.singletonList(mockCartItem);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(cartItemRepository.findByUserUserId(1L)).thenReturn(cartItems);

        // 準備一個有效的 CheckoutRequest，包含必要的 PaymentMethod
        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod("CREDIT_CARD");

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.checkout(1L, req);
        });

        assertTrue(exception.getMessage().contains("庫存不足"));

        // 確保沒有進行存檔操作
        verify(orderRepository, never()).save(any(Order.class));
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("結帳失敗：購物車為空應拋出異常")
    void testCheckout_EmptyCart() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(cartItemRepository.findByUserUserId(1L)).thenReturn(Collections.emptyList());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.checkout(1L, new CheckoutRequest());
        });

        assertEquals("購物車是空的，無法結帳", exception.getMessage());
    }

    // ==========================================
    // 測試 updateOrderStatus (更新訂單狀態)
    // ==========================================

    @Test
    @DisplayName("更新狀態成功：一般狀態變更")
    void testUpdateOrderStatus_NormalChange() {
        // Arrange
        Long orderId = 999L;
        Order mockOrder = new Order();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        // Act
        Order updatedOrder = orderService.updateOrderStatus(orderId, "SHIPPED");

        // Assert
        assertEquals(OrderStatus.SHIPPED, updatedOrder.getStatus());
        verify(orderRepository, times(1)).save(mockOrder);
    }

    @Test
    @DisplayName("取消訂單：應回補庫存")
    void testUpdateOrderStatus_CancelAndRestoreStock() {
        // Arrange
        Long orderId = 999L;
        Order mockOrder = new Order();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus(OrderStatus.PENDING); // 尚未付款，可以取消

        // 模擬訂單內的商品
        OrderItem orderItem = new OrderItem();
        orderItem.setBook(mockBook); // mockBook 初始庫存是 10
        orderItem.setQuantity(3);    // 訂單買了 3 本
        mockOrder.setItems(Collections.singletonList(orderItem));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        // Act
        orderService.updateOrderStatus(orderId, "CANCELLED");

        // Assert
        assertEquals(OrderStatus.CANCELLED, mockOrder.getStatus());

        // 驗證庫存是否回補 (10 + 3 = 13)
        assertEquals(13, mockBook.getStock());
        verify(bookRepository, times(1)).save(mockBook);
    }

    @Test
    @DisplayName("取消訂單：多樣商品庫存皆應回補")
    void testUpdateOrderStatus_Cancel_RestoreMultipleItems() {
        // Arrange
        Long orderId = 999L;
        Order mockOrder = new Order();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus(OrderStatus.PENDING);

        // Book 1 (使用 setUp 建立的 mockBook)
        OrderItem item1 = new OrderItem();
        item1.setBook(mockBook); // stock 10
        item1.setQuantity(2);

        // Book 2 (額外建立)
        Book mockBook2 = new Book();
        mockBook2.setBookId(102L);
        mockBook2.setTitle("第二本書");
        mockBook2.setStock(5);

        OrderItem item2 = new OrderItem();
        item2.setBook(mockBook2);
        item2.setQuantity(1);

        mockOrder.setItems(List.of(item1, item2));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        // Act
        orderService.updateOrderStatus(orderId, "CANCELLED");

        // Assert
        // Book 1: 10 + 2 = 12
        assertEquals(12, mockBook.getStock());
        // Book 2: 5 + 1 = 6
        assertEquals(6, mockBook2.getStock());

        verify(bookRepository).save(mockBook);
        verify(bookRepository).save(mockBook2);
    }

    @Test
    @DisplayName("重複取消：已取消的訂單再次取消，不應重複回補庫存")
    void testUpdateOrderStatus_DuplicateCancellation_NoStockRestore() {
        // Arrange
        Long orderId = 999L;
        Order mockOrder = new Order();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus(OrderStatus.CANCELLED); // 已經是取消狀態

        OrderItem orderItem = new OrderItem();
        orderItem.setBook(mockBook);
        orderItem.setQuantity(3);
        mockOrder.setItems(Collections.singletonList(orderItem));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        // Act
        orderService.updateOrderStatus(orderId, "CANCELLED");

        // Assert
        assertEquals(OrderStatus.CANCELLED, mockOrder.getStatus());
        // 驗證庫存沒有被保存 (沒有呼叫 save)，表示沒有執行回補
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("取消訂單失敗：已付款訂單不可直接取消")
    void testUpdateOrderStatus_FailIfPaid() {
        // Arrange
        Long orderId = 999L;
        Order mockOrder = new Order();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus(OrderStatus.PAID); // 狀態為已付款

        mockOrder.setItems(new ArrayList<>());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.updateOrderStatus(orderId, "CANCELLED");
        });

        assertEquals("已付款訂單無法直接取消，請聯繫金流端處理退款。", exception.getMessage());
    }

    @Test
    @DisplayName("更新狀態失敗：訂單不存在")
    void testUpdateOrderStatus_OrderNotFound() {
        // Arrange
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            orderService.updateOrderStatus(999L, "SHIPPED");
        });
    }

    @Test
    @DisplayName("更新狀態失敗：無效的狀態字串")
    void testUpdateOrderStatus_InvalidStatusString() {
        // Arrange
        Long orderId = 999L;
        Order mockOrder = new Order();
        mockOrder.setOrderId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.updateOrderStatus(orderId, "INVALID_STATUS");
        });
    }
}