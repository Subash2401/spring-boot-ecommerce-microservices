// Small UI helpers used by every page.
//
// SECURITY: data from the API (product names, messages, ...) is ALWAYS put on the page with
// textContent, never innerHTML. textContent shows text as text, so a product named
// "<script>...</script>" can never run as code (that would be XSS).

import { api } from './api.js';
import { isLoggedIn, isAdmin, getCurrentUser } from './auth.js';
import { cartCount } from './cart-store.js';
import { logout } from './session.js';

// ---- Building elements ------------------------------------------------------------------
// el('a', { class: 'btn', href: 'x.html', text: 'Go' }, [children...])
// "text" becomes textContent; "on" adds event listeners: { on: { click: handler } }.
export function el(tag, props = {}, children = []) {
	const node = document.createElement(tag);
	for (const [key, value] of Object.entries(props)) {
		if (value === undefined || value === null || value === false) {
			continue;
		}
		if (key === 'text') {
			node.textContent = value;
		} else if (key === 'class') {
			node.className = value;
		} else if (key === 'on') {
			for (const [event, handler] of Object.entries(value)) {
				node.addEventListener(event, handler);
			}
		} else {
			node.setAttribute(key, value === true ? '' : value);
		}
	}
	for (const child of [].concat(children)) {
		if (child !== null && child !== undefined && child !== false) {
			node.append(child); // strings are added as plain text
		}
	}
	return node;
}

// ---- Formatting -------------------------------------------------------------------------
const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

export function formatMoney(amount) {
	return money.format(Number(amount));
}

// The backend sends times in UTC. Most fields have no zone ("2026-11-15T16:00:00"), and a
// browser would read those as local time, so a missing zone is treated as UTC ("Z").
export function parseServerDate(value) {
	if (!value) {
		return null;
	}
	const hasZone = /[zZ]|[+-]\d\d:\d\d$/.test(value);
	return new Date(hasZone ? value : value + 'Z');
}

// Indian style, in the user's own time zone: "16 Nov 2026, 2:20 pm".
const dateFormat = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDate(value) {
	const date = parseServerDate(value);
	return date ? dateFormat.format(date) : '';
}

// Readable labels and Bootstrap colours for order / payment statuses.
const STATUS = {
	PENDING: ['Checking stock', 'secondary'],
	AWAITING_PAYMENT: ['Waiting for payment', 'warning'],
	CONFIRMED: ['Confirmed', 'success'],
	CANCELLED: ['Cancelled', 'danger'],
	DELIVERED: ['Delivered', 'success'],
	PROCESSING: ['Processing', 'info'],
	SUCCESS: ['Paid', 'success'],
	FAILED: ['Failed', 'danger'],
	EXPIRED: ['Expired', 'danger'],
	REFUNDED: ['Refunded', 'info'],
};

export function statusBadge(status) {
	const [label, colour] = STATUS[status] || [status, 'secondary'];
	const textClass = colour === 'warning' ? ' text-dark' : '';
	return el('span', { class: 'badge bg-' + colour + textClass, text: label });
}

const REASONS = {
	OUT_OF_STOCK: 'Not enough stock',
	PAYMENT_FAILED: 'The payment failed',
	PAYMENT_TIMEOUT: 'Not paid in time',
	RESERVATION_TIMEOUT: 'Stock could not be confirmed in time',
	USER_CANCELLED: 'Cancelled by you',
};

export function reasonText(reason) {
	return REASONS[reason] || reason || '';
}

// ---- Messages, loading and empty states -------------------------------------------------
// Shows a Bootstrap alert inside "container" (replacing what was there).
export function showAlert(container, message, type = 'danger') {
	container.replaceChildren(el('div', { class: 'alert alert-' + type, role: 'alert', text: message }));
}

export function clearAlert(container) {
	container.replaceChildren();
}

export function showLoading(container, text = 'Loading...') {
	container.replaceChildren(
		el('div', { class: 'text-center text-muted py-5' }, [
			el('div', { class: 'spinner-border mb-2', role: 'status' }),
			el('div', { text }),
		]));
}

export function showEmpty(container, text, linkText, href) {
	container.replaceChildren(
		el('div', { class: 'text-center text-muted py-5' }, [
			el('p', { class: 'mb-3', text }),
			linkText ? el('a', { class: 'btn btn-primary', href, text: linkText }) : null,
		]));
}

// Disables a button and shows a small spinner while "work" runs.
export async function withBusyButton(button, work) {
	const original = button.textContent;
	button.disabled = true;
	button.replaceChildren(el('span', { class: 'spinner-border spinner-border-sm me-2' }), 'Please wait...');
	try {
		return await work();
	} finally {
		button.disabled = false;
		button.textContent = original;
	}
}

// ---- Confirmation dialog ----------------------------------------------------------------
// Opens a Bootstrap modal and resolves to true (confirmed) or false (cancelled / closed).
export function confirmDialog({ title, message, confirmText = 'Yes', danger = false }) {
	return new Promise((resolve) => {
		let confirmed = false;
		const confirmButton = el('button', {
			type: 'button',
			class: 'btn ' + (danger ? 'btn-danger' : 'btn-primary'),
			text: confirmText,
			on: { click: () => { confirmed = true; modal.hide(); } },
		});
		const dialog = el('div', { class: 'modal fade', tabindex: '-1' }, [
			el('div', { class: 'modal-dialog modal-dialog-centered' }, [
				el('div', { class: 'modal-content' }, [
					el('div', { class: 'modal-header' }, [
						el('h5', { class: 'modal-title', text: title }),
						el('button', { type: 'button', class: 'btn-close', 'data-bs-dismiss': 'modal', 'aria-label': 'Close' }),
					]),
					el('div', { class: 'modal-body' }, [el('p', { class: 'mb-0', text: message })]),
					el('div', { class: 'modal-footer' }, [
						el('button', { type: 'button', class: 'btn btn-outline-secondary', 'data-bs-dismiss': 'modal', text: 'Cancel' }),
						confirmButton,
					]),
				]),
			]),
		]);
		document.body.append(dialog);
		// window.bootstrap comes from bootstrap.bundle.min.js (loaded in every page)
		const modal = new window.bootstrap.Modal(dialog);
		dialog.addEventListener('hidden.bs.modal', () => {
			dialog.remove();
			resolve(confirmed);
		});
		modal.show();
	});
}

// ---- Paging buttons ---------------------------------------------------------------------
// page = the "page" object of a Spring PagedModel: { size, number, totalElements, totalPages }
export function renderPager(container, page, onChange) {
	if (!page || page.totalPages <= 1) {
		container.replaceChildren();
		return;
	}
	const previous = el('button', {
		class: 'btn btn-outline-primary', text: '« Previous', disabled: page.number === 0,
		on: { click: () => onChange(page.number - 1) },
	});
	const next = el('button', {
		class: 'btn btn-outline-primary', text: 'Next »', disabled: page.number + 1 >= page.totalPages,
		on: { click: () => onChange(page.number + 1) },
	});
	const info = el('span', { class: 'mx-3 text-muted', text: 'Page ' + (page.number + 1) + ' of ' + page.totalPages });
	container.replaceChildren(el('div', { class: 'd-flex justify-content-center align-items-center my-4' }, [previous, info, next]));
}

// ---- Navbar -----------------------------------------------------------------------------
// Every page has <nav id="navbar"></nav>; this fills it. "active" is the current page's file.
export function renderNavbar(active) {
	const link = (href, text, extra) => el('li', { class: 'nav-item' }, [
		el('a', { class: 'nav-link' + (active === href ? ' active' : ''), href }, [text, extra || null]),
	]);

	const cartBadge = el('span', { class: 'badge bg-light text-dark ms-1', id: 'cart-count' });
	const leftLinks = [link('index.html', 'Products'), link('cart.html', 'Cart', cartBadge)];
	const rightLinks = [];

	if (isLoggedIn()) {
		const unreadBadge = el('span', { class: 'badge bg-danger ms-1 d-none', id: 'unread-count' });
		leftLinks.push(link('orders.html', 'My orders'), link('notifications.html', 'Notifications', unreadBadge));
		if (isAdmin()) {
			leftLinks.push(link('admin-products.html', 'Admin'));
		}
		const user = getCurrentUser();
		rightLinks.push(
			el('li', { class: 'nav-item' }, [el('span', { class: 'navbar-text me-3', text: user ? user.name : '' })]),
			el('li', { class: 'nav-item' }, [
				el('button', { class: 'btn btn-outline-light btn-sm mt-1', text: 'Log out', on: { click: logout } }),
			]));
	} else {
		rightLinks.push(link('login.html', 'Log in'), link('register.html', 'Register'));
	}

	const collapseId = 'main-nav';
	document.getElementById('navbar').replaceChildren(
		el('div', { class: 'container' }, [
			el('a', { class: 'navbar-brand fw-bold', href: 'index.html', text: 'Microshop' }),
			el('button', {
				class: 'navbar-toggler', type: 'button', 'data-bs-toggle': 'collapse', 'data-bs-target': '#' + collapseId,
				'aria-controls': collapseId, 'aria-expanded': 'false', 'aria-label': 'Toggle navigation',
			}, [el('span', { class: 'navbar-toggler-icon' })]),
			el('div', { class: 'collapse navbar-collapse', id: collapseId }, [
				el('ul', { class: 'navbar-nav me-auto' }, leftLinks),
				el('ul', { class: 'navbar-nav' }, rightLinks),
			]),
		]));

	updateCartBadge();
	if (isLoggedIn()) {
		refreshUnreadBadge();
		setInterval(refreshUnreadBadge, 30000); // keep the badge fresh while the page is open
	}
}

// Shows how many items are in the cart; hidden when the cart is empty (like the unread badge).
export function updateCartBadge() {
	const badge = document.getElementById('cart-count');
	if (badge) {
		const count = cartCount();
		badge.textContent = String(count);
		badge.classList.toggle('d-none', count === 0);
	}
}

// Asks for one unread notification just to read "totalElements" (the unread count).
export async function refreshUnreadBadge() {
	const badge = document.getElementById('unread-count');
	if (!badge) {
		return;
	}
	try {
		const data = await api('/notifications/me/unread?size=1');
		const count = data.page.totalElements;
		badge.textContent = count > 99 ? '99+' : String(count);
		badge.classList.toggle('d-none', count === 0);
	} catch {
		// the badge is not important enough to show an error
	}
}
