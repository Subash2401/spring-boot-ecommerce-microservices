// Admin: create, edit and delete products.
// The page is only SHOWN to admins; the backend checks the ADMIN role on every write anyway
// (a normal user calling these endpoints gets 403).
//   create: POST /products        { name, description, price, quantity }
//   edit:   PUT /products/{id}    (same body; 409 if the product changed in the meantime)
//   delete: DELETE /products/{id}

import { api, ApiError } from '../api.js';
import { isAdmin } from '../auth.js';
import { requireLogin } from '../session.js';
import {
	el, formatMoney, renderNavbar, showAlert, clearAlert, showLoading, showEmpty,
	renderPager, confirmDialog, withBusyButton,
} from '../ui.js';

const message = document.getElementById('message');
const form = document.getElementById('product-form');
const formTitle = document.getElementById('form-title');
const saveButton = document.getElementById('save');
const cancelEditButton = document.getElementById('cancel-edit');
const list = document.getElementById('products');
const pager = document.getElementById('pager');
const fields = {
	name: document.getElementById('name'),
	description: document.getElementById('description'),
	price: document.getElementById('price'),
	quantity: document.getElementById('quantity'),
};

const PER_PAGE = 20;
let page = 0;
let editingId = null; // null = the form creates a new product

async function loadProducts() {
	showLoading(list, 'Loading products...');
	try {
		const data = await api('/products?page=' + page + '&size=' + PER_PAGE, { auth: false });
		if (data.content.length === 0) {
			showEmpty(list, 'No products yet. Create the first one above.');
		} else {
			list.replaceChildren(productTable(data.content));
		}
		renderPager(pager, data.page, (newPage) => {
			page = newPage;
			loadProducts();
		});
	} catch (error) {
		list.replaceChildren();
		showAlert(message, error instanceof ApiError ? error.message : 'Could not load the products.');
	}
}

function productTable(products) {
	const rows = products.map((product) => el('tr', {}, [
		el('td', { text: product.name }),
		el('td', { class: 'text-end', text: formatMoney(product.price) }),
		el('td', { class: 'text-end', text: String(product.quantity) }),
		el('td', { class: 'text-end' }, [
			el('div', { class: 'd-flex justify-content-end gap-2' }, [
				el('button', { class: 'btn btn-sm btn-outline-primary', text: 'Edit', on: { click: () => startEdit(product) } }),
				el('button', { class: 'btn btn-sm btn-outline-danger', text: 'Delete', on: { click: () => deleteProduct(product) } }),
			]),
		]),
	]));
	return el('div', { class: 'card shadow-sm' }, [
		el('div', { class: 'table-responsive' }, [
			el('table', { class: 'table align-middle mb-0' }, [
				el('thead', {}, [el('tr', {}, [
					el('th', { text: 'Name' }), el('th', { class: 'text-end', text: 'Price' }),
					el('th', { class: 'text-end', text: 'Stock' }), el('th'),
				])]),
				el('tbody', {}, rows),
			]),
		]),
	]);
}

// Fills the form with a product; "Save" then sends PUT instead of POST.
function startEdit(product) {
	editingId = product.id;
	fields.name.value = product.name;
	fields.description.value = product.description || '';
	fields.price.value = product.price;
	fields.quantity.value = product.quantity;
	formTitle.textContent = 'Edit product';
	saveButton.textContent = 'Save changes';
	cancelEditButton.classList.remove('d-none');
	window.scrollTo({ top: 0, behavior: 'smooth' });
}

function resetForm() {
	editingId = null;
	form.reset();
	formTitle.textContent = 'New product';
	saveButton.textContent = 'Create product';
	cancelEditButton.classList.add('d-none');
}

// Checks the form the same way the backend does. Returns the request body or an error text.
function readForm() {
	const name = fields.name.value.trim();
	const description = fields.description.value.trim();
	const price = Number(fields.price.value);
	const quantity = Number(fields.quantity.value);
	if (!name || name.length > 100) {
		return { error: 'The name is required (at most 100 characters).' };
	}
	if (description.length > 1000) {
		return { error: 'The description can be at most 1000 characters.' };
	}
	if (!(price > 0)) {
		return { error: 'The price must be more than 0.' };
	}
	if (!Number.isInteger(quantity) || quantity < 0) {
		return { error: 'The stock must be a whole number, 0 or more.' };
	}
	return { body: { name, description, price, quantity } };
}

form.addEventListener('submit', async (event) => {
	event.preventDefault();
	clearAlert(message);
	const { body, error } = readForm();
	if (error) {
		showAlert(message, error);
		return;
	}
	await withBusyButton(saveButton, async () => {
		try {
			if (editingId) {
				await api('/products/' + encodeURIComponent(editingId), { method: 'PUT', body });
				showAlert(message, 'The product was saved.', 'success');
			} else {
				await api('/products', { method: 'POST', body });
				showAlert(message, 'The product was created.', 'success');
			}
			resetForm();
			loadProducts();
		} catch (apiError) {
			// 400 field errors, 403 not admin, 404 deleted meanwhile, 409 changed meanwhile
			showAlert(message, apiError instanceof ApiError ? apiError.message : 'Could not save the product.');
		}
	});
});

cancelEditButton.addEventListener('click', resetForm);

async function deleteProduct(product) {
	const sure = await confirmDialog({
		title: 'Delete ' + product.name + '?',
		message: 'The product will be removed from the shop. This cannot be undone.',
		confirmText: 'Delete',
		danger: true,
	});
	if (!sure) {
		return;
	}
	clearAlert(message);
	try {
		await api('/products/' + encodeURIComponent(product.id), { method: 'DELETE' });
		showAlert(message, product.name + ' was deleted.', 'success');
		if (editingId === product.id) {
			resetForm();
		}
		loadProducts();
	} catch (error) {
		showAlert(message, error instanceof ApiError ? error.message : 'Could not delete the product.');
	}
}

// Start
if (await requireLogin()) {
	renderNavbar('admin-products.html');
	if (isAdmin()) {
		document.getElementById('admin-area').classList.remove('d-none');
		loadProducts();
	} else {
		showAlert(message, 'This page is for admins only.', 'warning');
	}
}
