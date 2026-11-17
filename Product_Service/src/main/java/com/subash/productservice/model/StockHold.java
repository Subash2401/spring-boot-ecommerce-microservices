package com.subash.productservice.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Stock taken from a product for one order. Stored inside the product document, so taking
// or giving back stock and adding or removing the hold happen in one atomic update.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockHold {

	private Long orderId;
	private long quantity;
	// true once the order is paid: the stock is sold. Kept for a while so a refund can still give it back
	private boolean paid;
	private LocalDateTime createdAt;
}
