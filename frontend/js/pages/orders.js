// My orders: GET /orders/me?page=N&size=10 (newest first).
// While an order is still moving (PENDING or AWAITING_PAYMENT) the list refreshes every
// 5 seconds, so the user sees it change to Confirmed / Cancelled without reloading.

import { api, ApiError } from '../api.js';
import { requireLogin } from '../session.js';
import {
	el, formatMoney, formatDate, statusBadge, reasonText, renderNavbar, showAlert, clearAlert,
	showLoading, showEmpty, renderPager, confirmDialog, refreshUnreadBadge,
} from '../ui.js';

const list = document.getElementById('orders');
const pager = document.getElementById('pager');
const message = document.getElementById('message');

const ORDERS_PER_PAGE = 10;
const POLL_MS = 5000;
// Orders that can still change by themselves (and can still be cancelled)
const ACTIVE = ['PENDING', 'AWAITING_PAYMENT'];

let page = 0;
let pollTimer = null;

async function loadOrders(showSpinner) {
	clearTimeout(pollTimer);
	if (showSpinner) {
		showLoading(list, 'Loading your orders...');
	}
	try {
		const data = await api('/orders/me?page=' + page + '&size=' + ORDERS_PER_PAGE);
		if (data.content.length === 0) {
			showEmpty(list, 'You have no orders yet.', 'Browse products', 'index.html');
		} else {
			list.replaceChildren(...data.content.map(orderCard));
		}
		renderPager(pager, data.page, (newPage) => {
			page = newPage;
			loadOrders(true);
		});
		// Keep polling only while something can still change
		if (data.content.some((order) => ACTIVE.includes(order.status))) {
			pollTimer = setTimeout(() => loadOrders(false), POLL_MS);
		}
	} catch (error) {
		list.replaceChildren();
		showAlert(message, error instanceof ApiError ? error.message : 'Could not load your orders.');
	}
}

function orderCard(order) {
	const actions = [];
	if (order.status === 'AWAITING_PAYMENT') {
		actions.push(el('a', { class: 'btn btn-success btn-sm', href: 'pay.html?orderId=' + order.id, text: 'Pay now' }));
	}
	if (ACTIVE.includes(order.status)) {
		const cancelButton = el('button', {
			class: 'btn btn-outline-danger btn-sm', text: 'Cancel order',
			on: { click: () => cancelOrder(order) },
		});
		actions.push(cancelButton);
	}

	return el('div', { class: 'card shadow-sm mb-3' }, [
		el('div', { class: 'card-body' }, [
			el('div', { class: 'd-flex flex-wrap justify-content-between align-items-center gap-2 mb-2' }, [
				el('div', {}, [
					el('strong', { text: 'Order #' + order.id }),
					el('span', { class: 'text-muted ms-2 small', text: formatDate(order.createdAt) }),
				]),
				statusBadge(order.status),
			]),
			el('ul', { class: 'list-unstyled mb-2 small' }, order.items.map((item) =>
				el('li', { text: item.quantity + ' × ' + item.productName + ' (' + formatMoney(item.price) + ')' }))),
			order.cancellationReason
				? el('p', { class: 'text-danger small mb-2', text: 'Reason: ' + reasonText(order.cancellationReason) })
				: null,
			el('div', { class: 'd-flex flex-wrap justify-content-between align-items-center gap-2' }, [
				el('span', { class: 'fw-bold', text: formatMoney(order.totalAmount) }),
				el('div', { class: 'd-flex gap-2' }, actions),
			]),
		]),
	]);
}

async function cancelOrder(order) {
	const sure = await confirmDialog({
		title: 'Cancel order #' + order.id + '?',
		message: 'The reserved stock will be released. This cannot be undone.',
		confirmText: 'Yes, cancel it',
		danger: true,
	});
	if (!sure) {
		return;
	}
	clearAlert(message);
	try {
		await api('/orders/' + order.id + '/cancel', { method: 'PUT' });
		showAlert(message, 'Order #' + order.id + ' was cancelled.', 'success');
		refreshUnreadBadge();
	} catch (error) {
		// 409: the order moved on in the meantime (for example it was just paid)
		showAlert(message, error instanceof ApiError ? error.message : 'The order could not be cancelled.');
	}
	loadOrders(false);
}

// Start
if (await requireLogin()) {
	renderNavbar('orders.html');
	loadOrders(true);
}
