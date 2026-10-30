package com.quickbite.order_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

	private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

	private record SendRequest(Long userId, Long orderId, String orderStatus, String message) {
	}

	private final RestClient http;

	public NotificationClient(@Value("${services.notification.url}") String baseUrl) {
		this.http = RestClient.create(baseUrl);
	}

	/** Bước 6: gửi thông báo. Lỗi thông báo không được làm hỏng luồng đặt đơn nên chỉ log. */
	public void send(Long userId, Long orderId, String orderStatus, String message) {
		try {
			http.post().uri("/api/v1/notifications").body(new SendRequest(userId, orderId, orderStatus, message)).retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			log.warn("Could not send notification for order {}: {}", orderId, e.getMessage());
		}
	}
}
