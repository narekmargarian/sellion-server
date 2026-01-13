let tempItems = {}; // Для хранения состава (название: количество)

// --- 1. Навигация ---
function showTab(tabId) {
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    const target = document.getElementById(tabId);
    if (target) target.classList.add('active');
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const btnId = tabId.replace('tab-', 'btn-');
    if (document.getElementById(btnId)) document.getElementById(btnId).classList.add('active');
    localStorage.setItem('sellion_tab', tabId);
}

function openModal(id) {
    document.getElementById(id).classList.add('active');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

// Исправление даты (убирает [object Object])
function formatFullDate(dateSource) {
    if (!dateSource) return '---';
    const date = new Date(dateSource);
    return isNaN(date.getTime()) ? dateSource : date.toLocaleString('ru-RU').substring(0, 16);
}

// --- 2. Логика состава заказа ---
function updateTempQty(name, val) {
    let qty = parseInt(val) || 0;
    tempItems[name] = qty < 0 ? 0 : qty; // Не позволяем вводить меньше 0
}

function addItemToEdit() {
    const name = document.getElementById('add-item-select').value;
    const qty = parseInt(document.getElementById('add-item-qty').value) || 1;
    tempItems[name] = (tempItems[name] || 0) + qty;
    renderItemsTable(tempItems, true);
}

function removeItemFromEdit(name) {
    delete tempItems[name];
    renderItemsTable(tempItems, true);
}

function renderItemsTable(itemsMap, isEdit) {
    const body = document.getElementById('order-items-body');
    body.innerHTML = '';

    Object.entries(itemsMap).forEach(([name, qty]) => {
        const pInfo = productsData.find(p => p.name === name);
        const price = pInfo ? pInfo.price : 0;
        const total = price * qty;

        let qtyDisplay = isEdit ?
            `<input type="number" class="qty-input-active" onchange="updateTempQty('${name}', this.value)" value="${qty}">` :
            `<b>${qty} шт.</b>`;

        body.innerHTML += `
            <tr>
                <td>${name} ${isEdit ? `<button onclick="removeItemFromEdit('${name}')" style="color:#ef4444; border:none; background:none; cursor:pointer; margin-left:10px;">&times;</button>` : ''}</td>
                <td>${qtyDisplay}</td>
                <td>${price.toLocaleString()} ֏</td>
                <td style="font-weight:700;">${total.toLocaleString()} ֏</td>
                <td>${pInfo ? pInfo.category : '---'}</td>
            </tr>`;


        // Расчет итога
        let totalSum = Object.entries(itemsMap).reduce((sum, [name, qty]) => {
            const p = productsData.find(p => p.name === name);
            return sum + (p ? p.price * qty : 0);
        }, 0);

        const totalDisplay = document.getElementById('order-total-price');
        if (isEdit) {
            totalDisplay.innerText = `Итого (редактирование): ${totalSum.toLocaleString()} ֏`;
        } else {
            totalDisplay.innerText = `Итого: ${totalSum.toLocaleString()} ֏`;
        }
    });

    if (isEdit) {
        let options = productsData.map(p => `<option value="${p.name}">${p.name} (${p.price} ֏)</option>`).join('');
        body.innerHTML += `
            <tr style="background:#f8fafc">
                <td><select id="add-item-select">${options}</select></td>
                <td><input type="number" id="add-item-qty" value="1"></td>
                <td colspan="3"><button class="btn-primary" onclick="addItemToEdit()">+ Добавить</button></td>
            </tr>`;
    }
}

// --- 3. Основные функции карточки ---
function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return;
    tempItems = JSON.parse(JSON.stringify(order.items));

    const info = document.getElementById('order-info');
    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
<!--        <div><small>Дата заказа:</small><br><b>${formatFullDate(order.createdAt)}</b></div>-->
        <div><small>Дата заказа:</small><br><b>${order.createdAt}</b></div>
        <div><small>Доставка:</small><br><b>${order.deliveryDate || '---'}</b></div>
        <div><small>Оплата:</small><br><b>${order.paymentMethod || 'Наличные'}</b></div>
        <div><small>Фактура:</small><br><b>${order.needsSeparateInvoice ? 'ДА' : 'НЕТ'}</b></div>
    `;

    renderItemsTable(tempItems, false);
    document.getElementById('order-total-price').innerText = "Итого: " + (order.totalAmount || 0).toLocaleString() + " ֏";

    const footer = document.getElementById('order-footer-actions');
    if (order.status === 'INVOICED' || order.invoiceId != null) {
        footer.innerHTML = `<div style="color:#991b1b; font-weight:700; background:#fee2e2; padding:10px; border-radius:8px; flex:1; text-align:center;">СЧЕТ ВЫСТАВЛЕН — БЛОКИРОВКА</div>
                            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        footer.innerHTML = `<button class="btn-primary" onclick="enableOrderEdit(${order.id})">Изменить данные</button>
                            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    }
    openModal('modal-order-view');
}

function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    const info = document.getElementById('order-info');

    let clientOptions = clientsData.map(c =>
        `<option value="${c.name}" ${c.name === order.shopName ? 'selected' : ''}>${c.name}</option>`
    ).join('');

    // Значения по умолчанию подставляются автоматически из объекта order
    info.innerHTML = `
        <div><label>Магазин</label><select id="edit-shop">${clientOptions}</select></div>
        <div><label>Доставка</label><input type="date" id="edit-delivery" value="${order.deliveryDate || ''}"></div>
        <div><label>Оплата</label>
            <select id="edit-payment">
                <option value="Наличные" ${order.paymentMethod === 'Наличные' ? 'selected' : ''}>Наличные</option>
                <option value="Перевод" ${order.paymentMethod === 'Перевод' ? 'selected' : ''}>Перевод</option>
                <option value="Терминал" ${order.paymentMethod === 'Терминал' ? 'selected' : ''}>Терминал</option>
            </select>
        </div>
        <div><label>Отд. Фактура</label>
            <select id="edit-invoice-type">
                <option value="false" ${!order.needsSeparateInvoice ? 'selected' : ''}>НЕТ</option>
                <option value="true" ${order.needsSeparateInvoice ? 'selected' : ''}>ДА</option>
            </select>
        </div>
    `;

    renderItemsTable(tempItems, true);
    document.getElementById('order-total-price').innerText = "Режим редактирования";
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveFullChanges(${id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openOrderDetails(${id})">Отмена</button>`;
}

async function saveFullChanges(id) {
    const data = {
        shopName: document.getElementById('edit-shop').value,
        deliveryDate: document.getElementById('edit-delivery').value,
        paymentMethod: document.getElementById('edit-payment').value,
        needsSeparateInvoice: document.getElementById('edit-invoice-type').value === "true",
        items: tempItems
    };

    try {
        const response = await fetch(`/api/admin/orders/${id}/full-edit`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });

        if (response.ok) {
            localStorage.setItem('sellion_tab', 'tab-orders');
            location.reload();
        } else {
            const err = await response.json();
            alert(err.error || "Ошибка сохранения");
        }
    } catch (e) {
        alert("Ошибка сети");
    }
}

// --- 4. Склад и Инициализация ---
function editProduct(productId) {
    const p = productsData.find(p => p.id == productId);
    if (!p) return;
    document.getElementById('edit-product-id').value = p.id;
    document.getElementById('edit-product-name').value = p.name;
    document.getElementById('edit-product-price').value = p.price;
    document.getElementById('edit-product-stock').value = p.stockQuantity;
    openModal('modal-product-edit');
}

async function saveProductChanges() {
    const id = document.getElementById('edit-product-id').value;
    const data = {
        name: document.getElementById('edit-product-name').value,
        price: parseFloat(document.getElementById('edit-product-price').value),
        stockQuantity: parseInt(document.getElementById('edit-product-stock').value)
    };
    try {

        const response = await fetch(`/api/products/${id}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });
        if (response.ok) {
            localStorage.setItem('sellion_tab', 'tab-products');
            location.reload();
        }

    } catch (e) {
        alert("Ошибка сети");
    }

}

window.onload = function () {
    const urlParams = new URLSearchParams(window.location.search);
    const serverTab = urlParams.get('activeTab');
    const savedTab = localStorage.getItem('sellion_tab');
    showTab(serverTab || savedTab || 'tab-orders');
}
