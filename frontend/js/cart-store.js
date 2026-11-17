// The shopping cart, saved in localStorage so it survives page reloads and closing the browser.
// Each line: { productId, name, price, quantity }. Prices here are only for display: the
// backend always uses the current price when the order is placed.

import { MAX_CART_LINES, MAX_QUANTITY } from './config.js';

const CART_KEY = 'cart';

export function getCart() {
	try {
		const lines = JSON.parse(localStorage.getItem(CART_KEY) || '[]');
		return Array.isArray(lines) ? lines : [];
	} catch {
		return []; // broken data: start with an empty cart
	}
}

function saveCart(lines) {
	localStorage.setItem(CART_KEY, JSON.stringify(lines));
}

// Adds one unit of a product. Returns an error message, or null if it worked.
export function addToCart(product) {
	const lines = getCart();
	const line = lines.find((l) => l.productId === product.id);
	if (line) {
		if (line.quantity >= Math.min(MAX_QUANTITY, product.quantity)) {
			return 'You already have all available stock of this product in your cart.';
		}
		line.quantity += 1;
	} else {
		if (lines.length >= MAX_CART_LINES) {
			return 'Your cart can hold at most ' + MAX_CART_LINES + ' different products.';
		}
		lines.push({ productId: product.id, name: product.name, price: product.price, quantity: 1 });
	}
	saveCart(lines);
	return null;
}

export function setQuantity(productId, quantity) {
	const lines = getCart();
	const line = lines.find((l) => l.productId === productId);
	if (line) {
		line.quantity = quantity;
		saveCart(lines);
	}
}

// Replaces the whole cart (used after checking it against the current stock).
export function replaceCart(lines) {
	saveCart(lines);
}

export function removeFromCart(productId) {
	saveCart(getCart().filter((l) => l.productId !== productId));
}

export function clearCart() {
	localStorage.removeItem(CART_KEY);
}

// Total number of items, for the badge in the navbar.
export function cartCount() {
	return getCart().reduce((sum, l) => sum + l.quantity, 0);
}
