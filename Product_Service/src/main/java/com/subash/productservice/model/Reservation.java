package com.subash.productservice.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.subash.productservice.kafka.OrderEventItem;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Document(collection = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
	@Id
	private String id;
	@Indexed(unique = true)
	private Long orderId;
	private List<OrderEventItem> items;
	// RESERVING -> RESERVED or REJECTED; RESERVED -> CONFIRMED (paid) or RELEASED
	private String status;
	// true: the stock is tracked as holds on the products. false: an older reservation from
	// before holds existed, released the old way from items
	private boolean usesHolds;
}
