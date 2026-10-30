package com.quickbite.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Component
public class RestaurantClient {

	public record RestaurantDto(Long id, String name, String address) {
	}

	public record MenuItemDto(Long id, Long restaurantId, String name, BigDecimal price, boolean available) {
	}

	public record OrderLine(Long menuItemId, int quantity) {
	}

	public record OrderNotification(Long orderId, List<OrderLine> items) {
	}

	public record OrderDecision(boolean accepted, String reason) {
	}

	private final RestClient http;

	public RestaurantClient(@Value("${services.restaurant.url}") String baseUrl) {
		this.http = RestClient.create(baseUrl);
	}

	public RestaurantDto getRestaurant(Long id) {
		try {
			return http.get().uri("/api/v1/restaurants/{id}", id).retrieve().body(RestaurantDto.class);
		} catch (HttpClientErrorException.NotFound e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found: " + id);
		}
	}

	public List<MenuItemDto> getMenu(Long restaurantId) {
		return http.get().uri("/api/v1/restaurants/{id}/menu", restaurantId).retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
	}

	/** Bước 4: báo nhà hàng chuẩn bị món. */
	public OrderDecision notifyOrder(Long restaurantId, OrderNotification notification) {
		return http.post().uri("/api/v1/restaurants/{id}/orders", restaurantId).body(notification).retrieve()
				.body(OrderDecision.class);
	}
}
