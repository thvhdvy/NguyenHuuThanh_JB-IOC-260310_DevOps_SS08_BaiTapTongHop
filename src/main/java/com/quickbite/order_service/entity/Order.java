package com.quickbite.order_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Chỉ lưu id, không có khoá ngoại vì user/restaurant nằm ở DB của service khác. */
	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long restaurantId;

	private Long driverId;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
	private List<OrderItem> items = new ArrayList<>();

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	/** Chặn hai tài xế cùng nhận một đơn. */
	@Version
	private Long version;
}
