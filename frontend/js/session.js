// Starting a page: "am I logged in?" and logging out.
//
// A new tab has no access token (sessionStorage is per tab), but the refresh cookie may still
// be valid. So before sending the user to the login page we try ONE silent refresh.
// We only try it when localStorage says the user logged in before ("maybeLoggedIn"); otherwise
// every page view of a logged-out visitor would call /users/refresh, which shares the
// 5-per-minute rate limit with /users/login.

import { api, refreshAccessToken } from './api.js';
import { isLoggedIn, getCurrentUser, setCurrentUser, clearSession, goToLogin } from './auth.js';

const MAYBE_LOGGED_IN_KEY = 'maybeLoggedIn';

export function rememberLogin() {
	localStorage.setItem(MAYBE_LOGGED_IN_KEY, '1');
}

// Makes sure we have an access token and know the user. Returns true if logged in.
export async function restoreSession() {
	if (!isLoggedIn()) {
		if (localStorage.getItem(MAYBE_LOGGED_IN_KEY) !== '1') {
			return false;
		}
		const refreshed = await refreshAccessToken();
		if (!refreshed) {
			localStorage.removeItem(MAYBE_LOGGED_IN_KEY);
			return false;
		}
	}
	if (!getCurrentUser()) {
		setCurrentUser(await api('/users/me'));
	}
	return true;
}

// For pages that need a login (cart checkout, orders, pay, notifications, admin).
export async function requireLogin() {
	const loggedIn = await restoreSession();
	if (!loggedIn) {
		goToLogin();
	}
	return loggedIn;
}

// Ends the session on the server (revokes the refresh token, deletes the cookie) and here.
export async function logout() {
	try {
		await api('/users/logout', { method: 'POST', auth: false, credentials: 'include' });
	} catch {
		// even if the server cannot be reached, forget the login in this browser
	}
	clearSession();
	localStorage.removeItem(MAYBE_LOGGED_IN_KEY);
	location.href = 'login.html?loggedOut=1';
}
