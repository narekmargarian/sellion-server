// --- Навигация ---
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

// --- Просмотр Заказа ---
function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return;

    const info = document.getElementById('order-info');
    const body = document.getElementById('order-items-body');
    const footer = document.getElementById('order-footer-actions');

    const dateFormatted = order.createdAt ? new Date(order.createdAt).toLocaleString() : '---';
    const separateInvoice = order.needsSeparateInvoice ? "ДА" : "НЕТ";

    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
        <div><small>Менеджер:</small><br><b>${order.managerId || '---'}</b></div>
        <div><small>Дата:</small><br><b>${dateFormatted}</b></div>
        <div><small>Оплата:</small><br><b>${order.paymentMethod || 'Не указано'}</b></div>
        <div><small>Отд. фактура:</small><br><b>${separateInvoice}</b></div>
        <div><small>Статус:</small><br><span class="badge">${order.status}</span></div>
    `;

    body.innerHTML = '';
    if (order.items) {
        for (let productName in order.items) {
            let qty = order.items[productName];
            const productInfo = productsData.find(p => p.name === productName);
            const price = productInfo ? productInfo.price : 0;
            const category = productInfo ? productInfo.category : '---';
            const total = (price * qty).toLocaleString();

            body.innerHTML += `
                <tr>
                    <td>${productName}</td>
                    <td>${qty} шт.</td>
                    <td>${price} ֏</td>
                    <td>${total} ֏</td>
                    <td>${category}</td>
                </tr>`;
        }
    }

    document.getElementById('order-total-price').innerText = "Общий итог: " + (order.totalAmount || 0) + " ֏";

    if (order.status === 'INVOICED' || order.invoiceId != null) {
        footer.innerHTML = `
            <div style="color:#ef4444; font-weight:700; background:#fee2e2; padding:10px; border-radius:8px; flex:1">
                <i class="bi bi-lock-fill"></i> ФАКТУРА СОЗДАНА. ИЗМЕНЕНИЕ ЗАПРЕЩЕНО.
            </div>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        footer.innerHTML = `
            <button class="btn-primary" onclick="editOrder(${order.id})">Изменить заказ</button>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
        `;
    }
    openModal('modal-order-view');
}

// --- Просмотр Возврата ---
function openReturnDetails(id) {
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;

    const info = document.getElementById('order-info');
    const body = document.getElementById('order-items-body');
    const footer = document.getElementById('order-footer-actions');

    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
        <div><small>Менеджер:</small><br><b>${ret.managerId || '---'}</b></div>
        <div><small>Причина:</small><br><b>${ret.returnReason}</b></div>
        <div><small>Дата:</small><br><b>${ret.returnDate}</b></div>
        <div><small>Статус:</small><br><span class="badge" style="color:red">${ret.status}</span></div>
    `;

    body.innerHTML = '';
    if (ret.items) {
        for (let productName in ret.items) {
            let qty = ret.items[productName];
            const productInfo = productsData.find(p => p.name === productName);
            const price = productInfo ? productInfo.price : 0;
            const category = productInfo ? productInfo.category : '---';
            const total = (price * qty).toLocaleString();

            body.innerHTML += `
                <tr>
                    <td>${productName}</td>
                    <td>${qty} шт.</td>
                    <td>${price} ֏</td>
                    <td>${total} ֏</td>
                    <td>${category}</td>
                </tr>`;
        }
    }

    document.getElementById('order-total-price').innerText = "Сумма возврата: " + (ret.totalAmount || 0) + " ֏";
    footer.innerHTML = `<button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    openModal('modal-order-view');
}

// --- Редактирование Заказа ---
function editOrder(orderId) {
    const order = ordersData.find(o => o.id == orderId);
    if (!order) return;

    closeModal('modal-order-view');
    document.getElementById('edit-order-id').value = order.id;
    const list = document.getElementById('edit-items-list');
    list.innerHTML = '';

    for (let name in order.items) {
        list.innerHTML += `
            <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px; border-bottom: 1px solid #eee;">
                <span style="flex:2; font-weight:600;">${name}</span>
                <input type="number" class="qty-input" data-name="${name}" value="${order.items[name]}" 
                       style="width:80px; padding:5px; border-radius:6px; border:1px solid #ddd;">
            </div>`;
    }
    openModal('modal-order-edit');
}

async function saveOrderChanges() {
    const id = document.getElementById('edit-order-id').value;
    const inputs = document.querySelectorAll('.qty-input');
    const items = {};
    inputs.forEach(i => items[i.dataset.name] = parseInt(i.value));

    try {
        const response = await fetch(`/api/admin/orders/${id}/edit`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ items: items })
        });
        if (response.ok) { location.reload(); }
        else { alert("Ошибка сохранения."); }
    } catch (e) { alert("Ошибка сети."); }
}

// --- Редактирование Товара (Склад) ---
function editProduct(productId) {
    const product = productsData.find(p => p.id == productId);
    if (!product) return;

    document.getElementById('edit-product-id').value = product.id;
    document.getElementById('edit-product-name').value = product.name;
    document.getElementById('edit-product-price').value = product.price;
    document.getElementById('edit-product-box').value = product.itemsPerBox;
    document.getElementById('edit-product-barcode').value = product.barcode;
    document.getElementById('edit-product-stock').value = product.stockQuantity;

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

    try {
        const response = await fetch(`/api/products/${id}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });
        if (response.ok) { alert("Товар обновлен!"); location.reload(); }
        else { alert("Ошибка при обновлении товара."); }
    } catch (e) { alert("Ошибка сети."); }
}

window.onload = function() {
    const urlParams = new URLSearchParams(window.location.search);
    const serverTab = urlParams.get('activeTab');
    const savedTab = localStorage.getItem('sellion_tab');
    showTab(serverTab || savedTab || 'tab-orders');
}
