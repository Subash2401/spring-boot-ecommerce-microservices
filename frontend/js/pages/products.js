// Product list (home page). Public: works without logging in.
// GET /products?page=N&size=12 returns { content: [...], page: { number, totalPages, ... } }

import { api, ApiError } from '../api.js';
import { PAGE_SIZE } from '../config.js';
import { addToCart } from '../cart-store.js';
import { restoreSession } from '../session.js';
import {
	el, formatMoney, renderNavbar, updateCartBadge, showAlert, clearAlert,
	showLoading, showEmpty, renderPager,
} from '../ui.js';

const grid = document.getElementById('products');
const pager = document.getElementById('pager');
const message = document.getElementById('message');

// The page number lives in the URL (?page=2), so reload and the back button keep it.
function currentPage() {
	const page = Number(new URLSearchParams(location.search).get('page'));
	return Number.isInteger(page) && page >= 0 ? page : 0;
}

async function loadProducts(page) {
	clearAlert(message);
	showLoading(grid, 'Loading products...');
	try {
		const data = await api('/products?page=' + page + '&size=' + PAGE_SIZE, { auth: false });
		if (data.content.length === 0) {
			showEmpty(grid, 'There are no products yet.');
		} else {
			grid.replaceChildren(el('div', { class: 'row g-3' }, data.content.map(productCard)));
		}
		renderPager(pager, data.page, (newPage) => {
			history.replaceState(null, '', '?page=' + newPage);
			loadProducts(newPage);
		});
	} catch (error) {
		grid.replaceChildren();
		showAlert(message, error instanceof ApiError ? error.message : 'Could not load the products.');
	}
}

// One Bootstrap card: name, description, price, stock and an "Add to cart" button.
function productCard(product) {
	const inStock = product.quantity > 0;
	const button = el('button', {
		class: 'btn btn-primary mt-3',
		text: inStock ? 'Add to cart' : 'Out of stock',
		disabled: !inStock,
		on: {
			click: () => {
				const problem = addToCart(product);
				if (problem) {
					showAlert(message, problem, 'warning');
				} else {
					showAlert(message, product.name + ' was added to your cart.', 'success');
					updateCartBadge();
				}
			},
		},
	});
	return el('div', { class: 'col-12 col-sm-6 col-lg-4' }, [
		el('div', { class: 'card product-card shadow-sm' }, [
			el('div', { class: 'card-body' }, [
				el('h2', { class: 'h5 card-title', text: product.name }),
				el('p', { class: 'card-text', text: product.description || '' }),
				el('div', { class: 'd-flex justify-content-between align-items-center' }, [
					el('span', { class: 'fs-5 fw-bold', text: formatMoney(product.price) }),
					el('span', {
						class: inStock ? 'text-success small' : 'text-danger small',
						text: inStock ? product.quantity + ' in stock' : 'Out of stock',
					}),
				]),
				button,
			]),
		]),
	]);
}

// Start: find out if the user is logged in (for the navbar), then load the first page.
await restoreSession().catch(() => false);
renderNavbar('index.html');
loadProducts(currentPage());
