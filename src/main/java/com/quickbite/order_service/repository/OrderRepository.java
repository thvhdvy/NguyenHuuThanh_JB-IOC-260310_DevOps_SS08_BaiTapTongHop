package com.quickbite.order_service.repository;

import com.quickbite.order_service.entity.Order;
import com.quickbite.order_service.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findByStatusAndDriverIdIsNull(OrderStatus status);
}
