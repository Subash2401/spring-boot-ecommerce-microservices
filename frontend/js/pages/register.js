// Register page: creates an account, then sends the user to the login page.

import { api, ApiError } from '../api.js';
import { renderNavbar, showAlert, clearAlert, withBusyButton } from '../ui.js';

renderNavbar('register.html');

const form = document.getElementById('register-form');
const message = document.getElementById('message');

form.addEventListener('submit', async (event) => {
	event.preventDefault();
	clearAlert(message);
	const name = document.getElementById('name').value.trim();
	const email = document.getElementById('email').value.trim();
	const password = document.getElementById('password').value;

	// Quick checks in the browser; the backend checks everything again.
	if (!name || !email || !password) {
		showAlert(message, 'Please fill in all fields.');
		return;
	}
	if (password.length < 8 || password.length > 72) {
		showAlert(message, 'The password must be 8 to 72 characters.');
		return;
	}

	await withBusyButton(document.getElementById('submit'), async () => {
		try {
			await api('/users/register', { method: 'POST', body: { name, email, password }, auth: false });
			location.href = 'login.html?registered=1&email=' + encodeURIComponent(email);
		} catch (error) {
			// 400: the backend says which field is wrong; 409: email already registered
			showAlert(message, error instanceof ApiError ? error.message : 'Registration failed.');
		}
	});
});
