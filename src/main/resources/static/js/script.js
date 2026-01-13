// --- Навигация и окна ---
function showTab(tabId) {
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    const target = document.getElementById(tabId);
    if (target) target.classList.add('active');
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const btnId = tabId.replace('tab-', 'btn-');
    if (document.getElementById(btnId)) document.getElementById(btnId).classList.add('active');
    localStorage.setItem('sellion_tab', tabId);
}

function openModal(id) { document.getElementById(id).classList.add('active'); }
function closeModal(id) { document.getElementById(id).classList.remove('active'); }

// --- Вспомогательная функция для корректной даты ---
function formatFullDate(dateSource) {
    if (!dateSource) return '---';
    const date = new Date(dateSource);
    return isNaN(date) ? dateSource : date.toLocaleString('ru-RU');
}

// --- Универсальная отрисовка таблицы товаров ---
function renderItemsTable(data, isEdit, type) {
    const body = document.getElementById('order-items-body');
    body.innerHTML = '';
    if (data.items) {
        for (let name in data.items) {
            let qty = data.items[name];
            const pInfo = productsData.find(p => p.name === name);
            const price = pInfo ? pInfo.price : 0;
            const category = pInfo ? pInfo.category : '---';
            const total = price * qty;

            let qtyDisplay = isEdit ?
                `<input type="number" class="qty-input-active" data-name="${name}" value="${qty}" style="width:70px; padding:4px; border:1px solid var(--accent); border-radius:4px;">` :
                `<b>${qty} шт.</b>`;

            body.innerHTML += `
                <tr>
                    <td>${name}</td>
                    <td>${qtyDisplay}</td>
                    <td>${price} ֏</td>
                    <td>${total.toLocaleString()} ֏</td>
                    <td>${category}</td>
                </tr>`;
        }
    }
}

// --- ЛОГИКА ЗАКАЗОВ ---
function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return;

    const info = document.getElementById('order-info');
    const footer = document.getElementById('order-footer-actions');

    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
        <div><small>Менеджер:</small><br><b>${order.managerId || '---'}</b></div>
        <div><small>Дата заказа:</small><br><b>${formatFullDate(order.createdAt)}</b></div>
        <div><small>Дата доставки:</small><br><b>${order.deliveryDate || '---'}</b></div>
        <div><small>Оплата:</small><br><b>${order.paymentMethod || '---'}</b></div>
        <div><small>Отд. фактура:</small><br><b>${order.needsSeparateInvoice ? 'ДА' : 'НЕТ'}</b></div>
    `;

    renderItemsTable(order, false, 'order');
    document.getElementById('order-total-price').innerText = "Итого: " + (order.totalAmount || 0) + " ֏";

    if (order.status === 'INVOICED' || order.invoiceId != null) {
        footer.innerHTML = `<div style="color:red; font-weight:700; flex:1; background:#fee2e2; padding:8px; border-radius:8px;">СЧЕТ ВЫСТАВЛЕН — БЛОКИРОВКА</div>
                            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        footer.innerHTML = `<button class="btn-primary" onclick="enableOrderEdit(${order.id})">Изменить состав</button>
                            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    }
    openModal('modal-order-view');
}

function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    renderItemsTable(order, true, 'order');
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveChanges(${id}, 'order')">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openOrderDetails(${id})">Отмена</button>`;
}

// --- ЛОГИКА ВОЗВРАТОВ ---
function openReturnDetails(id) {
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;

    const info = document.getElementById('order-info');
    const footer = document.getElementById('order-footer-actions');

    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
        <div><small>Менеджер:</small><br><b>${ret.managerId || '---'}</b></div>
        <div><small>Причина:</small><br><b>${ret.returnReason}</b></div>
        <div><small>Дата возврата:</small><br><b>${ret.returnDate || '---'}</b></div>
        <div><small>Статус:</small><br><span class="badge" style="color:red">${ret.status}</span></div>
    `;

    renderItemsTable(ret, false, 'return');
    document.getElementById('order-total-price').innerText = "Сумма возврата: " + (ret.totalAmount || 0) + " ֏";

    footer.innerHTML = `
        <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
    `;
    openModal('modal-order-view');
}

function enableReturnEdit(id) {
    const ret = returnsData.find(r => r.id == id);
    renderItemsTable(ret, true, 'return');
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveChanges(${id}, 'return')">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openReturnDetails(${id})">Отмена</button>`;
}

// --- УНИВЕРСАЛЬНОЕ СОХРАНЕНИЕ (Заказ или Возврат) ---
async function saveChanges(id, type) {
    const inputs = document.querySelectorAll('.qty-input-active');
    const items = {};
    inputs.forEach(i => items[i.dataset.name] = parseInt(i.value));

    const url = type === 'order' ? `/api/admin/orders/${id}/edit` : `/api/returns/${id}/edit`;

    try {
        const response = await fetch(url, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ items: items })
        });
        if (response.ok) { location.reload(); }
        else { const result = await response.json();
            alert(result.error || result.message || "Ошибка системы");


        }
    } catch (e) { alert("Ошибка сети!"); }
}

// --- Остальные функции (Склад, Загрузка) ---
function editProduct(productId) {
    const p = productsData.find(p => p.id == productId);
    if (!p) return;
    document.getElementById('edit-product-id').value = p.id;
    document.getElementById('edit-product-name').value = p.name;
    document.getElementById('edit-product-price').value = p.price;
    document.getElementById('edit-product-box').value = p.itemsPerBox;
    document.getElementById('edit-product-barcode').value = p.barcode;
    document.getElementById('edit-product-stock').value = p.stockQuantity;
    openModal('modal-product-edit');
}

async function saveProductChanges() {
    const id = document.getElementById('edit-product-id').value;
    const data = {
        name: document.getElementById('edit-product-name').value,
        price: parseFloat(document.getElementById('edit-product-price').value),
        itemsPerBox: parseInt(document.getElementById('edit-product-box').value),
        barcode: document.getElementById('edit-product-barcode').value,
        stockQuantity: parseInt(document.getElementById('edit-product-stock').value)
    };
    const response = await fetch(`/api/products/${id}`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(data)
    });
    if (response.ok) { location.reload(); } else { alert("Ошибка!"); }
}

window.onload = function() {
    const urlParams = new URLSearchParams(window.location.search);
    const serverTab = urlParams.get('activeTab');
    const savedTab = localStorage.getItem('sellion_tab');
    showTab(serverTab || savedTab || 'tab-orders');


}
