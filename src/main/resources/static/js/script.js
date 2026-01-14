let tempItems = {};

// --- 1. Навигация и Утилиты ---

function showTab(tabId) {
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    const target = document.getElementById(tabId);
    if (target) {
        target.classList.add('active');
        // Сбрасываем скролл таблицы во вкладке при переключении
        const tableContainer = target.querySelector('.table-container');
        if (tableContainer) tableContainer.scrollTop = 0;
    }

    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const btnId = tabId.replace('tab-', 'btn-');
    if (document.getElementById(btnId)) document.getElementById(btnId).classList.add('active');

    localStorage.setItem('sellion_tab', tabId);
}

function openModal(id) {
    const modal = document.getElementById(id);
    modal.classList.add('active');
    // Сброс скролла внутреннего контейнера при открытии
    const sc = modal.querySelector('#table-scroll-container');
    if (sc) sc.scrollTop = 0;
}

function closeModal(id) { document.getElementById(id).classList.remove('active'); }

function formatOrderDate(dateStr) {
    if (!dateStr || dateStr.length < 16) return dateStr;
    return dateStr.replace('T', ' ').substring(0, 16);
}

function showStatus(text, isError = false) {
    const container = document.getElementById('order-footer-actions');
    const modalContent = document.querySelector('.modal-content');
    const old = document.getElementById('status-notify');
    if(old) old.remove();

    const statusDiv = document.createElement('div');
    statusDiv.id = "status-notify";

    if (text.includes("Недостаточно товара")) {
        let cleanMessage = text.split('\n')[0].split(': ').pop();
        statusDiv.className = "stock-error-box";
        statusDiv.innerHTML = `
            <div style="font-size: 20px; margin-bottom: 5px;">⚠️</div>
            <div style="font-weight: 800; text-transform: uppercase;">Ошибка склада</div>
            <div style="font-weight: 600;">${cleanMessage}</div>
        `;
        modalContent.classList.add('shake-it');
        setTimeout(() => modalContent.classList.remove('shake-it'), 500);
    } else {
        statusDiv.style = `color: ${isError ? '#ef4444' : '#10b981'}; font-weight: 700; margin-bottom: 10px; width: 100%; text-align: center;`;
        statusDiv.innerText = text;
    }

    container.prepend(statusDiv);
    setTimeout(() => { if(statusDiv) statusDiv.remove(); }, 6000);
}

function updateRowInTable(order) {
    const row = document.querySelector(`tr[onclick*="openOrderDetails(${order.id})"]`);
    if (row) {
        row.cells[0].innerText = formatOrderDate(order.createdAt);
        row.cells[2].innerText = order.shopName;
        row.cells[3].innerText = (order.totalAmount || 0).toLocaleString() + ' ֏';
        row.cells[4].innerText = order.deliveryDate || '---';
    }
}

// --- 2. Логика состава (с Кнопкой ✅) ---

async function applySingleQty(encodedName) {
    const name = decodeURIComponent(encodedName);
    const input = document.getElementById(`input-qty-${encodedName}`);
    if (!input) return;

    let newVal = parseInt(input.value);
    if (isNaN(newVal) || newVal < 0) {
        newVal = 0;
        input.value = 0;
        showStatus("Количество не может быть отрицательным", true);
    }

    const product = productsData.find(p => p.name === name);
    const row = input.closest('tr');

    if (product && newVal > product.stockQuantity) {
        row.style.backgroundColor = "#fee2e2";
        showStatus(`Недостаточно товара: ${name}. На складе: ${product.stockQuantity}`, true);
        return;
    } else {
        row.style.backgroundColor = "";
    }

    tempItems[name] = newVal;
    let newTotal = calculateCurrentTempTotal();

    document.getElementById('order-total-price').innerText = "Предварительно: " + newTotal.toLocaleString() + " ֏";
    renderItemsTable(tempItems, true);
    showStatus(`Товар "${name}" зафиксирован ✅`);
}

function addItemToEdit() {
    const name = document.getElementById('add-item-select').value;
    const qty = parseInt(document.getElementById('add-item-qty').value) || 1;

    const product = productsData.find(p => p.name === name);
    if (product && qty > product.stockQuantity) {
        showStatus(`Нельзя добавить ${qty} шт. товара "${name}". На складе всего ${product.stockQuantity}`, true);
        return;
    }

    tempItems[name] = (tempItems[name] || 0) + qty;
    renderItemsTable(tempItems, true);
    showStatus(`Товар "${name}" добавлен в список`);
}

function removeItemFromEdit(encodedName) {
    const name = decodeURIComponent(encodedName);
    delete tempItems[name];
    renderItemsTable(tempItems, true);
}

function calculateCurrentTempTotal() {
    let total = 0;
    Object.entries(tempItems).forEach(([pName, pQty]) => {
        const prod = productsData.find(p => p.name === pName);
        if (prod) total += prod.price * pQty;
    });
    return total;
}

// --- 3. Рендеринг таблицы состава ---

function renderItemsTable(itemsMap, isEdit) {
    const container = document.getElementById('table-scroll-container');
    const scrollPos = container ? container.scrollTop : 0;

    const body = document.getElementById('order-items-body');
    body.innerHTML = '';

    Object.entries(itemsMap).forEach(([name, qty]) => {
        const pInfo = productsData.find(p => p.name === name);
        const price = pInfo ? pInfo.price : 0;
        const total = price * qty;
        const encodedName = encodeURIComponent(name);

        let qtyDisplay = isEdit ?
            `<div style="display:flex; align-items:center; gap:5px;">
                <input type="number" id="input-qty-${encodedName}" class="qty-input-active" 
                       value="${qty}" min="0" style="width:65px;">
                <button onclick="applySingleQty('${encodedName}')" style="border:none; background:transparent; cursor:pointer;">✅</button>
            </div>` : `<b>${qty} шт.</b>`;

        body.innerHTML += `<tr>
            <td>${name} ${isEdit ? `<button onclick="removeItemFromEdit('${encodedName}')" style="color:#ef4444; border:none; background:none; cursor:pointer;">&times;</button>` : ''}</td>
            <td>${qtyDisplay}</td>
            <td>${price.toLocaleString()} ֏</td>
            <td style="font-weight:700;">${total.toLocaleString()} ֏</td>
            <td><small>${pInfo ? pInfo.category : '---'}</small></td>
        </tr>`;
    });

    if (isEdit) {
        let options = productsData.map(p => `<option value="${p.name.replace(/"/g, '&quot;')}">${p.name} (${p.price} ֏)</option>`).join('');
        body.innerHTML += `<tr style="background:#f8fafc; position: sticky; bottom: 0;">
            <td><select id="add-item-select" style="width:100%">${options}</select></td>
            <td><input type="number" id="add-item-qty" value="1" min="1" style="width:65px;"></td>
            <td colspan="3"><button class="btn-primary" onclick="addItemToEdit()" style="width:100%">+ Добавить</button></td>
        </tr>`;
    }

    if (container) {
        requestAnimationFrame(() => { container.scrollTop = scrollPos; });
    }
}

// --- 4. Основные функции карточки ---

function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return;
    tempItems = JSON.parse(JSON.stringify(order.items));

    document.getElementById('modal-title').innerHTML = `Детали операции <span class="badge" style="margin-left:10px;">ЗАКАЗ №${order.id}</span>`;

    const info = document.getElementById('order-info');
    info.style.gridTemplateColumns = 'repeat(3, 1fr)';
    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
        <div><small>Дата заказа:</small><br><b>${formatOrderDate(order.createdAt)}</b></div>
        <div><small>Менеджер:</small><br><b>${order.managerId}</b></div>
        <div><small>Доставка:</small><br><b>${order.deliveryDate || '---'}</b></div>
        <div><small>Оплата:</small><br><b>${order.paymentMethod}</b></div>
        <div><small>Фактура:</small><br><b>${order.needsSeparateInvoice ? 'ДА' : 'НЕТ'}</b></div>
    `;

    renderItemsTable(tempItems, false);
    document.getElementById('order-total-price').innerText = "Итого: " + (order.totalAmount || 0).toLocaleString() + " ֏";

    const footer = document.getElementById('order-footer-actions');
    footer.innerHTML = (order.invoiceId) ?
        `<div style="color:#991b1b; font-weight:700; background:#fee2e2; padding:10px; border-radius:8px; flex:1; text-align:center;">СЧЕТ ВЫСТАВЛЕН</div>
         <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>` :
        `<button class="btn-primary" onclick="enableOrderEdit(${order.id})">Изменить данные</button>
         <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;

    openModal('modal-order-view');
}

function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    document.getElementById('modal-title').innerText = "Режим редактирования заказа #" + id;

    const info = document.getElementById('order-info');
    info.style.gridTemplateColumns = 'repeat(3, 1fr)';

    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === order.shopName ? 'selected' : ''}>${c.name}</option>`).join('');

    // Используем paymentMethods из контроллера
    let paymentOptions = paymentMethods.map(m => `<option value="${m}" ${order.paymentMethod === m ? 'selected' : ''}>${m}</option>`).join('');

    info.innerHTML = `
        <div><label>Магазин</label><select id="edit-shop">${clientOptions}</select></div>
        <div><label>Доставка</label><input type="text" id="edit-delivery" value="${order.deliveryDate || ''}"></div>
        <div><label>Оплата</label><select id="edit-payment">${paymentOptions}</select></div>
        <div><label>Отд. Фактура</label>
            <select id="edit-invoice-type">
                <option value="false" ${!order.needsSeparateInvoice ? 'selected' : ''}>НЕТ</option>
                <option value="true" ${order.needsSeparateInvoice ? 'selected' : ''}>ДА</option>
            </select>
        </div>`;

    renderItemsTable(tempItems, true);
    document.getElementById('order-total-price').innerText = "Редактирование состава...";
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

        const result = await response.json();

        if (response.ok) {
            const idx = ordersData.findIndex(o => o.id == id);
            if (idx !== -1) {
                ordersData[idx] = { ...ordersData[idx], ...data, totalAmount: result.finalSum };
                updateRowInTable(ordersData[idx]);
            }
            showStatus("✅ Заказ успешно обновлен!");
            setTimeout(() => openOrderDetails(id), 1000);
        } else {
            showStatus(result.error || result.message || "Ошибка сохранения", true);
        }
    } catch (e) {
        showStatus("❌ Ошибка соединения", true);
    }
}

// --- 5. Возвраты ---

function openReturnDetails(id) {
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;
    tempItems = JSON.parse(JSON.stringify(ret.items));

    document.getElementById('modal-title').innerHTML = `Детали операции <span class="badge" style="margin-left:10px;">ВОЗВРАТ №${ret.id}</span>`;

    const info = document.getElementById('order-info');
    info.style.gridTemplateColumns = 'repeat(3, 1fr)';
    info.innerHTML = `
        <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
        <div><small>Дата возврата:</small><br><b>${ret.returnDate}</b></div>
        <div><small>Причина:</small><br><b style="color: #ef4444;">${ret.returnReason}</b></div>
    `;

    renderItemsTable(tempItems, false);
    document.getElementById('order-total-price').innerText = "Сумма возврата: " + (ret.totalAmount || 0).toLocaleString() + " ֏";

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;

    openModal('modal-order-view');
}

function enableReturnEdit(id) {
    const ret = returnsData.find(r => r.id == id);
    document.getElementById('modal-title').innerText = "Редактирование возврата #" + id;

    const info = document.getElementById('order-info');
    let reasonOptions = returnReasons.map(r => `<option value="${r}" ${ret.returnReason === r ? 'selected' : ''}>${r}</option>`).join('');

    info.innerHTML = `
        <div><label>Магазин</label><input type="text" value="${ret.shopName}" disabled></div>
        <div><label>Дата возврата</label><input type="text" id="edit-ret-date" value="${ret.returnDate}"></div>
        <div><label>Причина</label><select id="edit-ret-reason">${reasonOptions}</select></div>`;

    renderItemsTable(tempItems, true);
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveReturnChanges(${id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openReturnDetails(${id})">Отмена</button>`;
}

async function saveReturnChanges(id) {
    const data = {
        shopName: returnsData.find(r => r.id == id).shopName,
        returnDate: document.getElementById('edit-ret-date').value,
        returnReason: document.getElementById('edit-ret-reason').value,
        items: tempItems
    };
    try {
        const response = await fetch(`/api/admin/returns/${id}/edit`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });
        if (response.ok) {
            const result = await response.json();
            const idx = returnsData.findIndex(r => r.id == id);
            if (idx !== -1) {
                returnsData[idx] = { ...returnsData[idx], ...data, totalAmount: result.newTotal };
                updateReturnRowInTable(returnsData[idx]);
            }
            showStatus("✅ Возврат обновлен!");
            setTimeout(() => openReturnDetails(id), 1000);
        }
    } catch (e) { showStatus("❌ Ошибка сети", true); }
}

function updateReturnRowInTable(ret) {
    const row = document.querySelector(`tr[onclick*="openReturnDetails(${ret.id})"]`);
    if (row) {
        row.cells[0].innerText = ret.returnDate;
        row.cells[3].innerText = ret.returnReason;
        row.cells[4].innerText = (ret.totalAmount || 0).toLocaleString() + ' ֏';
    }
}

// --- 6. Инициализация ---

document.addEventListener("DOMContentLoaded", () => {
    showTab(localStorage.getItem('sellion_tab') || 'tab-orders');
});
