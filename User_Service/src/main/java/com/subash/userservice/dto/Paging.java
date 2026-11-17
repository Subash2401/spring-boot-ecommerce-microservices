package com.subash.userservice.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Every list endpoint pages its results. The client chooses page and size (default 20,
// at most 50); the sort is always the fixed one passed in, so results come in a stable
// order and an unknown sort field from a client can never cause an error.
public final class Paging {

	public static final int MAX_PAGE_SIZE = 50;

	private Paging() {
	}

	public static Pageable of(Pageable requested, Sort sort) {
		int size = Math.min(requested.getPageSize(), MAX_PAGE_SIZE);
		return PageRequest.of(requested.getPageNumber(), size, sort);
	}
}
