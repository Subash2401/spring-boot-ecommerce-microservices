// Pay page: pay.html?orderId=N
//
// The order goes PENDING (stock being checked) -> AWAITING_PAYMENT (payment created) -> paid.
// This page waits for AWAITING_PAYMENT, shows a countdown to the payment's expiresAt (the
// backend's 15-minute window) and lets the user pay after a "non-refundable" confirmation.

import { api, ApiError } from '../api.js';
import { requireLogin } from '../session.js';
import {
	el, formatMoney, statusBadge, reasonText, renderNavbar, showAlert, clearAlert,
	showLoading, confirmDialog, withBusyButton, parseServerDate,
} from '../ui.js';

const content = document.getElementById('content');
const message = document.getElementById('message');
const orderId = Number(new URLSearchParams(location.search).get('orderId'));

const POLL_MS = 3000;
let countdownTimer = null;

// Loads the order (and its payment when there is one) and shows the right view.
async function load() {
	clearInterval(countdownTimer);
	let order;
	try {
		order = await api('/orders/' + orderId);
	} catch (error) {
		showError(error, 'Could not load the order.');
		return;
	}

	if (order.status === 'PENDING') {
		showWaiting(order, 'We are checking the stock for your order...');
		setTimeout(load, POLL_MS);
		return;
	}
	if (order.status !== 'AWAITING_PAYMENT') {
		showFinal(order);
		return;
	}

	// AWAITING_PAYMENT: the payment is created a moment after the stock is reserved
	let payment;
	try {
		payment = await api('/payments/order/' + orderId);
	} catch (error) {
		if (error instanceof ApiError && error.status === 404) {
			showWaiting(order, 'Preparing your payment...');
			setTimeout(load, POLL_MS);
			return;
		}
		showError(error, 'Could not load the payment.');
		return;
	}
	if (payment.status === 'PENDING') {
		showPayForm(order, payment);
	} else {
		showFinal(order, payment);
	}
}

function showError(error, fallback) {
	content.replaceChildren();
	showAlert(message, error instanceof ApiError ? error.message : fallback);
}

// Order number, total and items (used by every view)
function orderSummary(order) {
	return el('div', { class: 'card shadow-sm mb-4' }, [
		el('div', { class: 'card-body' }, [
			el('div', { class: 'd-flex justify-content-between align-items-center mb-2' }, [
				el('h2', { class: 'h5 mb-0', text: 'Order #' + order.id }),
				statusBadge(order.status),
			]),
			el('ul', { class: 'list-unstyled mb-2' }, order.items.map((item) =>
				el('li', { text: item.quantity + ' × ' + item.productName + ' (' + formatMoney(item.price) + ')' }))),
			el('div', { class: 'fs-5' }, ['Total: ', el('strong', { text: formatMoney(order.totalAmount) })]),
		]),
	]);
}

function showWaiting(order, text) {
	content.replaceChildren(orderSummary(order));
	const spinner = el('div');
	showLoading(spinner, text);
	content.append(spinner);
}

// Order is cancelled, confirmed, or the payment is no longer PENDING
function showFinal(order, payment) {
	content.replaceChildren(orderSummary(order));
	if (order.status === 'CANCELLED') {
		showAlert(message, 'This order was cancelled: ' + reasonText(order.cancellationReason) + '.', 'warning');
	} else if (order.status === 'CONFIRMED' || (payment && payment.status === 'SUCCESS')) {
		showAlert(message, 'This order is paid. Thank you!', 'success');
	} else if (payment && payment.status === 'PROCESSING') {
		showAlert(message, 'Your payment is being processed...', 'info');
		setTimeout(load, POLL_MS);
	} else if (payment) {
		showAlert(message, 'This payment cannot be made any more (' + payment.status.toLowerCase() + ').', 'warning');
	}
	content.append(el('a', { class: 'btn btn-outline-primary', href: 'orders.html', text: 'Go to my orders' }));
}

function showPayForm(order, payment) {
	const deadline = parseServerDate(payment.expiresAt);
	const countdown = el('div', { class: 'countdown fw-bold' });
	const payButton = el('button', {
		class: 'btn btn-success btn-lg', text: 'Pay ' + formatMoney(payment.amount),
		on: { click: () => pay(payment, payButton) },
	});

	content.replaceChildren(
		orderSummary(order),
		el('div', { class: 'card shadow-sm' }, [
			el('div', { class: 'card-body text-center' }, [
				el('p', { class: 'mb-1', text: 'Time left to pay:' }),
				countdown,
				el('p', { class: 'text-muted small', text: 'If you do not pay in time, the order is cancelled automatically.' }),
				payButton,
			]),
		]));

	// Count down every second. At 0 the button is disabled; the backend expires the payment.
	const tick = () => {
		const secondsLeft = Math.max(0, Math.floor((deadline - Date.now()) / 1000));
		const minutes = String(Math.floor(secondsLeft / 60)).padStart(2, '0');
		const seconds = String(secondsLeft % 60).padStart(2, '0');
		countdown.textContent = minutes + ':' + seconds;
		if (secondsLeft === 0) {
			clearInterval(countdownTimer);
			payButton.disabled = true;
			showAlert(message, 'The time to pay has run out. This order will be cancelled.', 'warning');
		}
	};
	tick();
	countdownTimer = setInterval(tick, 1000);
}

async function pay(payment, button) {
	const sure = await confirmDialog({
		title: 'Confirm payment',
		message: 'You are about to pay ' + formatMoney(payment.amount) + '. Payments are non-refundable. Are you sure?',
		confirmText: 'Yes, pay now',
	});
	if (!sure) {
		return;
	}
	clearAlert(message);
	await withBusyButton(button, async () => {
		try {
			const result = await api('/payments/order/' + orderId + '/pay', { method: 'POST' });
			clearInterval(countdownTimer);
			if (result.status === 'SUCCESS') {
				showAlert(message, 'Payment successful! Your order will show as Confirmed in a moment.', 'success');
			} else {
				showAlert(message, 'The payment was declined. Your order has been cancelled.', 'danger');
			}
			content.replaceChildren(el('a', { class: 'btn btn-primary', href: 'orders.html', text: 'Go to my orders' }));
		} catch (error) {
			// 409: already paid, being processed, or the window expired
			showAlert(message, error instanceof ApiError ? error.message : 'The payment failed.');
			setTimeout(load, POLL_MS);
		}
	});
}

// Start
if (await requireLogin()) {
	renderNavbar('pay.html');
	if (!Number.isInteger(orderId) || orderId <= 0) {
		showAlert(message, 'No order selected.');
		content.replaceChildren(el('a', { class: 'btn btn-primary', href: 'orders.html', text: 'Go to my orders' }));
	} else {
		document.getElementById('title').textContent = 'Pay for order #' + orderId;
		showLoading(content);
		load();
	}
}
