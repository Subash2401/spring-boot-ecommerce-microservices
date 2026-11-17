package com.subash.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.subash.orderservice.client.ResilientProductClient;
import com.subash.orderservice.dto.CreateOrderRequest;
import com.subash.orderservice.dto.OrderItemRequest;
import com.subash.orderservice.dto.OrderResponse;
import com.subash.orderservice.dto.ProductResponse;
import com.subash.orderservice.exception.InvalidOrderException;
import com.subash.orderservice.exception.OrderNotCancellableException;
import com.subash.orderservice.exception.OrderNotFoundException;
import com.subash.orderservice.exception.ProductNotFoundException;
import com.subash.orderservice.exception.ProductServiceUnavailableException;
import com.subash.orderservice.kafka.OrderEvent;
import com.subash.orderservice.model.CancellationReason;
import com.subash.orderservice.model.Order;
import com.subash.orderservice.model.OrderStatus;
import com.subash.orderservice.repository.OrderRepository;

// Unit tests for OrderService: no Spring, no database. Mockito replaces the repository,
// the Product Service client (ResilientProductClient, tested on its own) and the event publisher with fakes ("mocks"), so each test
// decides what they return and then checks what OrderService did with them.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	private static final Long USER_ID = 7L;

	@Mock
	private OrderRepository orderRepository;
	@Mock
	private ResilientProductClient productClient;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks // a real OrderService, built with the three mocks above
	private OrderService orderService;

	// ---- placeOrder ----------------------------------------------------------------------

	@Test
	void placeOrder_mergesDuplicateLines_andUsesThePriceFromProductService() {
		when(productClient.getProduct("p1")).thenReturn(product("p1", "Phone", "100.00"));
		when(productClient.getProduct("p2")).thenReturn(product("p2", "Cable", "50.50"));
		whenSavedGiveIdAndPendingStatus();

		// p1 is listed twice (2 + 1); the client sends no prices at all
		OrderResponse response = orderService.placeOrder(request(line("p1", 2), line("p1", 1), line("p2", 1)), USER_ID);

		// one call per product, even though p1 was listed twice
		verify(productClient, times(1)).getProduct("p1");
		// 3 x 100.00 + 1 x 50.50
		assertThat(response.getTotalAmount()).isEqualByComparingTo("350.50");
		assertThat(response.getItems()).hasSize(2);
		assertThat(response.getItems().get(0).getQuantity()).isEqualTo(3);
		assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);

		// the PENDING event (it goes to the outbox) carries the merged quantities
		OrderEvent event = publishedEvent();
		assertThat(event.getStatus()).isEqualTo("PENDING");
		assertThat(event.getUserId()).isEqualTo(USER_ID);
		assertThat(event.getItems()).hasSize(2);
	}

	@Test
	void placeOrder_moreThan100OfOneProductInTotal_isRejectedBeforeAnyCall() {
		// 60 + 50 = 110 of the same product, although each line alone is allowed
		assertThatThrownBy(() -> orderService.placeOrder(request(line("p1", 60), line("p1", 50)), USER_ID))
				.isInstanceOf(InvalidOrderException.class);

		verifyNoInteractions(productClient, orderRepository, eventPublisher);
	}

	@Test
	void placeOrder_unknownProduct_stopsTheOrder() { // ProductNotFoundException -> 400
		when(productClient.getProduct("nope")).thenThrow(new ProductNotFoundException("nope"));

		assertThatThrownBy(() -> orderService.placeOrder(request(line("nope", 1)), USER_ID))
				.isInstanceOf(ProductNotFoundException.class);
		verify(orderRepository, never()).save(any());
	}

	@Test
	void placeOrder_productServiceUnavailable_stopsTheOrder() { // -> 503
		when(productClient.getProduct("p1")).thenThrow(new ProductServiceUnavailableException());

		assertThatThrownBy(() -> orderService.placeOrder(request(line("p1", 1)), USER_ID))
				.isInstanceOf(ProductServiceUnavailableException.class);
		verify(orderRepository, never()).save(any());
	}

	// ---- cancelOrder ---------------------------------------------------------------------

	@Test
	void cancelOrder_awaitingPayment_isCancelledByTheUser_andAnEventIsPublished() {
		Order order = existingOrder(1L, USER_ID, OrderStatus.AWAITING_PAYMENT);
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
		when(orderRepository.save(order)).thenReturn(order);

		OrderResponse response = orderService.cancelOrder(1L, USER_ID, false);

		assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(response.getCancellationReason()).isEqualTo(CancellationReason.USER_CANCELLED);
		assertThat(publishedEvent().getStatus()).isEqualTo("CANCELLED");
	}

	@Test
	void cancelOrder_confirmed_isRefused() { // 409: a paid order cannot be cancelled by the user
		when(orderRepository.findById(1L)).thenReturn(Optional.of(existingOrder(1L, USER_ID, OrderStatus.CONFIRMED)));

		assertThatThrownBy(() -> orderService.cancelOrder(1L, USER_ID, false))
				.isInstanceOf(OrderNotCancellableException.class);
		verify(orderRepository, never()).save(any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void cancelOrder_someoneElsesOrder_looksLikeAMissingOrder() { // 404, so its existence is not revealed
		when(orderRepository.findById(1L)).thenReturn(Optional.of(existingOrder(1L, 99L, OrderStatus.PENDING)));

		assertThatThrownBy(() -> orderService.cancelOrder(1L, USER_ID, false))
				.isInstanceOf(OrderNotFoundException.class);
	}

	// ---- payment results (from Kafka) ----------------------------------------------------

	@Test
	void handlePaymentResult_success_confirmsTheOrder() {
		Order order = existingOrder(1L, USER_ID, OrderStatus.AWAITING_PAYMENT);
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

		orderService.handlePaymentResult(1L, "SUCCESS");

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
		verify(orderRepository).save(order);
	}

	@Test
	void handlePaymentResult_expired_cancelsTheOrder_andPublishesCancelled() {
		Order order = existingOrder(1L, USER_ID, OrderStatus.AWAITING_PAYMENT);
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
		when(orderRepository.save(order)).thenReturn(order);

		orderService.handlePaymentResult(1L, "EXPIRED");

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
		// Product needs this event to give the held stock back
		assertThat(publishedEvent().getCancellationReason()).isEqualTo("PAYMENT_TIMEOUT");
	}

	@Test
	void handlePaymentResult_duplicateMessage_isIgnored() { // Kafka delivers at least once
		Order order = existingOrder(1L, USER_ID, OrderStatus.CONFIRMED);
		when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

		orderService.handlePaymentResult(1L, "SUCCESS");

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
		verify(orderRepository, never()).save(any());
	}

	// ---- helpers -------------------------------------------------------------------------

	private static CreateOrderRequest request(OrderItemRequest... lines) {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setItems(List.of(lines));
		return request;
	}

	private static OrderItemRequest line(String productId, int quantity) {
		OrderItemRequest line = new OrderItemRequest();
		line.setProductId(productId);
		line.setQuantity(quantity);
		return line;
	}

	private static ProductResponse product(String id, String name, String price) {
		ProductResponse product = new ProductResponse();
		product.setId(id);
		product.setName(name);
		product.setPrice(new BigDecimal(price));
		product.setQuantity(10);
		return product;
	}

	private static Order existingOrder(Long id, Long userId, OrderStatus status) {
		Order order = new Order();
		order.setId(id);
		order.setUserId(userId);
		order.setStatus(status);
		order.setTotalAmount(new BigDecimal("100.00"));
		order.setItems(new ArrayList<>());
		return order;
	}

	// A real save sets the id and (in @PrePersist) the PENDING status; the mock does the same
	private void whenSavedGiveIdAndPendingStatus() {
		when(orderRepository.save(any(Order.class))).thenAnswer(call -> {
			Order order = call.getArgument(0);
			order.setId(1L);
			order.setStatus(OrderStatus.PENDING);
			return order;
		});
	}

	// The OrderEvent that OrderService handed to the event publisher
	private OrderEvent publishedEvent() {
		ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		return captor.getValue();
	}
}
