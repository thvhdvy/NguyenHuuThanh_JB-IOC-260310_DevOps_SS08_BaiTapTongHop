package com.quickbite.order_service.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

	private Long menuItemId;

	private String name;

	private BigDecimal price;

	private int quantity;
}
