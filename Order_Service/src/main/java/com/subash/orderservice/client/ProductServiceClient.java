package com.subash.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.subash.orderservice.dto.ProductResponse;

@FeignClient(name = "PRODUCT-SERVICE")
public interface ProductServiceClient {
	
	@GetMapping("/products/{id}")
	ProductResponse getProductById(@PathVariable("id") String id);
}
