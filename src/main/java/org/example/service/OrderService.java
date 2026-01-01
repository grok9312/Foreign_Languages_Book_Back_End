package org.example.service;

import org.example.dto.*;
import org.example.entity.*;
import org.example.exception.OrderNotFoundException;
import org.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, CartItemRepository cartItemRepository,
                        BookRepository bookRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    // --- 核心業務：結帳流程 ---

    /**
     * 執行結帳流程：將購物車轉換為訂單，並扣除庫存。
     * @param userId 當前會員 ID
     * @param req 結帳請求資訊
     * @return 創建的 Order 實體
     */
    @Transactional // 確保訂單創建和庫存扣除是原子操作
    public CheckoutResponseDTO checkout(Long userId, CheckoutRequest req) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("會員不存在"));

        List<CartItem> cartItems = cartItemRepository.findByUserUserId(userId);
        if (cartItems.isEmpty()) {
            throw new RuntimeException("購物車是空的，無法結帳");
        }

        Order order = new Order();
        order.setUser(user);
        order.setPaymentMethod(PaymentMethod.valueOf(req.getPaymentMethod()));
        order.setStatus(OrderStatus.PENDING); // 預設訂單狀態為 pending
        // 假設 CheckoutRequest 內有 getRecipientName() 和 getShippingAddress() 方法
        order.setRecipientName(req.getRecipientName());
        order.setShippingAddress(req.getShippingAddress());
        order.setRecipientPhone(req.getRecipientPhone());
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        // 1. 處理購物車明細，檢查庫存，並創建 OrderItem
        for (CartItem cartItem : cartItems) {
            Book book = cartItem.getBook();
            if (book == null) {
                // 如果購物車中引用了不存在的書籍，拋出清晰的錯誤
                System.err.println("!!! 追蹤: 購物車中存在無效的 Book ID，CartItem ID: " + cartItem.getCartItemId());
                throw new RuntimeException("購物車中存在無效商品，請移除後重試。");
            }
            Integer quantity = cartItem.getQuantity();

            // 庫存檢查 (雙重檢查，確保在交易內最新)
            if (!book.getIsOnsale() || quantity > book.getStock()) {
                throw new RuntimeException(
                        book.getTitle() + " 庫存不足或已下架，無法結帳。庫存: " + book.getStock());
            }

            // 2. 創建 OrderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setBook(book);
            orderItem.setQuantity(quantity);
            orderItem.setPrice(book.getPrice()); // 記錄結帳時的價格

            BigDecimal subtotal = book.getPrice().multiply(new BigDecimal(quantity));
            orderItem.setSubtotal(subtotal);

            orderItems.add(orderItem);
            total = total.add(subtotal);

            // 3. 扣除庫存並保存 Book (核心步驟)
            book.setStock(book.getStock() - quantity);
            bookRepository.save(book);
        }

        // 4. 設置訂單總價並保存 Order
        order.setTotalPrice(total);
        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        // 5. 清空購物車 (結帳成功後)
        cartItemRepository.deleteAll(cartItems);

        // 🎯 核心修正 2: 創建並返回 DTO
        CheckoutResponseDTO response = new CheckoutResponseDTO();
        response.setOrderId(savedOrder.getOrderId());
        response.setMessage("結帳成功！訂單 ID: " + savedOrder.getOrderId());

        return response;
    }

    // --- 會員前台訂單查詢 ---

    /**
     * 會員查詢自己的所有訂單
     */

    public List<OrderListDTO> getOrdersByUserId(Long userId) {
        List<Order> orders = orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId);

        // 🎯 核心修正：在這裡將實體列表轉換為 DTO 列表
        return orders.stream()
                .map(this::mapToOrderListDTO)
                .collect(Collectors.toList());
    }

    private OrderListDTO mapToOrderListDTO(Order order) {
        OrderListDTO dto = new OrderListDTO();
        dto.setOrderId(order.getOrderId());
        dto.setStatus(order.getStatus().name());
        dto.setTotalPrice(order.getTotalPrice());
        dto.setCreatedAt(order.getCreatedAt());
        // 🌟 補上這一行，否則後台列表永遠拿不到付款方式！
        dto.setPaymentMethod(order.getPaymentMethod().name());
        // 🌟 加上這個防護：如果付款方式是空，就給一個預設值，避免 500 錯誤
        if (order.getPaymentMethod() != null) {
            dto.setPaymentMethod(order.getPaymentMethod().name());
        } else {
            dto.setPaymentMethod("UNKNOWN"); // 或者 "CASH_ON_DELIVERY"
        }
        return dto;
    }
    /**
     * 會員查詢單筆訂單詳情 (需確認所有權)
     */
    // --- 管理員後台訂單管理 ---

    /**
     * 管理員查詢所有訂單
     */
    public List<OrderListDTO> getAllOrders() {
        // 雖然 orderRepository.findAll() 會執行 N+1 查詢，但在 Service 層轉換 DTO 仍然是解決序列化問題的關鍵。
        List<Order> orders = orderRepository.findAll();

        // 🎯 修正 2: 執行 Order 實體到 OrderListDTO 的轉換
        return orders.stream()
                // 使用您已定義的列表 DTO 轉換方法
                .map(this::mapToOrderListDTO)
                .collect(Collectors.toList());
    }

    /**
     * 管理員更新訂單狀態
     */
    @Transactional
    public Order updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("訂單 ID: " + orderId + " 未找到"));

        OrderStatus nextStatus = OrderStatus.valueOf(newStatus);

        // 原有的防護邏輯 (修正：必須在 setStatus 之前檢查)
        if ("CANCELLED".equalsIgnoreCase(newStatus) && "PAID".equalsIgnoreCase(order.getStatus().name())) {
            // 注意：這裡如果拋出異常，上面的庫存回補會因為 @Transactional 而回滾(Rollback)，是安全的
            throw new RuntimeException("已付款訂單無法直接取消，請聯繫金流端處理退款。");
        }

        // 🌟 核心邏輯：如果新狀態是 CANCELLED，且舊狀態不是 CANCELLED
        if (nextStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            restoreStock(order);
        }

        order.setStatus(nextStatus);

        return orderRepository.save(order);
    }
    /**
     * 獲取單筆訂單詳情（會員前台使用，需驗證用戶ID）
     */
    public OrderDetailDTO getOrderDetailByIdAndUserId(Long orderId, Long currentUserId) {
        // 使用 Repository 中帶 JOIN FETCH 的方法
        Order order = orderRepository.findByIdAndUserIdWithDetails(orderId, currentUserId)
                .orElseThrow(() -> new OrderNotFoundException("訂單不存在或您無權限查看此訂單。"));

        // 將實體轉換為 DTO
        return mapToDetailDTO(order);
    }

    /**
     * 將 Order 實體轉換為 OrderDetailDTO
     */
    private OrderDetailDTO mapToDetailDTO(Order order) {
        List<OrderItemDTO> itemDTOs = order.getItems().stream()
                .map(this::mapOrderItemToDTO)
                .collect(Collectors.toList());

        return OrderDetailDTO.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalPrice(order.getTotalPrice())
                // 🌟 核心檢查點：這裡必須確保有拿到資料
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "CASH_ON_DELIVERY")
                .createdAt(order.getCreatedAt())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .shippingAddress(order.getShippingAddress())
                .items(itemDTOs)
                .build();
    }

    /**
     * 將 OrderItem 實體轉換為 OrderItemDTO
     */
    private OrderItemDTO mapOrderItemToDTO(OrderItem item) {
        return OrderItemDTO.builder()
                .orderItemId(item.getOrderItemId())
                .quantity(item.getQuantity())
                .price(item.getPrice()) // 🎯 修正：使用 price 匹配前端模板
                .subtotal(item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                // 🎯 這是解決商品名稱缺失的關鍵
                .bookId(item.getBook().getBookId())
                .bookTitle(item.getBook().getTitle())
                .build();
    }
    // src/main/java/org/example/service/OrderService.java

// ... 其他方法 ...

    /**
     * 管理員更新訂單狀態並返回詳情 DTO
     * 🎯 新方法：供 Admin Controller 調用
     */
    @Transactional
    public OrderDetailDTO updateOrderStatusAndGetDetail(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("訂單 ID: " + orderId + " 未找到"));

        // 🌟 修正點 1: 加上 .toUpperCase() 並處理空格，防止前端小寫造成的 400 錯誤
        OrderStatus nextStatus;
        try {
            nextStatus = OrderStatus.valueOf(newStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("不支援的訂單狀態: " + newStatus);
        }

        // 🌟 修正點 3: 調整取消限制 (如果你希望管理員擁有最高權限強行取消，請移除或註解掉這段)
        // 修正：必須在 setStatus 之前檢查
        if (nextStatus == OrderStatus.CANCELLED && order.getStatus() == OrderStatus.PAID) {
            // 如果是期末專案為了方便演示，建議把這個限制拿掉，或者讓管理員可以取消
             // throw new RuntimeException("已付款訂單無法直接取消，請聯繫金流端處理退款。");
        }

        // 🌟 修正點 2: 庫存回補邏輯
        if (nextStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            restoreStock(order);
        }

        order.setStatus(nextStatus);
        orderRepository.save(order);

        return mapToDetailDTO(order);
    }

    /**
     * 💡 新增私有輔助方法：統一處理庫存回補
     */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Book book = item.getBook();
            if (book != null) {
                int updatedStock = book.getStock() + item.getQuantity();
                book.setStock(updatedStock);
                bookRepository.save(book);
                System.out.println("成功回補庫存 - 書籍: " + book.getTitle() + ", 加回數量: " + item.getQuantity());
            }
        }
    }

    /**
     * 🎯 管理員專用：根據訂單 ID 獲取詳情 (不限用戶)
     */
    public OrderDetailDTO getOrderDetailByOrderIdOnly(Long orderId) {
        // 建議使用帶有 Fetch Join 的 Repository 方法以優化效能
        // 如果沒有自定義方法，暫時使用 findById
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("找不到訂單：" + orderId));

        // 🌟 修正點：將 convertToDetailDTO 改為 mapToDetailDTO
        return mapToDetailDTO(order);
    }

    /**
     * (可選擇性保留此方法供其他內部 Service 調用，如果其他地方需要返回 Order 實體)
     * @deprecated 避免在 Controller 中直接調用
     */
}
