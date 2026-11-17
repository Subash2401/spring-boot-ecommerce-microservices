// Settings shared by every page.

// Where the API gateway runs. Every request goes through it.
// At deploy time the frontend is served by nginx on the same origin as the API,
// and this becomes "" (same origin).
export const API_BASE = 'http://localhost:8080';

// How many products / orders / notifications one page of a list shows.
// The backend never returns more than 50.
export const PAGE_SIZE = 12;

// Cart limits, the same as the backend's order validation.
export const MAX_CART_LINES = 50;
export const MAX_QUANTITY = 100;
