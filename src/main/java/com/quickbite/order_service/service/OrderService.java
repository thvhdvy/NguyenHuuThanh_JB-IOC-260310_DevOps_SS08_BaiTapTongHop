package com.quickbite.order_service.service;

import com.quickbite.order_service.client.NotificationClient;
import com.quickbite.order_service.client.RestaurantClient;
import com.quickbite.order_service.client.RestaurantClient.MenuItemDto;
import com.quickbite.order_service.client.RestaurantClient.OrderDecision;
import com.quickbite.order_service.client.UserClient;
import com.quickbite.order_service.client.UserClient.PaymentResult;
import com.quickbite.order_service.client.UserClient.UserDto;
import com.quickbite.order_service.entity.Order;
import com.quickbite.order_service.entity.OrderItem;
import com.quickbite.order_service.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Điều phối vòng đời 6 bước của một đơn đặt món. */
@Service
@RequiredArgsConstructor
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	public record ItemRequest(Long menuItemId, int quantity) {
	}

	public record PlaceOrderRequest(Long userId, Long restaurantId, List<ItemRequest> items) {
	}

	private final OrderStateService state;
	private final UserClient users;
	private final RestaurantClient restaurants;
	private final NotificationClient notifications;

	public Order placeOrder(PlaceOrderRequest req) {
		validate(req);
		UserDto user = users.getUser(req.userId());
		if (!"CUSTOMER".equals(user.role())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CUSTOMER can place orders");
		}
		restaurants.getRestaurant(req.restaurantId());
		List<OrderItem> items = priceItems(req);
		BigDecimal total = items.stream().map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// Bước 2: tạo đơn PENDING + ghi lịch sử
		Order order = state.createPending(req.userId(), req.restaurantId(), items, total);
		notifyUser(order, "Đơn #" + order.getId() + " đã được tiếp nhận, đang chờ thanh toán");

		// Bước 3: thanh toán qua ví
		PaymentResult payment;
		try {
			payment = users.pay(req.userId(), total);
		} catch (RestClientException e) {
			log.error("Payment call failed for order {}", order.getId(), e);
			return fail(order, "Không gọi được user-service để thanh toán");
		}
		if (!payment.success()) {
			return fail(order, "Ví không đủ tiền");
		}
		order = state.changeStatus(order.getId(), OrderStatus.ACCEPTED, "Paid " + total);
		notifyUser(order, "Đơn #" + order.getId() + " đã thanh toán " + total + " và được chấp nhận");

		// Bước 4: báo nhà hàng chuẩn bị món
		OrderDecision decision;
		try {
			List<RestaurantClient.OrderLine> lines = req.items().stream()
					.map(i -> new RestaurantClient.OrderLine(i.menuItemId(), i.quantity())).toList();
			decision = restaurants.notifyOrder(req.restaurantId(),
					new RestaurantClient.OrderNotification(order.getId(), lines));
		} catch (RestClientException e) {
			log.error("Restaurant call failed for order {}", order.getId(), e);
			decision = new OrderDecision(false, "Không gọi được restaurant-service");
		}
		if (!decision.accepted()) {
			return compensate(order, decision.reason());
		}

		// Bước 5 (phần 1): tìm tài xế, báo cho các DRIVER đang có trong hệ thống
		dispatchToDrivers(order);
		return order;
	}

	/** Bước 5 (phần 2): tài xế bấm nhận đơn. */
	public Order pickUp(Long orderId, Long driverId) {
		UserDto driver = users.getUser(driverId);
		if (!"DRIVER".equals(driver.role())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User " + driverId + " is not a DRIVER");
		}
		Order order;
		try {
			order = state.assignDriver(orderId, driverId);
		} catch (OptimisticLockingFailureException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Order was taken by another driver");
		}
		notifyUser(order, "Đơn #" + orderId + " đang được tài xế " + driver.name() + " giao đến bạn");
		return order;
	}

	public Order deliver(Long orderId) {
		Order order = state.deliver(orderId);
		notifyUser(order, "Đơn #" + orderId + " đã giao thành công. Chúc bạn ngon miệng!");
		return order;
	}

	private Order fail(Order order, String reason) {
		Order failed = state.changeStatus(order.getId(), OrderStatus.FAILED, reason);
		notifyUser(failed, "Đơn #" + order.getId() + " thất bại: " + reason);
		return failed;
	}

	/** Giao dịch bù: hoàn tiền cho khách rồi huỷ đơn. */
	private Order compensate(Order order, String reason) {
		String note = reason;
		try {
			users.refund(order.getUserId(), order.getTotalAmount());
			note = reason + " - đã hoàn " + order.getTotalAmount();
		} catch (RestClientException e) {
			log.error("REFUND FAILED for order {} (user {}, amount {})", order.getId(), order.getUserId(),
					order.getTotalAmount(), e);
			note = reason + " - HOÀN TIỀN THẤT BẠI, cần xử lý thủ công";
		}
		Order cancelled = state.changeStatus(order.getId(), OrderStatus.CANCELLED, note);
		notifyUser(cancelled, "Đơn #" + order.getId() + " bị huỷ: " + note);
		return cancelled;
	}

	private void dispatchToDrivers(Order order) {
		try {
			List<UserDto> drivers = users.getDrivers();
			log.info("Order {} is waiting for a driver, notifying {} drivers", order.getId(), drivers.size());
			drivers.forEach(d -> notifications.send(d.id(), order.getId(), order.getStatus().name(),
					"Có đơn mới #" + order.getId() + " đang chờ tài xế nhận"));
		} catch (RestClientException e) {
			log.warn("Could not load drivers for order {}: {}", order.getId(), e.getMessage());
		}
	}

	private void notifyUser(Order order, String message) {
		notifications.send(order.getUserId(), order.getId(), order.getStatus().name(), message);
	}

	private List<OrderItem> priceItems(PlaceOrderRequest req) {
		Map<Long, MenuItemDto> menu = restaurants.getMenu(req.restaurantId()).stream()
				.collect(Collectors.toMap(MenuItemDto::id, Function.identity()));
		return req.items().stream().map(i -> {
			MenuItemDto m = menu.get(i.menuItemId());
			if (m == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Menu item " + i.menuItemId() + " does not belong to restaurant " + req.restaurantId());
			}
			return new OrderItem(m.id(), m.name(), m.price(), i.quantity());
		}).toList();
	}

	private void validate(PlaceOrderRequest req) {
		if (req.userId() == null || req.restaurantId() == null || req.items() == null || req.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId, restaurantId and items are required");
		}
		if (req.items().stream().anyMatch(i -> i.menuItemId() == null || i.quantity() <= 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item needs menuItemId and quantity > 0");
		}
	}
}
