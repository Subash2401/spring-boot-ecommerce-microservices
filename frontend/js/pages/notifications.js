// Notifications: GET /notifications/me?page=N&size=20 (newest first).
// Unread ones are highlighted; "Mark as read" = PUT /notifications/{id}/read,
// "Mark all as read" = PUT /notifications/me/read-all. The navbar badge is updated after both.

import { api, ApiError } from '../api.js';
import { requireLogin } from '../session.js';
import {
	el, formatDate, renderNavbar, showAlert, clearAlert, showLoading, showEmpty,
	renderPager, refreshUnreadBadge, withBusyButton,
} from '../ui.js';

const list = document.getElementById('notifications');
const pager = document.getElementById('pager');
const message = document.getElementById('message');
const markAllButton = document.getElementById('mark-all');

const PER_PAGE = 20;
let page = 0;

async function loadNotifications() {
	showLoading(list, 'Loading notifications...');
	try {
		const data = await api('/notifications/me?page=' + page + '&size=' + PER_PAGE);
		if (data.content.length === 0) {
			showEmpty(list, 'No notifications yet. You will get one for every step of your orders.');
		} else {
			list.replaceChildren(el('div', { class: 'list-group shadow-sm' }, data.content.map(notificationItem)));
		}
		renderPager(pager, data.page, (newPage) => {
			page = newPage;
			loadNotifications();
		});
	} catch (error) {
		list.replaceChildren();
		showAlert(message, error instanceof ApiError ? error.message : 'Could not load notifications.');
	}
}

function notificationItem(notification) {
	const item = el('div', {
		class: 'list-group-item d-flex flex-wrap justify-content-between align-items-start gap-2'
			+ (notification.read ? '' : ' notification-unread'),
	}, [
		el('div', {}, [
			el('div', { class: notification.read ? '' : 'fw-semibold', text: notification.message }),
			el('small', { class: 'text-muted', text: formatDate(notification.createdAt) }),
		]),
	]);
	if (!notification.read) {
		item.append(el('button', {
			class: 'btn btn-sm btn-outline-secondary', text: 'Mark as read',
			on: { click: (event) => markRead(notification, event.target) },
		}));
	}
	return item;
}

async function markRead(notification, button) {
	clearAlert(message);
	await withBusyButton(button, async () => {
		try {
			await api('/notifications/' + encodeURIComponent(notification.id) + '/read', { method: 'PUT' });
			refreshUnreadBadge();
			loadNotifications();
		} catch (error) {
			showAlert(message, error instanceof ApiError ? error.message : 'Could not update the notification.');
		}
	});
}

markAllButton.addEventListener('click', async () => {
	clearAlert(message);
	await withBusyButton(markAllButton, async () => {
		try {
			await api('/notifications/me/read-all', { method: 'PUT' });
			refreshUnreadBadge();
			loadNotifications();
		} catch (error) {
			showAlert(message, error instanceof ApiError ? error.message : 'Could not update the notifications.');
		}
	});
});

// Start
if (await requireLogin()) {
	renderNavbar('notifications.html');
	loadNotifications();
}
