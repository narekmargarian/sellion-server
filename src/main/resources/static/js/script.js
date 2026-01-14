let tempItems = {};

// --- 1. Навигация и Утилиты ---
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
        // Берем текст только после двоеточия и до первой технической детали (at ...)
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

// Функция для мгновенного обновления одного товара и пересчета суммы
async function applySingleQty(name) {
    const input = document.getElementById(`input-qty-${name}`);
    let newVal = parseInt(input.value);

    // Если ввели минус или не число — сбрасываем в 0
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

    // Пересчет общей суммы в интерфейсе
    let newTotal = 0;
    Object.entries(tempItems).forEach(([pName, pQty]) => {
        const prod = productsData.find(p => p.name === pName);
        if (prod) newTotal += prod.price * pQty;
    });

    document.getElementById('order-total-price').innerText = "Предварительно: " + newTotal.toLocaleString() + " ֏";
    renderItemsTable(tempItems, true);
    showStatus(`Товар зафиксирован ✅`);
}


function addItemToEdit() {
    const name = document.getElementById('add-item-select').value;
    const qty = parseInt(document.getElementById('add-item-qty').value) || 1;

    // Проверка склада перед добавлением в список
    const product = productsData.find(p => p.name === name);
    if (product && qty > product.stockQuantity) {
        showStatus(`Нельзя добавить ${qty} шт. товара "${name}". На складе всего ${product.stockQuantity}`, true);
        return; // Блокируем добавление
    }

    tempItems[name] = (tempItems[name] || 0) + qty;
    renderItemsTable(tempItems, true);
    showStatus(`Товар "${name}" добавлен в список`);
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
            `<div style="display:flex; align-items:center; gap:5px;">
                <input type="number" id="input-qty-${name}" class="qty-input-active" 
                       value="${qty}" min="0" oninput="if(this.value<0)this.value=0" style="width:60px;">
                <button onclick="applySingleQty('${name}')" style="border:none; background:none; cursor:pointer; font-size:16px;">✅</button>
            </div>` : `<b>${qty} шт.</b>`;

        body.innerHTML += `<tr>
            <td>${name} ${isEdit ? `<button onclick="removeItemFromEdit('${name}')" style="color:#ef4444; border:none; background:none; cursor:pointer;">&times;</button>` : ''}</td>
            <td>${qtyDisplay}</td>
            <td>${price.toLocaleString()} ֏</td>
            <td style="font-weight:700;">${total.toLocaleString()} ֏</td>
            <td>${pInfo ? pInfo.category : '---'}</td>
        </tr>`;
    });
    if (isEdit) {
        let options = productsData.map(p => `<option value="${p.name}">${p.name} (${p.price} ֏)</option>`).join('');
        body.innerHTML += `<tr style="background:#f8fafc">
            <td><select id="add-item-select">${options}</select></td>
            <!-- Защита для поля добавления нового товара -->
            <td><input type="number" id="add-item-qty" value="1" min="1" oninput="if(this.value<1)this.value=1"></td>
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
        <div style="margin-bottom: 15px;">
            <span style="background: #eef2ff; color: var(--accent); padding: 4px 12px; border-radius: 6px; font-weight: 800; font-size: 12px; border: 1px solid #e0e7ff;">
                ДОКУМЕНТ: ЗАКАЗ №${order.id}
            </span>
        </div>
        <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
        <div><small>Дата заказа:</small><br><b>${formatOrderDate(order.createdAt)}</b></div>
        <div><small>Доставка:</small><br><b>${order.deliveryDate || '---'}</b></div>
        <div><small>Оплата:</small><br><b>${order.paymentMethod || 'Наличный'}</b></div>
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
    const info = document.getElementById('order-info');

    // Конвертер даты "15 января 2026" -> "2026-01-15"
    let dForInp = "";
    if (order.deliveryDate && order.deliveryDate.includes(' ')) {
        const ms = {"января":"01","февраля":"02","марта":"03","апреля":"04","мая":"05","июня":"06","июля":"07","августа":"08","сентября":"09","октября":"10","ноября":"11","декабря":"12"};
        const p = order.deliveryDate.split(' ');
        if(p.length >= 3) dForInp = `${p[2]}-${ms[p[1].toLowerCase()]}-${p[0].padStart(2, '0')}`;
    } else { dForInp = order.deliveryDate || ''; }

    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === order.shopName ? 'selected' : ''}>${c.name}</option>`).join('');

    info.innerHTML = `
        <div><label>Магазин</label><select id="edit-shop">${clientOptions}</select></div>
        <div><label>Доставка</label><input type="date" id="edit-delivery" value="${dForInp}"></div>
        <div><label>Оплата</label>
            <select id="edit-payment">
                <option value="Наличный" ${order.paymentMethod === 'Наличный' ? 'selected' : ''}>Наличный</option>
                <option value="Перевод" ${order.paymentMethod === 'Перевод' ? 'selected' : ''}>Перевод</option>
            </select>
        </div>
        <div><label>Отд. Фактура</label>
            <select id="edit-invoice-type">
                <option value="false" ${!order.needsSeparateInvoice ? 'selected' : ''}>НЕТ</option>
                <option value="true" ${order.needsSeparateInvoice ? 'selected' : ''}>ДА</option>
            </select>
        </div>`;

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

        // 1. Сначала проверяем, не пустой ли ответ
        const text = await response.text();
        let result;
        try {
            result = JSON.parse(text); // Пытаемся превратить текст в объект
        } catch (e) {
            // Если сервер прислал не JSON (а просто текст ошибки)
            showStatus(text || "Ошибка сервера", true);
            return;
        }

        if (response.ok) {
            const orderIdx = ordersData.findIndex(o => o.id == id);
            if (orderIdx !== -1) {
                ordersData[orderIdx] = {
                    ...ordersData[orderIdx],
                    ...data,
                    items: JSON.parse(JSON.stringify(tempItems)),
                    totalAmount: result.finalSum || result.newTotal
                };
                updateRowInTable(ordersData[orderIdx]);
            }
            showStatus("✅ Заказ успешно обновлен!");
            setTimeout(() => openOrderDetails(id), 1000);
        } else {
            // 2. Если сервер прислал ошибку (StockService), передаем её в чистильщик
            showStatus(result.error || result.message || text, true);
        }
    } catch (e) {
        // 3. Сюда попадем только если ВООБЩЕ нет связи (сервер выключен)
        console.error(e);
        showStatus("❌ Ошибка соединения или критический сбой", true);
    }
}

// --- ВОЗВРАТЫ ---

function openReturnDetails(id) {
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;
    tempItems = JSON.parse(JSON.stringify(ret.items));

    const info = document.getElementById('order-info');
    info.innerHTML = `
        <div style="margin-bottom: 15px;">
            <span class="badge" style="background: #fff1f2; color: #991b1b; border-color: #fecdd3; padding: 4px 12px; border-radius: 6px; font-weight: 800; font-size: 12px;">
                ДОКУМЕНТ: ВОЗВРАТ №${ret.id}
            </span>
        </div>
        <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
        <div><small>Дата возврата:</small><br><b>${ret.returnDate}</b></div>
        <div><small>Причина:</small><br><b style="color: #ef4444;">${ret.returnReason || 'Не указана'}</b></div>
    `;

    renderItemsTable(tempItems, false);
    document.getElementById('order-total-price').innerText = "Сумма возврата: " + (ret.totalAmount || 0).toLocaleString() + " ֏";
    document.getElementById('order-total-price').className = "price-down";

    const footer = document.getElementById('order-footer-actions');
    footer.innerHTML = `
        <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;

    openModal('modal-order-view');
}

function enableReturnEdit(id) {
    const ret = returnsData.find(r => r.id == id);
    const info = document.getElementById('order-info');
    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === ret.shopName ? 'selected' : ''}>${c.name}</option>`).join('');

    info.innerHTML = `
        <div><label>Магазин</label><select id="edit-ret-shop">${clientOptions}</select></div>
        <div><label>Дата возврата</label><input type="text" id="edit-ret-date" value="${ret.returnDate}"></div>
        <div><label>Причина возврата</label>
            <select id="edit-ret-reason">
                <option value="Брак" ${ret.returnReason === 'Брак' ? 'selected' : ''}>Брак</option>
                <option value="Просрочка" ${ret.returnReason === 'Просрочка' ? 'selected' : ''}>Просрочка</option>
                <option value="Ошибка заказа" ${ret.returnReason === 'Ошибка заказа' ? 'selected' : ''}>Ошибка заказа</option>
            </select>
        </div>`;

    renderItemsTable(tempItems, true);
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveReturnChanges(${id})">Сохранить возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="openReturnDetails(${id})">Отмена</button>`;
}

async function saveReturnChanges(id) {
    const data = {
        shopName: document.getElementById('edit-ret-shop').value,
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
                returnsData[idx] = { ...returnsData[idx], ...data, items: JSON.parse(JSON.stringify(tempItems)), totalAmount: result.newTotal };
                updateReturnRowInTable(returnsData[idx]);
            }
            showStatus("✅ Возврат успешно обновлен!");
            setTimeout(() => openReturnDetails(id), 1000);
        } else {
            const err = await response.json();
            showStatus(err.error || "Ошибка сохранения", true);
        }
    } catch (e) { showStatus("❌ Ошибка сети", true); }
}

function updateReturnRowInTable(ret) {
    const row = document.querySelector(`tr[onclick*="openReturnDetails(${ret.id})"]`);
    if (row) {
        row.cells[0].innerText = ret.returnDate;
        row.cells[2].innerText = ret.shopName;
        row.cells[3].innerText = ret.returnReason;
        row.cells[4].innerText = (ret.totalAmount || 0).toLocaleString() + ' ֏';
    }
}

document.addEventListener("DOMContentLoaded", () => {
    showTab(localStorage.getItem('sellion_tab') || 'tab-orders');
});
