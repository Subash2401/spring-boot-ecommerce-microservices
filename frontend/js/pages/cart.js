// Cart + checkout.
// 1. Check every cart line against the current product (GET /products/{id}): removed products
//    are dropped, quantities are limited to the stock, names and prices are refreshed.
// 2. "Place order" sends POST /orders { items: [{ productId, quantity }] } and opens the pay page.

import { api, ApiError } from '../api.js';
import { MAX_QUANTITY } from '../config.js';
import { isLoggedIn, goToLogin } from '../auth.js';
import { getCart, replaceCart, setQuantity, removeFromCart, clearCart } from '../cart-store.js';
import { restoreSession } from '../session.js';
import {
	el, formatMoney, renderNavbar, updateCartBadge, showAlert, clearAlert,
	showLoading, showEmpty, withBusyButton,
} from '../ui.js';

const cartBox = document.getElementById('cart');
const message = document.getElementById('message');

// Current stock per productId, filled by checkAgainstStock()
const stockById = new Map();

async function checkAgainstStock() {
	const notes = [];
	const checked = [];
	// One request at a time: a 50-line cart sent all at once would hit the gateway's rate limit.
	for (const line of getCart()) {
		try {
			const product = await api('/products/' + encodeURIComponent(line.productId), { auth: false });
			stockById.set(product.id, product.quantity);
			if (product.quantity === 0) {
				notes.push(product.name + ' is out of stock and was removed.');
				continue;
			}
			const maxAllowed = Math.min(product.quantity, MAX_QUANTITY);
			let quantity = line.quantity;
			if (quantity > maxAllowed) {
				quantity = maxAllowed;
				notes.push('Only ' + maxAllowed + ' of ' + product.name + ' can be ordered; the quantity was changed.');
			}
			checked.push({ productId: product.id, name: product.name, price: product.price, quantity });
		} catch (error) {
			if (error instanceof ApiError && error.status === 404) {
				notes.push(line.name + ' is no longer sold and was removed.');
			} else {
				throw error; // server problem: keep the cart as it is
			}
		}
	}
	replaceCart(checked);
	updateCartBadge();
	if (notes.length > 0) {
		showAlert(message, notes.join(' '), 'warning');
	}
}

function render() {
	const lines = getCart();
	if (lines.length === 0) {
		showEmpty(cartBox, 'Your cart is empty.', 'Browse products', 'index.html');
		return;
	}

	const rows = lines.map((line) => {
		const max = Math.min(stockById.get(line.productId) ?? MAX_QUANTITY, MAX_QUANTITY);
		const quantityInput = el('input', {
			type: 'number', class: 'form-control form-control-sm qty-input', min: '1', max: String(max),
			value: String(line.quantity), 'aria-label': 'Quantity of ' + line.name,
			on: {
				change: (event) => {
					// Keep the number between 1 and the stock (or 100)
					let quantity = Math.floor(Number(event.target.value));
					if (!Number.isFinite(quantity) || quantity < 1) {
						quantity = 1;
					}
					quantity = Math.min(quantity, max);
					setQuantity(line.productId, quantity);
					updateCartBadge();
					render();
				},
			},
		});
		const removeButton = el('button', {
			class: 'btn btn-sm btn-outline-danger', text: 'Remove',
			on: { click: () => { removeFromCart(line.productId); updateCartBadge(); render(); } },
		});
		return el('tr', {}, [
			el('td', { text: line.name }),
			el('td', { class: 'text-end', text: formatMoney(line.price) }),
			el('td', {}, [quantityInput]),
			el('td', { class: 'text-end', text: formatMoney(line.price * line.quantity) }),
			el('td', { class: 'text-end' }, [removeButton]),
		]);
	});

	const total = lines.reduce((sum, l) => sum + l.price * l.quantity, 0);
	const placeOrderButton = el('button', {
		class: 'btn btn-success btn-lg', text: isLoggedIn() ? 'Place order' : 'Log in to place your order',
		on: { click: () => placeOrder(placeOrderButton) },
	});

	cartBox.replaceChildren(
		el('div', { class: 'card shadow-sm' }, [
			el('div', { class: 'table-responsive' }, [
				el('table', { class: 'table align-middle mb-0' }, [
					el('thead', {}, [el('tr', {}, [
						el('th', { text: 'Product' }), el('th', { class: 'text-end', text: 'Price' }),
						el('th', { text: 'Quantity' }), el('th', { class: 'text-end', text: 'Total' }), el('th'),
					])]),
					el('tbody', {}, rows),
				]),
			]),
		]),
		el('div', { class: 'd-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3 mt-4' }, [
			el('div', { class: 'fs-4' }, ['Total: ', el('strong', { text: formatMoney(total) })]),
			placeOrderButton,
		]),
		el('p', { class: 'text-muted small mt-2', text: 'Prices are checked again when you place the order.' }));
}

async function placeOrder(button) {
	if (!isLoggedIn()) {
		goToLogin(); // comes back to the cart after logging in
		return;
	}
	clearAlert(message);
	const items = getCart().map((l) => ({ productId: l.productId, quantity: l.quantity }));
	await withBusyButton(button, async () => {
		try {
			const order = await api('/orders', { method: 'POST', body: { items } });
			clearCart();
			updateCartBadge();
			location.href = 'pay.html?orderId=' + order.id;
		} catch (error) {
			// 400: unknown product / wrong quantity; 503: product service busy (cart is kept)
			showAlert(message, error instanceof ApiError ? error.message : 'The order could not be placed.');
		}
	});
}

// Start
await restoreSession().catch(() => false);
renderNavbar('cart.html');
if (getCart().length === 0) {
	render();
} else {
	showLoading(cartBox, 'Checking prices and stock...');
	try {
		await checkAgainstStock();
	} catch (error) {
		showAlert(message, (error instanceof ApiError ? error.message : 'Could not check the stock.')
			+ ' Your cart is shown as saved.', 'warning');
	}
	render();
}
