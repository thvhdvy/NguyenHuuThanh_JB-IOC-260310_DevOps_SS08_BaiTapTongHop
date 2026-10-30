package com.quickbite.order_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long orderId;

	/** null khi đơn mới được tạo. */
	@Enumerated(EnumType.STRING)
	private OrderStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus toStatus;

	private String note;

	@Column(nullable = false)
	private Instant changedAt;
}
