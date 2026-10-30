package com.quickbite.order_service.service;

import com.quickbite.order_service.entity.Order;
import com.quickbite.order_service.entity.OrderItem;
import com.quickbite.order_service.entity.OrderStatus;
import com.quickbite.order_service.entity.OrderStatusHistory;
import com.quickbite.order_service.repository.OrderRepository;
import com.quickbite.order_service.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Mọi thay đổi DB của đơn hàng nằm ở đây, mỗi method là một transaction ngắn
 * để không giữ transaction trong lúc gọi HTTP sang service khác.
 */
@Service
@RequiredArgsConstructor
public class OrderStateService {

	private final OrderRepository orders;
	private final OrderStatusHistoryRepository history;

	@Transactional
	public Order createPending(Long userId, Long restaurantId, List<OrderItem> items, BigDecimal total) {
		Order order = new Order();
		order.setUserId(userId);
		order.setRestaurantId(restaurantId);
		order.setItems(items);
		order.setTotalAmount(total);
		order.setStatus(OrderStatus.PENDING);
		order.setCreatedAt(Instant.now());
		order.setUpdatedAt(order.getCreatedAt());
		order = orders.save(order);
		history.save(new OrderStatusHistory(null, order.getId(), null, OrderStatus.PENDING, "Order created",
				order.getCreatedAt()));
		return order;
	}

	@Transactional
	public Order changeStatus(Long orderId, OrderStatus to, String note) {
		Order order = find(orderId);
		return apply(order, to, note);
	}

	/** Tài xế nhận đơn: chỉ hợp lệ khi đơn đang ACCEPTED và chưa có tài xế. */
	@Transactional
	public Order assignDriver(Long orderId, Long driverId) {
		Order order = find(orderId);
		if (order.getStatus() != OrderStatus.ACCEPTED || order.getDriverId() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Order " + orderId + " is not available for pickup (status=" + order.getStatus() + ")");
		}
		order.setDriverId(driverId);
		return apply(order, OrderStatus.SHIPPING, "Picked up by driver " + driverId);
	}

	@Transactional
	public Order deliver(Long orderId) {
		Order order = find(orderId);
		if (order.getStatus() != OrderStatus.SHIPPING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Order " + orderId + " is not being shipped (status=" + order.getStatus() + ")");
		}
		return apply(order, OrderStatus.DELIVERED, "Delivered");
	}

	@Transactional(readOnly = true)
	public Order find(Long orderId) {
		return orders.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
	}

	private Order apply(Order order, OrderStatus to, String note) {
		OrderStatus from = order.getStatus();
		Instant now = Instant.now();
		order.setStatus(to);
		order.setUpdatedAt(now);
		orders.save(order);
		history.save(new OrderStatusHistory(null, order.getId(), from, to, note, now));
		return order;
	}
}
