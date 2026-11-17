package com.subash.productservice.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.subash.productservice.dto.Paging;
import com.subash.productservice.dto.ProductRequest;
import com.subash.productservice.exception.ProductNotFoundException;
import com.subash.productservice.model.Product;
import com.subash.productservice.repository.ProductRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
	private final ProductRepository productRepository;
	
	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping
	public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request){
		Product product = new Product();
		copyFields(request, product);
		Product saved = productRepository.save(product);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}
	
	// Public. ?page=0&size=20 (size at most 50); by name, then id (stable across pages)
	@GetMapping
	public PagedModel<Product> getAllProduct(@PageableDefault(size = 20) Pageable pageable){
		return new PagedModel<>(productRepository.findAll(Paging.of(pageable, Sort.by("name", "id"))));
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<Product> getProductById(@PathVariable String id){
		Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));           
		return ResponseEntity.ok(product);	
	}
	
	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/{id}")
	public ResponseEntity<Product> updateProduct(@PathVariable String id, @Valid @RequestBody ProductRequest request){
		// Copy only the editable fields onto the stored product, so its id and @Version are kept.
		// If stock changed since it was loaded, save throws OptimisticLockingFailureException (409).
		Product existing = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		copyFields(request, existing);
		Product saved = productRepository.save(existing);
		return ResponseEntity.ok(saved);
	}
	
	@PreAuthorize("hasRole('ADMIN')")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteProduct(@PathVariable String id){
		if(!productRepository.existsById(id)) throw new ProductNotFoundException(id);
		productRepository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	private void copyFields(ProductRequest request, Product product) {
		product.setName(request.getName());
		product.setDescription(request.getDescription());
		product.setPrice(request.getPrice());
		product.setQuantity(request.getQuantity());
	}
	
}
