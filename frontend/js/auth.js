// Login state in the browser.
//
// - The ACCESS token (a JWT, valid 30 minutes) is kept in sessionStorage: it survives a page
//   reload but is gone when the tab is closed. JavaScript can read it, which is why every
//   page avoids XSS (no innerHTML for data, a Content-Security-Policy, no inline scripts).
// - The REFRESH token is an HttpOnly cookie set by the backend. JavaScript can never read it;
//   the browser sends it only to /users/refresh and /users/logout.

const ACCESS_TOKEN_KEY = 'accessToken';
const USER_KEY = 'currentUser'; // { id, name, email, roles } from GET /users/me

export function getAccessToken() {
	return sessionStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token) {
	sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function getCurrentUser() {
	const json = sessionStorage.getItem(USER_KEY);
	return json ? JSON.parse(json) : null;
}

export function setCurrentUser(user) {
	sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function isLoggedIn() {
	return getAccessToken() !== null;
}

// Only decides what the UI shows. The backend checks the role on every request anyway.
export function isAdmin() {
	const user = getCurrentUser();
	return user !== null && Array.isArray(user.roles) && user.roles.includes('ADMIN');
}

export function clearSession() {
	sessionStorage.removeItem(ACCESS_TOKEN_KEY);
	sessionStorage.removeItem(USER_KEY);
}

// Sends the user to the login page and remembers where to come back to.
export function goToLogin() {
	const here = location.pathname.split('/').pop() + location.search;
	location.href = 'login.html?next=' + encodeURIComponent(here);
}

// Only allow "next" to be one of our own pages (never another website).
export function safeNextPage(next) {
	if (next && /^[a-z-]+\.html(\?[\w=&%.-]*)?$/i.test(next)) {
		return next;
	}
	return 'index.html';
}
