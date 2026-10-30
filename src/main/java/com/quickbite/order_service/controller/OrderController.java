package com.quickbite.order_service.controller;

import com.quickbite.order_service.entity.Order;
import com.quickbite.order_service.entity.OrderStatus;
import com.quickbite.order_service.entity.OrderStatusHistory;
import com.quickbite.order_service.repository.OrderRepository;
import com.quickbite.order_service.repository.OrderStatusHistoryRepository;
import com.quickbite.order_service.service.OrderService;
import com.quickbite.order_service.service.OrderService.PlaceOrderRequest;
import com.quickbite.order_service.service.OrderStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	public record PickUpRequest(Long driverId) {
	}

	private final OrderService orderService;
	private final OrderStateService state;
	private final OrderRepository orders;
	private final OrderStatusHistoryRepository history;

	/** Bước 1-4: đặt đơn. Kết quả cuối (ACCEPTED / FAILED / CANCELLED) nằm trong field status. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Order place(@RequestBody PlaceOrderRequest req) {
		return orderService.placeOrder(req);
	}

	@GetMapping
	public List<Order> list() {
		return orders.findAll();
	}

	/** Đơn đã được nhà hàng nhận, đang chờ tài xế. */
	@GetMapping("/awaiting-driver")
	public List<Order> awaitingDriver() {
		return orders.findByStatusAndDriverIdIsNull(OrderStatus.ACCEPTED);
	}

	@GetMapping("/{id}")
	public Order get(@PathVariable Long id) {
		return state.find(id);
	}

	@GetMapping("/{id}/history")
	public List<OrderStatusHistory> history(@PathVariable Long id) {
		state.find(id);
		return history.findByOrderIdOrderByIdAsc(id);
	}

	/** Bước 5: tài xế nhận đơn -> SHIPPING. */
	@PostMapping("/{id}/pickup")
	public Order pickUp(@PathVariable Long id, @RequestBody PickUpRequest req) {
		return orderService.pickUp(id, req.driverId());
	}

	/** Giao xong -> DELIVERED. */
	@PostMapping("/{id}/deliver")
	public Order deliver(@PathVariable Long id) {
		return orderService.deliver(id);
	}
}
