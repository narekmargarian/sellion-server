let tempItems = {};

// --- 1. Навигация и Утилиты ---
function openModal(id) {
    const modal = document.getElementById(id);
    modal.classList.add('active');
    const sc = modal.querySelector('#table-scroll-container');
    if (sc) sc.scrollTop = 0;
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

// Безопасное форматирование даты (убирает T и секунды)
function formatOrderDate(dateStr) {
    if (!dateStr || typeof dateStr !== 'string') return dateStr;
    let clean = dateStr.replace('T', ' ');
    return clean.length > 16 ? clean.substring(0, 16) : clean;
}

// Утилита для перевода методов оплаты
function translatePayment(m) {
    if (!m) return '';
    const val = (typeof m === 'object') ? (m.name || m) : m;
    const mapping = {
        'CASH': 'Наличный',
        'TRANSFER': 'Перевод'
    };
    return mapping[val] || val;
}

// Утилита для перевода причин возврата
function translateReason(r) {
    if (!r) return '';
    const val = (typeof r === 'object') ? (r.name || r) : r;
    const mapping = {
        'EXPIRED': 'Просрочка',
        'DAMAGED': 'Поврежденная упаковка',
        'WAREHOUSE': 'На склад',
        'OTHER': 'Другое'
    };
    return mapping[val] || val;
}

function showStatus(text, isError = false) {
    const container = document.getElementById('order-footer-actions');
    const modalContent = document.querySelector('.modal-content');
    const old = document.getElementById('status-notify');
    if (old) old.remove();
    const statusDiv = document.createElement('div');
    statusDiv.id = "status-notify";
    if (text.includes("Недостаточно товара")) {
        let cleanMessage = text.split('\n').pop().split(': ').pop();
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
    setTimeout(() => {
        if (statusDiv) statusDiv.remove();
    }, 6000);
}

// Обновление строки заказа в главной таблице
function updateRowInTable(order) {
    const row = document.querySelector(`tr[onclick*="openOrderDetails(${order.id})"]`);
    if (row) {
        row.cells[0].innerText = formatOrderDate(order.createdAt);
        row.cells[2].innerText = order.shopName;
        row.cells[3].innerText = (order.totalAmount || 0).toLocaleString() + ' ֏';
        row.cells[4].innerText = order.deliveryDate || '---';
        const statusText = translatePayment(order.status || order.paymentMethod);
        row.cells[5].innerHTML = `<span class="badge">${statusText}</span>`;
    }
}

// --- 2. Логика состава (общая) ---
function applySingleQty(encodedName) {
    const name = decodeURIComponent(encodedName);
    const input = document.getElementById(`input-qty-${encodedName}`);

    if (!input) return;
    let newVal = parseInt(input.value);
    // 1. Логика удаления при 0 или пустом вводе
    if (isNaN(newVal) || newVal <= 0) {
        delete tempItems[name];
        showStatus(`Товар "${name}" удален`);
    } else {
        // 2. Проверка остатка на складе (стандарт 2026)
        const product = productsData.find(p => p.name === name);
        if (product && newVal > product.stockQuantity) {
            showStatus(`Недостаточно товара! Доступно: ${product.stockQuantity}`, true);
            input.value = product.stockQuantity; // Сбрасываем до максимума
            tempItems[name] = product.stockQuantity;
        } else {
            tempItems[name] = newVal;
            showStatus(`Товар "${name}" обновлен ✅`);
        }
    }
    // 3. Перерисовываем таблицу
    renderItemsTable(tempItems, true);

    // 4. Обновляем сумму (используем вашу функцию расчета)
    let newTotal = calculateCurrentTempTotal();
    const totalPriceElement = document.getElementById('order-total-price');
    if (totalPriceElement) {
        totalPriceElement.innerText = "Предварительно: " + newTotal.toLocaleString() + " ֏";
    }
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
    // Обновляем общую сумму в модалке при каждом расчете
    const totalPriceElement = document.getElementById('order-total-price');
    if (totalPriceElement) {
        totalPriceElement.innerText = "Предварительно: " + total.toLocaleString() + " ֏";
    }
    return total;
}


// --- 3. Рендеринг таблицы состава ---

function renderItemsTable(itemsMap, isEdit) {
    // (Логика рендеринга остается без изменений, она использует calculateCurrentTempTotal внутри)
    const container = document.getElementById('table-scroll-container');
    const scrollPos = container ? container.scrollTop : 0;
    const body = document.getElementById('order-items-body');
    body.innerHTML = '';
    Object.entries(itemsMap).forEach(([name, qty]) => {
        // ... (твоя логика добавления строк)
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
        // ... (твоя логика добавления строки "Добавить товар")
        let options = productsData.map(p => `<option value="${p.name.replace(/"/g, '&quot;')}">${p.name} (${p.price} ֏)</option>`).join('');
        body.innerHTML += `<tr style="background:#f8fafc; position: sticky; bottom: 0;">
            <td><select id="add-item-select" style="width:100%">${options}</select></td>
            <td><input type="number" id="add-item-qty" value="1" min="1" style="width:65px;"></td>
            <td colspan="3"><button class="btn-primary" onclick="addItemToEdit()" style="width:100%">+ Добавить</button></td>
        </tr>`;
    }

    if (container) {
        requestAnimationFrame(() => {
            container.scrollTop = scrollPos;
        });
    }
    // Вызываем расчет общей суммы после рендера таблицы
    calculateCurrentTempTotal();
}

// --- 4. Основные функции карточки заказа ---
function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return;
    tempItems = JSON.parse(JSON.stringify(order.items));
    document.getElementById('modal-title').innerHTML = `Детали операции <span class="badge" style="margin-left:10px;">ЗАКАЗ №${order.id}</span>`;
    const info = document.getElementById('order-info');
    info.style.gridTemplateColumns = '1fr';
    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
            <div><small>Дата заказа:</small><br><b>${formatOrderDate(order.createdAt)}</b></div>
            <div><small>Менеджер:</small><br><b>${order.managerId}</b></div>
        </div>
        <div class="modal-info-row">
            <div><small>Доставка:</small><br><b>${order.deliveryDate || '---'}</b></div>
            <div><small>Оплата:</small><br><b>${translatePayment(order.paymentMethod)}</b></div>
            <div><small>Фактура:</small><br><b>${order.needsSeparateInvoice ? 'ДА' : 'НЕТ'}</b></div>
        </div>
    `;

    renderItemsTable(tempItems, false);
    document.getElementById('order-total-price').innerText = "Итого: " + (order.totalAmount || 0).toLocaleString() + " ֏";
    const footer = document.getElementById('order-footer-actions');
    // Формируем кнопки в зависимости от статуса инвойса
    if (order.invoiceId) {
        // Если СЧЕТ ВЫСТАВЛЕН: только История и Закрыть
        footer.innerHTML = `
            <button class="btn-primary" style="background:#6366f1" onclick="showOrderHistory(${order.id})">📜 История</button>
            <div style="color:#991b1b; font-weight:700; background:#fee2e2; padding:10px; border-radius:8px; flex:1; text-align:center;">СЧЕТ ВЫСТАВЛЕН</div>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
        `;
    } else {
        // Если СЧЕТА НЕТ: История, Изменить, Отменить, Закрыть
        footer.innerHTML = `
            <button class="btn-primary" style="background:#6366f1" onclick="showOrderHistory(${order.id})">📜 История</button>
            <button class="btn-primary" onclick="enableOrderEdit(${order.id})">Изменить</button>
            <button class="btn-primary" style="background:#ef4444" onclick="cancelOrder(${order.id})">Отменить заказ</button>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
        `;
    }
    openModal('modal-order-view');
}

function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    document.getElementById('modal-title').innerText = "Режим редактирования заказа #" + id;
    const info = document.getElementById('order-info');
    info.style.gridTemplateColumns = '1fr';
    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === order.shopName ? 'selected' : ''}>${c.name}</option>`).join('');
    let paymentOptions = paymentMethods.map(m => {
        const val = (typeof m === 'object') ? m.name : m;
        const label = translatePayment(m);
        return `<option value="${val}" ${order.paymentMethod === val ? 'selected' : ''}>${label}</option>`;
    }).join('');

    info.innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин</label><select id="edit-shop">${clientOptions}</select></div>
            <div><label>Доставка</label><input type="text" id="edit-delivery" value="${order.deliveryDate || ''}"></div>
            <div><label>Оплата</label><select id="edit-payment">${paymentOptions}</select></div>
            <div><label>Отд. Фактура</label>
                <select id="edit-invoice-type">
                    <option value="false" ${!order.needsSeparateInvoice ? 'selected' : ''}>НЕТ</option>
                    <option value="true" ${order.needsSeparateInvoice ? 'selected' : ''}>ДА</option>
            </select>
            </div>
        </div>`;

    renderItemsTable(tempItems, true);
    document.getElementById('order-total-price').innerText = "Редактирование состава...";
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveFullChanges(${id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="cancelOrderEdit(${id})">Отмена</button>`;
}

function cancelOrderEdit(id) {
    openOrderDetails(id);
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
                ordersData[idx] = {...ordersData[idx], ...data, totalAmount: result.finalSum};
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
    info.style.gridTemplateColumns = '1fr';


    const displayReason = translateReason(ret.returnReason);
    const footer = document.getElementById('order-footer-actions');
    if (ret.status === 'DRAFT') {
        footer.innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="confirmReturn(${ret.id})">✅ Подтвердить и уменьшить долг</button>
        <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        footer.innerHTML = `<b style="color:green">ПОДТВЕРЖДЕНО</b> <button class="btn-primary" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    }

    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
            <div><small>Дата возврата:</small><br><b>${formatOrderDate(ret.returnDate)}</b></div>
            <div><small>Причина:</small><br><b style="color: #ef4444;">${displayReason}</b></div>
        </div>
    `;

    renderItemsTable(tempItems, false);
    document.getElementById('order-total-price').innerText = "Сумма возврата: " + (ret.totalAmount || 0).toLocaleString() + " ֏";

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;

    // Добавляем кнопку удаления, если статус черновик
    if (ret.status === 'DRAFT') {
        footer.innerHTML = `
            <button class="btn-primary" style="background:#10b981" onclick="confirmReturn(${ret.id})">✅ Подтвердить</button>
            <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить</button>
            <button class="btn-primary" style="background:#ef4444" onclick="deleteReturnOrder(${ret.id})">❌ Удалить</button>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        // Если подтвержден или отменен, просто закрыть
        footer.innerHTML = `<b style="color:gray;">Обработан</b> <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    }
    openModal('modal-order-view');
}

async function confirmReturn(id) {
    if (!confirm("Подтвердить возврат? Сумма будет вычтена из долга клиента.")) return;
    const response = await fetch(`/api/admin/returns/${id}/confirm`, {method: 'POST'});
    if (response.ok) {
        showToast("Возврат подтвержден!");
        location.reload();
    }
}

function enableReturnEdit(id) {
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;
    document.getElementById('modal-title').innerText = "Редактирование возврата #" + id;
    const info = document.getElementById('order-info');
    info.style.gridTemplateColumns = '1fr';
    let reasonOptions = returnReasons.map(r => {
        const val = (typeof r === 'object') ? r.name : r;
        const label = translateReason(r);
        return `<option value="${val}" ${ret.returnReason === val ? 'selected' : ''}>${label}</option>`;
    }).join('');

    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === ret.shopName ? 'selected' : ''}>${c.name}</option>`).join('');

    info.innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин</label><select id="edit-ret-shop">${clientOptions}</select></div>
            <div><label>Дата возврата</label><input type="text" id="edit-ret-date" value="${ret.returnDate || ''}"></div>
            <div><label>Причина</label><select id="edit-ret-reason">${reasonOptions}</select></div>
        </div>`;

    renderItemsTable(tempItems, true);
    document.getElementById('order-total-price').innerText = "Редактирование состава...";
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveReturnChanges(${id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="cancelReturnEdit(${id})">Отмена</button>`;
}

function cancelReturnEdit(id) {
    openReturnDetails(id);
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
                returnsData[idx] = {...returnsData[idx], ...data, totalAmount: result.newTotal};
                updateReturnRowInTable(returnsData[idx]); // <--- Вызов обновления строки
            }
            showStatus("✅ Возврат обновлен!");
            setTimeout(() => openReturnDetails(id), 1000);
        }
    } catch (e) {
        showStatus("❌ Ошибка сети", true);
    }
}

// Обновление строки возврата в главной таблице
function updateReturnRowInTable(ret) {
    const row = document.querySelector(`tr[onclick*="openReturnDetails(${ret.id})"]`);
    if (row) {
        row.cells[0].innerText = formatOrderDate(ret.returnDate);
        row.cells[1].innerText = ret.managerId; // Менеджер обычно не меняется при редактировании возврата
        row.cells[2].innerText = ret.shopName;
        row.cells[3].innerText = translateReason(ret.returnReason);
        row.cells[4].innerText = (ret.totalAmount || 0).toLocaleString() + ' ֏';
    }
}

// --- НОВАЯ ЛОГИКА ДЛЯ КЛИЕНТОВ (CLIENTS) ---
function cancelClientEdit(id) {
    openClientDetails(id);
}

// 2. Полная карточка клиента (все поля)
function openClientDetails(id) {
    const client = clientsData.find(c => c.id == id);
    if (!client) return;
    window.currentClientId = id;
    document.getElementById('modal-client-title').innerHTML = `Детали клиента <span class="badge">${client.name}</span>`;
    const info = document.getElementById('client-info');
    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Название магазина:</small><br><b>${client.name}</b></div>
            <div><small>Владелец / ИП:</small><br><b>${client.ownerName || '---'}</b></div>
            <div><small>ИНН:</small><br><b>${client.inn || '---'}</b></div>
        </div>
        <div class="modal-info-row">
            <div><small>Телефон:</small><br><b>${client.phone || '---'}</b></div>
            <div><small>Адрес:</small><br><b>${client.address || '---'}</b></div>
            <div><small>Текущий долг:</small><br><b class="price-down">${(client.debt || 0).toLocaleString()} ֏</b></div>
        </div>
    `;
    document.getElementById('client-footer-actions').innerHTML = `
        <button class="btn-primary" onclick="enableClientEdit()">Изменить данные</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-client-view')">Закрыть</button>`;

    openModal('modal-client-view');
}

function enableClientEdit() {
    const client = clientsData.find(c => c.id == window.currentClientId);
    if (!client) return;
    const info = document.getElementById('client-info');
    info.innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин</label><input type="text" id="edit-client-name" value="${client.name}"></div>
            <div><label>Владелец</label><input type="text" id="edit-client-owner" value="${client.ownerName || ''}"></div>
            <div><label>ИНН</label><input type="text" id="edit-client-inn" value="${client.inn || ''}"></div>
        </div>
        <div class="modal-info-row">
            <div><label>Телефон</label><input type="text" id="edit-client-phone" value="${client.phone || ''}"></div>
            <div><label>Адрес</label><input type="text" id="edit-client-address" value="${client.address || ''}"></div>
            <div><label>Долг</label><input type="number" id="edit-client-debt" value="${client.debt || 0}"></div>
        </div>
    `;

    document.getElementById('client-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveClientChanges(${client.id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openClientDetails(${client.id})">Отмена</button>`;
}

async function saveClientChanges(id) {
    const data = {
        name: document.getElementById('edit-client-name').value,
        ownerName: document.getElementById('edit-client-owner').value,
        inn: document.getElementById('edit-client-inn').value,
        phone: document.getElementById('edit-client-phone').value,
        address: document.getElementById('edit-client-address').value,
        debt: parseFloat(document.getElementById('edit-client-debt').value) || 0
    };

    try {
        const response = await fetch(`/api/admin/clients/${id}/edit`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });

        if (response.ok) {
            // СРАЗУ ОБНОВЛЯЕМ ДАННЫЕ В ЛОКАЛЬНОМ МАССИВЕ
            const idx = clientsData.findIndex(c => c.id == id);
            if (idx !== -1) clientsData[idx] = {...clientsData[idx], ...data};
            // Обновляем строку в таблице
            updateClientRowInTable(clientsData[idx]);
            // Показываем обновленную карточку
            openClientDetails(id);
            showStatus("✅ Данные клиента обновлены в базе");
        } else {
            showToast("Ошибка сохранения на сервере");
        }
    } catch (e) {
        showToast("Ошибка сети");
    }
}

// Обновление строки клиента в главной таблице
function updateClientRowInTable(client) {
    const row = document.querySelector(`tr[onclick*="openClientDetails(${client.id})"]`);
    if (row) {
        row.cells[0].innerText = client.name;
        row.cells[1].innerText = client.address;
        row.cells[2].innerText = (client.debt || 0).toLocaleString() + ' ֏';
    }
}

// --- НОВАЯ ЛОГИКА ДЛЯ СКЛАДА (PRODUCTS) ---

function cancelProductEdit(id) {
    openProductDetails(id);
}

function openProductDetails(id) {
    window.currentProductId = id;
    const product = productsData.find(p => p.id == id);
    if (!product) return;
    document.getElementById('modal-product-title').innerHTML = `Детали товара <span class="badge" style="margin-left:10px;">${product.name}</span>`;
    const info = document.getElementById('product-info');
    info.style.gridTemplateColumns = '1fr';

    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Название:</small><br><b>${product.name}</b></div>
            <div><small>Цена:</small><br><b class="price-up">${(product.price || 0).toLocaleString()} ֏</b></div>
            <div><small>Категория:</small><br><b>${product.category || '---'}</b></div>
        </div>
        <div class="modal-info-row">
            <div><small>Остаток на складе:</small><br><b>${product.stockQuantity || 0} шт.</b></div>
            <div><small>Штрих-код:</small><br><b>${product.barcode || '---'}</b></div>
            <div><small>Упаковка (шт. в коробке):</small><br><b>${product.itemsPerBox || '---'}</b></div>
        </div>
    `;

    const footer = document.getElementById('product-footer-actions');
    footer.innerHTML = `
        <button class="btn-primary" onclick="enableProductEdit()">Изменить товар</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-product-view')">Закрыть</button>`;

    openModal('modal-product-view');
}

function enableProductEdit() {
    const id = window.currentProductId;
    const product = productsData.find(p => p.id == id);
    if (!product) return;
    document.getElementById('modal-product-title').innerText = "Редактирование товара";
    const info = document.getElementById('product-info');
    info.style.gridTemplateColumns = '1fr';
    info.innerHTML = `
         <div class="modal-info-row">
            <div><label>Название</label><input type="text" id="edit-product-name" value="${product.name}"></div>
            <div><label>Цена</label><input type="number" id="edit-product-price" value="${product.price}"></div>
            <div><label>Категория</label><input type="text" id="edit-product-category" value="${product.category || ''}"></div>
        </div>
        <div class="modal-info-row">
            <div><label>Остаток</label><input type="number" id="edit-product-qty" value="${product.stockQuantity || 0}"></div>
            <div><label>Штрих-код</label><input type="text" id="edit-product-barcode" value="${product.barcode || ''}"></div>
            <div><label>Упаковка</label><input type="number" id="edit-product-perbox" value="${product.itemsPerBox || 0}"></div>
        </div>
    `;

    const footer = document.getElementById('product-footer-actions');
    footer.innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveProductChanges(${product.id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="cancelProductEdit(${product.id})">Отмена</button>`;
}

async function saveProductChanges(id) {
    const data = {
        name: document.getElementById('edit-product-name').value,
        price: parseFloat(document.getElementById('edit-product-price').value) || 0,
        stockQuantity: parseInt(document.getElementById('edit-product-qty').value) || 0,
        barcode: document.getElementById('edit-product-barcode').value,
        itemsPerBox: parseInt(document.getElementById('edit-product-perbox').value) || 0,
        category: document.getElementById('edit-product-category').value
    };

    try {
        const response = await fetch(`/api/admin/products/${id}/edit`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (response.ok) {
            const idx = productsData.findIndex(p => p.id == id);
            if (idx !== -1) {
                productsData[idx] = {...productsData[idx], ...data};
                updateProductRowInTable(productsData[idx]); // <--- Вызов обновления строки
            }
            showStatus("✅ Данные товара успешно обновлены!");
            setTimeout(() => openProductDetails(id), 1000);
        } else {
            showStatus(result.error || result.message || "Ошибка сохранения", true);
        }
    } catch (e) {
        showStatus("❌ Ошибка соединения", true);
    }
}

// Обновление строки товара в главной таблице
function updateProductRowInTable(product) {
    const row = document.querySelector(`tr[onclick*="openProductDetails(${product.id})"]`);
    if (row) {
        row.cells[0].innerText = product.name;
        row.cells[1].innerText = (product.price || 0).toLocaleString() + ' ֏';
        row.cells[2].innerText = (product.stockQuantity || 0) + ' шт.';
        row.cells[3].innerText = product.itemsPerBox;
        row.cells[4].innerText = product.barcode;
    }
}

function openPaymentModal(invoiceId) {
    document.getElementById('pay-invoice-id').value = invoiceId;
    openModal('modal-payment');
}

async function submitPayment() {
    const data = {
        invoiceId: document.getElementById('pay-invoice-id').value,
        amount: parseFloat(document.getElementById('pay-amount').value),
        comment: document.getElementById('pay-comment').value
    };

    const response = await fetch('/api/payments/register', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(data)
    });

    if (response.ok) {
        showToast("Оплата принята, долг клиента обновлен!");
        location.reload();
    } else {
        showToast("Ошибка при регистрации оплаты");
    }
}

// Универсальная функция поиска
function filterTable(inputId, tableBodyId) {
    const input = document.getElementById(inputId);
    const filter = input.value.toUpperCase();
    const tbody = document.getElementById(tableBodyId);
    if (!tbody) return;
    const tr = tbody.getElementsByTagName("tr");

    for (let i = 0; i < tr.length; i++) {
        const text = tr[i].textContent || tr[i].innerText;
        tr[i].style.display = text.toUpperCase().includes(filter) ? "" : "none";
    }
}

// Функция отправки нового товара на сервер
async function submitCreateProduct() {
    const data = {
        name: document.getElementById('new-p-name').value,
        price: parseFloat(document.getElementById('new-p-price').value) || 0,
        stockQuantity: parseInt(document.getElementById('new-p-qty').value) || 0,
        itemsPerBox: parseInt(document.getElementById('new-p-box').value) || 1,
        barcode: document.getElementById('new-p-code').value,
        category: document.getElementById('new-p-cat').value
    };

    if (!data.name) {
        showToast("Введите название товара!");
        return;
    }

    try {
        const response = await fetch('/api/admin/products/create', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });
        if (response.ok) {
            location.reload(); // Обновляем страницу после создания
        } else {
            showToast("Ошибка при сохранении товара");
        }
    } catch (e) {
        console.error(e);
        showToast("Ошибка сети");
    }
}

// --- НОВЫЙ ЗАКАЗ ---
function openCreateOrderModal() {
    tempItems = {};
    document.getElementById('modal-title').innerText = "Создание нового заказа";

    // Формируем список клиентов и менеджеров
    let clientOptions = clientsData.map(c => `<option value="${c.name}">${c.name}</option>`).join('');
    let managerOptions = usersData.map(u => `<option value="${u.username}">${u.fullName}</option>`).join('');

    // Горизонтальное отображение в 2 ряда:
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин:</label><select id="new-op-shop">${clientOptions}</select></div>
            <div><label>Менеджер:</label><select id="new-op-manager">${managerOptions}</select></div>
            <div><label>Доставка:</label><input type="date" id="new-op-date" value="${new Date().toISOString().split('T')[0]}"></div>
        </div>
        <div class="modal-info-row">
            <div><label>Оплата:</label>
                <select id="new-op-payment">
                    <option value="CASH">Наличный</option>
                    <option value="TRANSFER">Перевод</option>
                </select>
            </div>
            <div><label>Фактура:</label>
                <select id="new-op-invoice">
                    <option value="false">НЕТ</option>
                    <option value="true">ДА</option>
                </select>
            </div>
             <div><label>Комментарий:</label><input type="text" id="new-op-comment" placeholder="Любой текст"></div>
        </div>`;

    renderItemsTable(tempItems, true);

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveNewManualOperation('order')">Создать заказ</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Отмена</button>`;

    openModal('modal-order-view');
}

function openCreateReturnModal() {
    tempItems = {};
    document.getElementById('modal-title').innerText = "Оформление нового возврата";

    let clientOptions = clientsData.map(c => `<option value="${c.name}">${c.name}</option>`).join('');
    let reasonOptions = returnReasons.map(r => `<option value="${r.name || r}">${translateReason(r)}</option>`).join('');
    let managerOptions = usersData.map(u => `<option value="${u.username}">${u.fullName}</option>`).join('');


    // Горизонтальное отображение в 1 ряд:
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин:</label><select id="new-op-shop">${clientOptions}</select></div>
            <div><label>Менеджер:</label><select id="new-op-manager">${managerOptions}</select></div>
            <div><label>Причина:</label><select id="new-op-reason">${reasonOptions}</select></div>
            <div><label>Дата возврата:</label><input type="date" id="new-op-date" value="${new Date().toISOString().split('T')[0]}"></div>
        </div>`;

    renderItemsTable(tempItems, true);

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveNewManualOperation('return')">Создать возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Отмена</button>`;

    openModal('modal-order-view');
}


// --- УНИВЕРСАЛЬНОЕ СОХРАНЕНИЕ ---
// --- 7. УНИВЕРСАЛЬНОЕ СОХРАНЕНИЕ ---

async function saveNewManualOperation(type) {
    if (Object.keys(tempItems).length === 0) {
        showToast("Добавьте хотя бы один товар!");
        return;
    }

    const url = type === 'order' ? '/api/admin/orders/create-manual' : '/api/returns/sync';

    // Собираем общие данные
    const data = {
        shopName: document.getElementById('new-op-shop').value,
        managerId: document.getElementById('new-op-manager').value,
        items: tempItems,
        totalAmount: calculateCurrentTempTotal(), // Сумма уже посчитана в renderItemsTable/calculateCurrentTempTotal
        createdAt: new Date().toISOString()
    };

    // Добавляем специфичные данные
    if (type === 'order') {
        data.deliveryDate = document.getElementById('new-op-date').value;
        data.paymentMethod = document.getElementById('new-op-payment').value;
        data.needsSeparateInvoice = document.getElementById('new-op-invoice').value === "true";
        // data.comment = document.getElementById('new-op-comment').value; // TODO: Добавить поле comment в сущность Order.java
        data.status = "ACCEPTED";
    } else {
        data.returnReason = document.getElementById('new-op-reason').value;
        data.returnDate = document.getElementById('new-op-date').value;
    }

    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(type === 'order' ? data : [data])
        });
        if (response.ok) {
            showToast(`✅ ${type === 'order' ? 'Заказ' : 'Возврат'} успешно создан`, "success");
            location.reload();
        } else {
            const result = await response.json();
            showStatus(result.error || "Ошибка сервера", true);
        }
    } catch (e) {
        showStatus("Ошибка сети", true);
    }
}

function printInvoiceInline(invoiceId) {
    // 1. Создаем iframe
    const iframe = document.createElement('iframe');
    iframe.style.display = "none";
    iframe.src = "/admin/invoices/print/" + invoiceId;
    document.body.appendChild(iframe);

    // 2. Печатаем правильно через контентное окно
    iframe.onload = function () {
        try {
            // В 2026 году это самый надежный способ вызова печати из фрейма
            iframe.contentWindow.print();

            // Удаляем фрейм после закрытия окна печати
            setTimeout(() => {
                document.body.removeChild(iframe);
            }, 1000);
        } catch (e) {
            console.error("Ошибка печати:", e);
            // Если фрейм все равно блокируется, открываем в новом окне как запасной вариант
            window.open("/admin/invoices/print/" + invoiceId, '_blank');
        }
    };
}


// Функция отмены заказа
async function cancelOrder(id) {
    if (!confirm("Отменить заказ? Товар вернется на склад.")) return;

    try {
        const response = await fetch(`/api/admin/orders/${id}/cancel`, {method: 'POST'});
        if (response.ok) {
            showToast("Заказ отменен");
            location.reload();
        } else {
            showToast("Ошибка при отмене");
        }
    } catch (e) {
        showToast("Ошибка сети");
    }
}

// Функция просмотра истории (Аудит)
async function showOrderHistory(orderId) {
    // Для 2026 года: мы можем запрашивать логи по конкретному объекту
    // Предполагаем, что у нас есть эндпоинт /api/admin/audit/order/{id}
    try {
        const response = await fetch(`/api/admin/audit/order/${orderId}`);
        const logs = await response.json();

        let historyHtml = logs.length > 0 ? logs.map(log => `
            <div style="border-bottom:1px solid #eee; padding:10px 0;">
                <small style="color:gray">${fmt(log.timestamp)}</small><br>
                <b>${log.username}:</b> ${log.action}<br>
                <i style="font-size:12px">${log.details || ''}</i>
            </div>
        `).join('') : '<p>История изменений пуста</p>';

        // Показываем историю вместо таблицы товаров на время
        const body = document.getElementById('order-items-body');
        document.getElementById('modal-title').innerText = `История заказа #${orderId}`;
        body.innerHTML = `<tr><td colspan="5">${historyHtml}</td></tr>`;

        // Кнопка возврата к составу заказа
        document.getElementById('order-footer-actions').innerHTML = `
            <button class="btn-primary" onclick="openOrderDetails(${orderId})">🔙 Назад к заказу</button>
        `;
    } catch (e) {
        showToast("Эндпоинт аудита не найден. Сначала создайте контроллер для AuditLog!");
    }
}

function showTab(tabId) {
    // 1. Стандартная логика переключения
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    const target = document.getElementById(tabId);
    if (target) target.classList.add('active');
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const btnId = tabId.replace('tab-', 'btn-');
    if (document.getElementById(btnId)) document.getElementById(btnId).classList.add('active');
    localStorage.setItem('sellion_tab', tabId);
    // 2. НОВАЯ ЛОГИКА: Если открыт Обзор, обновляем данные
    if (tabId === 'tab-main') {
        updateDashboardStats();
    }
}

function updateDashboardStats() {
    // Считаем средний чек по актуальным данным ordersData
    const totalSum = ordersData.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
    const avg = ordersData.length > 0 ? (totalSum / ordersData.length) : 0;
    document.getElementById('stat-avg-check').innerText = Math.round(avg).toLocaleString() + " ֏";
    document.getElementById('stat-pending-orders').innerText = ordersData.filter(o => o.status === 'NEW').length;

    // Имитация "Кто в сети" (в 2026 можно сделать через WebSocket, пока берем из базы)
    const onlineList = document.getElementById('online-users-list');
    onlineList.innerHTML = `<span class="badge" style="background:#dcfce7; color:#166534;">● Администратор (Вы)</span>`;

    // Добавим пару случайных операторов для вида (или из usersData)
    const operators = ["Оператор Арам", "Оператор Анна"];
    operators.forEach(op => {
        onlineList.innerHTML += `<span class="badge" style="background:#f1f5f9; color:#475569;">● ${op}</span>`;
    });
}

async function deleteReturnOrder(id) {
    if (!confirm("Вы уверены, что хотите удалить этот возврат?")) return;

    try {
        // Эндпоинт /api/admin/returns/{id}/delete, который мы создали ранее
        const response = await fetch(`/api/admin/returns/${id}/delete`, {method: 'POST'});
        if (response.ok) {
            showToast("Возврат удален", "success");
            location.reload();
        } else {
            const error = await response.json();
            showToast(error.error || "Ошибка удаления возврата", "error");
        }
    } catch (e) {
        showToast("Ошибка сети", "error");
    }
}

// Заглушка для импорта
function triggerImport() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.xlsx, .xls';

    input.onchange = async (e) => {
        const file = e.target.files[0]; // Берем первый выбранный файл
        if (!file) return;
        const formData = new FormData();
        formData.append('file', file);

        // Используем наш новый современный Toast вместо alert
        showToast("Начинаем импорт файла...", "info");

        try {
            // ВОТ ЗДЕСЬ МЫ СТАВИМ НОВЫЙ ПУТЬ:
            const response = await fetch('/api/products/import', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const result = await response.json();
                showToast(`Успешно! ${result.message}`, "success");
                // Перезагружаем страницу через 1.5 секунды, чтобы данные на складе обновились
                setTimeout(() => location.reload(), 1500);
            } else {
                const errorText = await response.text();
                showToast("Ошибка импорта: " + errorText, "error");
            }
        } catch (err) {
            console.error(err);
            showToast("Критическая ошибка сети или сервера", "error");
        }
    };
    input.click();
}

function showToast(text, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast-msg toast-${type}`;
    const icon = type === 'success' ? '✅' : (type === 'error' ? '❌' : 'ℹ️');
    toast.innerHTML = `<span>${icon}</span> <span>${text}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 500);
    }, 4000);
}

async function resetPassword(userId) {
    if (!confirm("Сбросить пароль пользователю на стандартный '1111'?")) return;

    try {
        // У вас есть эндпоинт /admin/users/reset-password/{id} в UserWebController
        const response = await fetch(`/admin/users/reset-password/${userId}`, {method: 'POST'});
        if (response.ok) {
            showToast("Пароль сброшен на '1111'", "success"); // Используем Toast
        } else {
            showToast("Ошибка при сбросе пароля", "error");
        }
    } catch (e) {
        showToast("Ошибка сети", "error");
    }
}

document.addEventListener("DOMContentLoaded", () => {
    showTab(localStorage.getItem('sellion_tab') || 'tab-orders');
});