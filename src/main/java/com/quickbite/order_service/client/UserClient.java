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
public class UserClient {

	public record UserDto(Long id, String name, String email, String role) {
	}

	public record PaymentResult(boolean success, BigDecimal balance, String message) {
	}

	private record AmountRequest(BigDecimal amount) {
	}

	private final RestClient http;

	public UserClient(@Value("${services.user.url}") String baseUrl) {
		this.http = RestClient.create(baseUrl);
	}

	public UserDto getUser(Long id) {
		try {
			return http.get().uri("/api/v1/users/{id}", id).retrieve().body(UserDto.class);
		} catch (HttpClientErrorException.NotFound e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
		}
	}

	public List<UserDto> getDrivers() {
		return http.get().uri("/api/v1/users?role=DRIVER").retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	/** Bước 3: trừ tiền ví. Không đủ tiền thì success=false. */
	public PaymentResult pay(Long userId, BigDecimal amount) {
		return http.post().uri("/api/v1/users/{id}/wallet/pay", userId).body(new AmountRequest(amount)).retrieve()
				.body(PaymentResult.class);
	}

	/** Giao dịch bù ở bước 4: hoàn tiền. */
	public PaymentResult refund(Long userId, BigDecimal amount) {
		return http.post().uri("/api/v1/users/{id}/wallet/refund", userId).body(new AmountRequest(amount)).retrieve()
				.body(PaymentResult.class);
	}
}
