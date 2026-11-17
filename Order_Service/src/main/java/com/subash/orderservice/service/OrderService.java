package com.subash.orderservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.subash.orderservice.client.ResilientProductClient;
import com.subash.orderservice.dto.CreateOrderRequest;
import com.subash.orderservice.dto.OrderItemRequest;
import com.subash.orderservice.dto.OrderItemResponse;
import com.subash.orderservice.dto.OrderResponse;
import com.subash.orderservice.dto.Paging;
import com.subash.orderservice.dto.ProductResponse;
import com.subash.orderservice.exception.InvalidOrderException;
import com.subash.orderservice.exception.OrderNotCancellableException;
import com.subash.orderservice.exception.OrderNotFoundException;
import com.subash.orderservice.kafka.OrderEvent;
import com.subash.orderservice.kafka.OrderEventItem;
import com.subash.orderservice.model.CancellationReason;
import com.subash.orderservice.model.Order;
import com.subash.orderservice.model.OrderItem;
import com.subash.orderservice.model.OrderStatus;
import com.subash.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

	private final OrderRepository orderRepository;
	// Product Service with retry + circuit breaker; throws ProductNotFoundException (400) or
	// ProductServiceUnavailableException (503)
	private final ResilientProductClient productClient;
	private final ApplicationEventPublisher eventPublisher;

	// Same limit as @Max on OrderItemRequest.quantity, applied after duplicates are merged
	private static final int MAX_QUANTITY_PER_PRODUCT = 100;

	@Transactional
	public OrderResponse placeOrder(CreateOrderRequest request, Long userId) {
		// Same product listed twice -> one line with the summed quantity, so Product is called once per product
		Map<String, Integer> quantities = new LinkedHashMap<>();
		for (OrderItemRequest itemRequest : request.getItems()) {
			quantities.merge(itemRequest.getProductId(), itemRequest.getQuantity(), Integer::sum);
		}
		quantities.forEach((productId, quantity) -> {
			if (quantity > MAX_QUANTITY_PER_PRODUCT) {
				throw new InvalidOrderException("Quantity for product " + productId + " cannot be more than "
						+ MAX_QUANTITY_PER_PRODUCT + " in total");
			}
		});

		List<OrderItem> items = new ArrayList<>();
		quantities.forEach((productId, quantity) -> {
			ProductResponse product = productClient.getProduct(productId);

			OrderItem item = new OrderItem();
			item.setProductId(product.getId());
			item.setProductName(product.getName());
			item.setQuantity(quantity);
			item.setPrice(product.getPrice());
			items.add(item);
		});

		BigDecimal total = items.stream().map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Order order = new Order();
		order.setUserId(userId);
		order.setItems(items);
		order.setTotalAmount(total);
		items.forEach(i -> i.setOrder(order));

		Order saved = orderRepository.save(order);
		eventPublisher.publishEvent(toEvent(saved));

		log.info("Order placed successfully. OrderId: {}", saved.getId());

		return mapToResponse(saved);
	}

	// Read-only transaction: mapToResponse reads the lazy items list, which needs an open session
	// now that spring.jpa.open-in-view is false
	@Transactional(readOnly = true)
	public OrderResponse getOrderById(Long id, Long userId, boolean isAdmin) {
		Order order = findOrderForUser(id, userId, isAdmin);
		return mapToResponse(order);
	}

	// Newest first; id breaks ties so the order is stable across pages
	@Transactional(readOnly = true)
	public Page<OrderResponse> getOrdersByUser(Long userId, Pageable pageable) {
		Pageable page = Paging.of(pageable, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		return orderRepository.findByUserId(userId, page).map(this::mapToResponse);
	}

	@Transactional
	public OrderResponse cancelOrder(Long id, Long userId, boolean isAdmin) {
		Order order = findOrderForUser(id, userId, isAdmin);

		if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
			throw new OrderNotCancellableException(id, order.getStatus());
		}
		order.setStatus(OrderStatus.CANCELLED);
		order.setCancellationReason(CancellationReason.USER_CANCELLED);
		Order saved = orderRepository.save(order);
		eventPublisher.publishEvent(toEvent(saved));
		return mapToResponse(saved);
	}

	@Transactional
	public void handleInventoryResult(Long orderId, String inventoryStatus) {
		Order order = orderRepository.findById(orderId).orElse(null);
		if (order == null || order.getStatus() != OrderStatus.PENDING) {
			log.info("Order {} not pending, ignoring inventory result", orderId);
			return;
		}
		if ("RESERVED".equals(inventoryStatus)) {
			order.setStatus(OrderStatus.AWAITING_PAYMENT);
		} else if ("REJECTED".equals(inventoryStatus)) {
			order.setStatus(OrderStatus.CANCELLED);
			order.setCancellationReason(CancellationReason.OUT_OF_STOCK);
		} else {
			return;
		}
		orderRepository.save(order);
		log.info("Order {} updated to {}", orderId, order.getStatus());
	}

	@Transactional
	public void handlePaymentResult(Long orderId, String paymentStatus) {
		Order order = orderRepository.findById(orderId).orElse(null);
		if (order == null) {
			log.warn("Order {} not found, skipping payment result", orderId);
			return;
		}

		if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
			if ("SUCCESS".equals(paymentStatus) && order.getStatus() == OrderStatus.CANCELLED) {
				// Payment refunds it: it gets this order's CANCELLED event and marks the payment REFUNDED
				log.warn("Order {} was already cancelled when its payment succeeded; Payment will refund it", orderId);
			} else {
				log.info("Order {} is already {}, ignoring payment result {}", orderId, order.getStatus(), paymentStatus);
			}
			return;
		}

		if ("SUCCESS".equals(paymentStatus)) {
			order.setStatus(OrderStatus.CONFIRMED);
		} else if ("FAILED".equals(paymentStatus)) {
			order.setStatus(OrderStatus.CANCELLED);
			order.setCancellationReason(CancellationReason.PAYMENT_FAILED);
		} else if ("EXPIRED".equals(paymentStatus)) {
			// Payment owns the payment deadline; Order only follows it. The CANCELLED event
			// (through the outbox) makes Product release the stock and Notification tell the user.
			order.setStatus(OrderStatus.CANCELLED);
			order.setCancellationReason(CancellationReason.PAYMENT_TIMEOUT);
			Order saved = orderRepository.save(order);
			eventPublisher.publishEvent(toEvent(saved));
			log.info("Order {} cancelled: payment window expired", orderId);
			return;
		} else {
			return;
		}

		orderRepository.save(order);
		log.info("Order {} updated to {}", orderId, order.getStatus());
	}

	@Transactional
	public void expireUnpaidOrder(Long orderId) {
		Order order = orderRepository.findById(orderId).orElse(null);
		if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
			return;
		}
		order.setStatus(OrderStatus.CANCELLED);
		order.setCancellationReason(CancellationReason.PAYMENT_TIMEOUT);
		// saveAndFlush sends the UPDATE now, so a DB error is thrown here and the log
		// below only runs when the database accepted the change
		Order saved = orderRepository.saveAndFlush(order);
		log.info("Order {} cancelled: payment timeout", orderId);
		eventPublisher.publishEvent(toEvent(saved));
	}

	// Stock was never confirmed (no inventory result arrived), so the order is cancelled.
	// The CANCELLED event lets Product release any stock it reserved late.
	@Transactional
	public void expireStuckOrder(Long orderId) {
		Order order = orderRepository.findById(orderId).orElse(null);
		if (order == null || order.getStatus() != OrderStatus.PENDING) {
			return;
		}
		order.setStatus(OrderStatus.CANCELLED);
		order.setCancellationReason(CancellationReason.RESERVATION_TIMEOUT);
		// saveAndFlush: same reason as in expireUnpaidOrder
		Order saved = orderRepository.saveAndFlush(order);
		log.info("Order {} cancelled: reservation timeout", orderId);
		eventPublisher.publishEvent(toEvent(saved));
	}

	// Someone else's order gives the same 404 as a missing one, so its existence is
	// not revealed
	private Order findOrderForUser(Long id, Long userId, boolean isAdmin) {
		return orderRepository.findById(id).filter(order -> isAdmin || order.getUserId().equals(userId))
				.orElseThrow(() -> new OrderNotFoundException(id));
	}

	private OrderEvent toEvent(Order order) {
		List<OrderEventItem> eventItems = order.getItems().stream()
				.map(item -> new OrderEventItem(item.getProductId(), item.getQuantity())).collect(Collectors.toList());

		return new OrderEvent(order.getId(), order.getUserId(), order.getTotalAmount(), order.getStatus().name(),
				eventItems, order.getCancellationReason() == null ? null : order.getCancellationReason().name());
	}

	private OrderResponse mapToResponse(Order order) {
		OrderResponse response = new OrderResponse();
		response.setId(order.getId());
		response.setUserId(order.getUserId());
		response.setStatus(order.getStatus());
		response.setCancellationReason(order.getCancellationReason());
		response.setTotalAmount(order.getTotalAmount());
		response.setCreatedAt(order.getCreatedAt());

		List<OrderItemResponse> itemResponses = order.getItems().stream().map(item -> {
			OrderItemResponse ir = new OrderItemResponse();
			ir.setId(item.getId());
			ir.setProductId(item.getProductId());
			ir.setProductName(item.getProductName());
			ir.setQuantity(item.getQuantity());
			ir.setPrice(item.getPrice());
			return ir;
		}).collect(Collectors.toList());

		response.setItems(itemResponses);
		return response;
	}

}
