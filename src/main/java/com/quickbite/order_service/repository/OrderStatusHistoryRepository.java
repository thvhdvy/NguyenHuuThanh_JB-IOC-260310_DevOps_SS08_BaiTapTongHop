package com.quickbite.order_service.repository;

import com.quickbite.order_service.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

	List<OrderStatusHistory> findByOrderIdOrderByIdAsc(Long orderId);
}
