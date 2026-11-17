// Login page: email + password -> access token (sessionStorage) + refresh cookie (HttpOnly).

import { api, ApiError } from '../api.js';
import { setAccessToken, setCurrentUser, safeNextPage } from '../auth.js';
import { rememberLogin } from '../session.js';
import { renderNavbar, showAlert, clearAlert, withBusyButton } from '../ui.js';

renderNavbar('login.html');

const form = document.getElementById('login-form');
const message = document.getElementById('message');
const params = new URLSearchParams(location.search);

// Messages from other pages ("you are registered", "you logged out")
if (params.get('registered')) {
	showAlert(message, 'Your account was created. Please log in.', 'success');
}
if (params.get('loggedOut')) {
	showAlert(message, 'You are logged out.', 'info');
}
if (params.get('email')) {
	document.getElementById('email').value = params.get('email');
}

form.addEventListener('submit', async (event) => {
	event.preventDefault(); // we send the form ourselves with fetch
	clearAlert(message);
	const email = document.getElementById('email').value.trim();
	const password = document.getElementById('password').value;
	if (!email || !password) {
		showAlert(message, 'Please enter your email and password.');
		return;
	}

	await withBusyButton(document.getElementById('submit'), async () => {
		try {
			// credentials: 'include' lets the browser save the refresh-token cookie from the answer
			const tokens = await api('/users/login', {
				method: 'POST', body: { email, password }, auth: false, credentials: 'include',
			});
			setAccessToken(tokens.accessToken);
			setCurrentUser(await api('/users/me'));
			rememberLogin();
			location.href = safeNextPage(params.get('next'));
		} catch (error) {
			// 401 wrong email/password, 423 locked, 429 too many tries: ApiError has the message
			showAlert(message, error instanceof ApiError ? error.message : 'Login failed.');
		}
	});
});
