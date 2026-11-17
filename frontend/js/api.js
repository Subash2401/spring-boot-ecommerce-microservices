// The ONE place that talks to the backend. Every page calls api(...) instead of fetch(...).
//
// What it does for every request:
// 1. adds "Authorization: Bearer <access token>" (unless auth: false)
// 2. on 401 (access token expired), gets a new access token ONCE via /users/refresh
//    (the browser sends the HttpOnly refresh cookie) and repeats the request
// 3. if that fails too, clears the login and opens the login page
// 4. turns every error into an ApiError with a friendly message

import { API_BASE } from './config.js';
import { getAccessToken, setAccessToken, clearSession, goToLogin } from './auth.js';

export class ApiError extends Error {
	constructor(status, message) {
		super(message);
		this.status = status;
	}
}

// Friendly messages per HTTP status. For 400, 404, 409 and 423 the backend's own message
// is shown instead, because it says exactly what is wrong (e.g. "Quantity cannot be more than 100").
const MESSAGES = {
	0: 'Cannot reach the server. Is the backend running?',
	400: 'Please check what you entered.',
	401: 'Please log in again.',
	403: 'You are not allowed to do that.',
	404: 'Not found.',
	409: 'This changed in the meantime. Please reload the page and try again.',
	413: 'That is too large to send (maximum 1 MB).',
	415: 'The server did not accept the request format.',
	423: 'Your account is locked for 15 minutes after too many wrong passwords.',
	429: 'Too many requests. Please wait a moment and try again.',
	500: 'Something went wrong on the server. Please try again.',
	503: 'The service is busy right now. Please try again in a few seconds.',
};
const USE_BACKEND_MESSAGE = [400, 401, 404, 409, 423];

// Sends one HTTP request. Returns the fetch Response (never throws for HTTP errors).
async function send(path, { method = 'GET', body, auth = true, credentials = 'omit' }) {
	const headers = {};
	if (body !== undefined) {
		headers['Content-Type'] = 'application/json';
	}
	const token = getAccessToken();
	if (auth && token) {
		headers['Authorization'] = 'Bearer ' + token;
	}
	try {
		return await fetch(API_BASE + path, {
			method,
			headers,
			body: body === undefined ? undefined : JSON.stringify(body),
			credentials, // 'include' only for login/refresh/logout, so the cookie is sent and saved
		});
	} catch {
		throw new ApiError(0, MESSAGES[0]);
	}
}

// Reads {status, message} from an error response and picks the message to show.
async function toApiError(response) {
	let backendMessage = null;
	try {
		const data = await response.json();
		backendMessage = data.message || null;
	} catch {
		// the body was empty or not JSON
	}
	const status = response.status;
	const message = (USE_BACKEND_MESSAGE.includes(status) && backendMessage)
		|| MESSAGES[status] || MESSAGES[500];
	return new ApiError(status, message);
}

// ---- Refreshing the access token -------------------------------------------------------
// Two things can go wrong if we are careless:
// - Two requests in this tab get 401 at the same moment and both refresh. The second would
//   send an already-used refresh token, the backend treats that as theft and logs us out.
//   So all callers share ONE refresh (refreshInFlight).
// - Two TABS refresh at the same moment (they share the cookie). navigator.locks makes the
//   tabs take turns, so each one sends the newest cookie.
let refreshInFlight = null;

export function refreshAccessToken() {
	if (!refreshInFlight) {
		refreshInFlight = refreshWithLock().finally(() => {
			refreshInFlight = null;
		});
	}
	return refreshInFlight; // resolves to true (new access token saved) or false
}

async function refreshWithLock() {
	const doRefresh = async () => {
		const response = await send('/users/refresh', { method: 'POST', auth: false, credentials: 'include' });
		if (!response.ok) {
			return false;
		}
		const data = await response.json();
		setAccessToken(data.accessToken);
		return true;
	};
	try {
		return navigator.locks ? await navigator.locks.request('refresh-token', doRefresh) : await doRefresh();
	} catch {
		return false; // server unreachable: treat as "not logged in"
	}
}

// ---- The function every page uses ------------------------------------------------------
// Returns the parsed JSON body (or null for 204), or throws ApiError.
export async function api(path, options = {}) {
	let response = await send(path, options);

	if (response.status === 401 && options.auth !== false) {
		const refreshed = await refreshAccessToken();
		if (refreshed) {
			response = await send(path, options); // try once more with the new access token
		}
		if (!refreshed || response.status === 401) {
			clearSession();
			goToLogin();
			throw new ApiError(401, MESSAGES[401]);
		}
	}

	if (!response.ok) {
		throw await toApiError(response);
	}
	return response.status === 204 ? null : response.json();
}
