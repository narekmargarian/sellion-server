let tempItems = {};

// --- 1. Навигация и Утилиты ---
function openModal(id) {
    const modal = document.getElementById(id);
    modal.classList.add('active');
    document.getElementById(id).classList.add('active');
    document.body.style.overflow = 'hidden'; // Блокируем фон
    const sc = modal.querySelector('#table-scroll-container');
    if (sc) sc.scrollTop = 0;
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
    document.body.style.overflow = '';
    document.getElementById(id).classList.remove('active');
}

// Безопасное форматирование даты (убирает T и секунды)
function formatOrderDate(dateVal) {
    if (!dateVal) return '---';
    if (typeof dateVal === 'object' && dateVal.year) {
        const months = {
            'JANUARY': 'января', 'FEBRUARY': 'февраля', 'MARCH': 'марта', 'APRIL': 'апреля',
            'MAY': 'мая', 'JUNE': 'июня', 'JULY': 'июля', 'AUGUST': 'августа',
            'SEPTEMBER': 'сентября', 'OCTOBER': 'октября', 'NOVEMBER': 'ноября', 'DECEMBER': 'декабря'
        };
        return `${dateVal.dayOfMonth} ${months[dateVal.month] || dateVal.monthValue} ${dateVal.year}`;
    }
    if (typeof dateVal === 'string') {
        return dateVal.includes('T') ? dateVal.replace('T', ' ').substring(0, 16) : dateVal;
    }
    return dateVal;
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
    const modalTitle = document.getElementById('modal-title').innerText.toLowerCase();
    const isReturn = modalTitle.includes("возврат");

    if (!input) return;
    let newVal = parseInt(input.value);
    // 1. Логика удаления при 0 или пустом вводе
    if (isNaN(newVal) || newVal <= 0) {
        delete tempItems[name];
        showStatus(`Товар "${name}" удален`);
    } else {
        // 2. Проверка остатка на складе (стандарт 2026)
        const product = productsData.find(p => p.name === name);
        if (!isReturn && product && newVal > product.stockQuantity) {
            showStatus(`Недостаточно товара "${product.name}"! Доступно на складе: ${product.stockQuantity}`, true);
            input.value = product.stockQuantity;
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
    const selectElement = document.getElementById('add-item-select');
    const productId = selectElement.value; // Это строка "123"
    const qty = parseInt(document.getElementById('add-item-qty').value) || 1;

    // ИСПОЛЬЗУЕМ == вместо === чтобы сравнить "123" и 123
    const product = productsData.find(p => p.id == productId);

    if (product) {
        const modalTitle = document.getElementById('modal-title').innerText.toLowerCase();
        const isReturn = modalTitle.includes("возврат");

        // Если это НЕ возврат, проверяем склад
        if (!isReturn && qty > product.stockQuantity) {
            showStatus(`Недостаточно товара "${product.name}"! Доступно: ${product.stockQuantity}`, true);
            return;
        }

        // Добавляем в список
        tempItems[product.name] = (tempItems[product.name] || 0) + qty;

        // Перерисовываем таблицу
        renderItemsTable(tempItems, true);
        showStatus(`Товар "${product.name}" добавлен`);
    } else {
        // Если продукт не найден (например, не выбран в списке)
        showStatus("Выберите товар из списка", true);
    }
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
        // Замени "Предварительно:" на "Итого:"
        totalPriceElement.innerText = "Итого: " + total.toLocaleString() + " ֏";
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
        // Исправлено: добавляем кавычки вокруг value и проверяем наличие данных
        let options = productsData.map(p => `<option value="${p.id}">${p.name} (${p.price} ֏)</option>`).join('');

        body.innerHTML += `<tr style="background:#f8fafc; position: sticky; bottom: 0;">
        <td>
            <select id="add-item-select" style="width:100%">
                <option value="">-- Выберите товар --</option>
                ${options}
            </select>
        </td>
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
    const printBtn = `<button class="btn-primary" style="background:#475569" onclick="printOrder(${order.id})">🖨 Печать Заказа</button>`;
    // info.style.gridTemplateColumns = '1fr';
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
        footer.innerHTML = `
        <button class="btn-primary" style="background:#6366f1" onclick="showOrderHistory(${order.id})">📜 История</button>
        <button class="btn-primary" style="background:#475569" onclick="printOrder(${order.id})">🖨 Печать</button>
        <div style="color:#991b1b; font-weight:700; background:#fee2e2; padding:10px; border-radius:8px; flex:1; text-align:center;">СЧЕТ ВЫСТАВЛЕН</div>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
    `;
    } else {
        footer.innerHTML = `
        <button class="btn-primary" style="background:#6366f1" onclick="showOrderHistory(${order.id})">📜 История</button>
        <button class="btn-primary" style="background:#475569" onclick="printOrder(${order.id})">🖨 Печать</button>
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
    // info.style.gridTemplateColumns = '1fr';
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
// --- 5. Возвраты ---
function openReturnDetails(id) {
    // Используем ==, как мы договорились
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;

    const statusText = ret.status === 'CONFIRMED' ? 'Проведено' : (ret.status === 'DRAFT' ? 'Черновик' : ret.status);
    const statusClass = ret.status === 'CONFIRMED' ? 'bg-success' : 'bg-warning';
    const footer = document.getElementById('order-footer-actions');
    // HTML для кнопки печати
    const printBtnHtml = `<button class="btn-primary" style="background:#475569" onclick="printReturn(${ret.id})">🖨 Печать</button>`;
    const displayReason = translateReason(ret.returnReason);

    tempItems = JSON.parse(JSON.stringify(ret.items));

    // Обновляем заголовок и информацию
    document.getElementById('modal-title').innerHTML = `
        Детали операции 
        <span class="badge ${statusClass}" style="margin-left:10px;">${statusText}</span>
        <span class="badge" style="margin-left:5px;">ВОЗВРАТ №${ret.id}</span>
    `;

    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row">
            <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
            <div><small>Дата возврата:</small><br><b>${formatOrderDate(ret.returnDate)}</b></div>
            <div><small>Причина:</small><br><b style="color: #ef4444;">${displayReason}</b></div>
        </div>
    `;

    // Рендерим таблицу товаров
    renderItemsTable(tempItems, false);

    document.getElementById('order-total-price').innerText = "Сумма возврата: " + (ret.totalAmount || 0).toLocaleString() + " ֏";

    // *** ЕДИНСТВЕННЫЙ И ПРАВИЛЬНЫЙ БЛОК ДЛЯ КНОПОК ФУТЕРА ***
    if (ret.status === 'DRAFT') {
        footer.innerHTML = `
            <button class="btn-primary" style="background:#10b981" onclick="confirmReturn(${ret.id})">✅ Подтвердить</button>
            ${printBtnHtml}
            <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">Изменить</button>
            <button class="btn-primary" style="background:#ef4444" onclick="deleteReturnOrder(${ret.id})">❌ Удалить</button>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
        `;
    } else {
        footer.innerHTML = `
            <b style="color:gray;">Обработан</b>
            ${printBtnHtml}
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
        `;
    }
    // *********************************************************

    openModal('modal-order-view');
}


async function confirmReturn(id) {
    showConfirmModal("Подтвердить возврат?", "Сумма будет вычтена из долга клиента.", async () => {
        const response = await fetch(`/api/admin/returns/${id}/confirm`, {method: 'POST'});
        if (response.ok) {
            showToast("Возврат подтвержден!", "success");
            location.reload();
        }
    });
}


function enableReturnEdit(id) {
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return;
    document.getElementById('modal-title').innerText = "Редактирование возврата #" + id;
    const info = document.getElementById('order-info');
    // info.style.gridTemplateColumns = '1fr';
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
    const client = clientsData.find(c => c.id === window.currentClientId);
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
    // info.style.gridTemplateColumns = '1fr';

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
    // info.style.gridTemplateColumns = '1fr';
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

function openCreateClientModal() { // Используй это имя в onclick
    openModal('modal-client');
}

// --- НОВЫЙ ЗАКАЗ ---
// Вспомогательная функция для получения списка менеджеров (чтобы не дублировать код)
// В script.js

let managerIdList = []; // Глобальный массив для хранения списка из Enum

// Функция для загрузки списка менеджеров с сервера (асинхронно)
async function loadManagerIds() {
    try {
        const response = await fetch('/api/public/managers'); // Вызываем наш API
        if (response.ok) {
            managerIdList = await response.json();
            console.log("Список менеджеров из Enum загружен:", managerIdList);
        } else {
            console.error("Не удалось загрузить список менеджеров из Enum.");
        }
    } catch (e) {
        console.error("Ошибка сети при загрузке Enum менеджеров.");
    }
}

// ... (остальные ваши функции: openModal, closeModal, formatOrderDate, translatePayment, ...)

// Вспомогательная функция, которая использует загруженный список
function getManagerOptionsHTML() {
    // Теперь мы используем managerIdList, который содержит ТОЛЬКО логины из Enum (включая OFFICE)
    return managerIdList.map(managerName => `<option value="${managerName}">${managerName}</option>`).join('');
    // Ручное добавление больше не нужно!
}

// ... (все остальные функции логики) ...

// ОБРАТИТЕ ВНИМАНИЕ НА ЭТОТ БЛОК:
// Он запускает загрузку данных при старте страницы и активирует табы

// ... (остальная часть вашего script.js) ...


// --- НОВЫЙ ЗАКАЗ ---
async function openCreateOrderModal() {
    await loadManagerIds();
    tempItems = {};
    document.getElementById('modal-title').innerText = "Создание нового заказа";

    let clientOptions = clientsData.map(c => `<option value="${c.name}">${c.name}</option>`).join('');
    // ИСПРАВЛЕНО: Теперь переменная определена
    let managerOptions = getManagerOptionsHTML();

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

// --- НОВЫЙ ВОЗВРАТ ---
async function openCreateReturnModal() {
    await loadManagerIds();
    tempItems = {};
    document.getElementById('modal-title').innerText = "Оформление нового возврата";

    let clientOptions = clientsData.map(c => `<option value="${c.name}">${c.name}</option>`).join('');
    let reasonOptions = returnReasons.map(r => `<option value="${r.name || r}">${translateReason(r)}</option>`).join('');
    // ИСПРАВЛЕНО: Используем общую функцию со списком менеджеров
    let managerOptions = getManagerOptionsHTML();

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

function getCurrentTimeFormat() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    return `${hours}:${minutes}:${seconds}`;
}
// --- УНИВЕРСАЛЬНОЕ СОХРАНЕНИЕ ---
// --- 7. УНИВЕРСАЛЬНОЕ СОХРАНЕНИЕ ---

async function saveNewManualOperation(type) {
    if (Object.keys(tempItems).length === 0) {
        showToast("Добавьте хотя бы один товар!");
        return;
    }

    const url = type === 'order' ? '/api/admin/orders/create-manual' : '/api/returns/sync';

    // 1. Получаем дату из календаря (например, "2026-01-16")
    const baseDate = document.getElementById('new-op-date').value;
    if (!baseDate) {
        showToast("Выберите дату!");
        return;
    }

    // 2. Вспомогательная функция для конвертации в русский формат "16 января 2026"
    // Это именно то, что ожидает ваш сервер в LocalDate
    const toRuDate = (isoStr) => {
        const d = new Date(isoStr);
        const months = ["января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"];
        return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
    };

    const russianDate = toRuDate(baseDate); // Результат: "16 января 2026"
    const formattedDateTime = `${baseDate}T${getCurrentTimeFormat()}`; // Для createdAt

    // Собираем общие данные
    const data = {
        shopName: document.getElementById('new-op-shop').value,
        managerId: document.getElementById('new-op-manager').value,
        items: tempItems,
        totalAmount: calculateCurrentTempTotal(),
        // createdAt — это String в Java, здесь время нужно оставить
        createdAt: formattedDateTime
    };

    // Добавляем специфичные данные
    if (type === 'order') {
        data.comment = document.getElementById('new-op-comment').value;
        // ИСПРАВЛЕНО: Отправляем только дату БЕЗ ВРЕМЕНИ в формате "16 января 2026"
        data.deliveryDate = russianDate;
        data.paymentMethod = document.getElementById('new-op-payment').value;
        data.needsSeparateInvoice = document.getElementById('new-op-invoice').value === "true";
        data.status = "ACCEPTED";
    } else {
        data.returnReason = document.getElementById('new-op-reason').value;
        // ИСПРАВЛЕНО: Отправляем только дату БЕЗ ВРЕМЕНИ в формате "16 января 2026"
        data.returnDate = russianDate;
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
            // Если сервер вернет ошибку валидации даты, мы увидим её здесь
            showStatus(result.error || "Ошибка сервера при парсинге даты", true);
        }
    } catch (e) {
        showStatus("Ошибка сети", true);
    }
}


function printInvoiceInline(invoiceId) {
    const url = `/admin/invoices/print/${invoiceId}`;

    // Пытаемся использовать метод с iframe (лучший вариант)
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = url;
    document.body.appendChild(iframe);

    iframe.onload = function () {
        try {
            iframe.contentWindow.focus();
            iframe.contentWindow.print();
            setTimeout(() => document.body.removeChild(iframe), 1000);
        } catch (e) {
            // Если сервер запрещает iframe (X-Frame-Options),
            // используем резервный вариант — новое окно
            console.warn("Фрейм заблокирован, открываю в новом окне...");
            const printWin = window.open(url, '_blank', 'width=800,height=600');
            printWin.onload = function () {
                printWin.focus();
                printWin.print();
                // printWin.close(); // Можно раскомментировать, чтобы окно закрывалось само
            };
        }
    };
}


// Функция отмены заказа
async function cancelOrder(id) {
    showConfirmModal("Отменить заказ?", "Товар вернется на склад.", async () => {
        try {
            const response = await fetch(`/api/admin/orders/${id}/cancel`, {method: 'POST'});
            if (response.ok) {
                showToast("Заказ отменен", "success");
                location.reload();
            } else {
                showToast("Ошибка при отмене", "error");
            }
        } catch (e) {
            showToast("Ошибка сети", "error");
        }
    });
}


async function showOrderHistory(orderId) {
    try {
        const response = await fetch(`/api/admin/audit/order/${orderId}`);

        // Проверяем статус HTTP ответа
        if (!response.ok) {
            const errorText = await response.text();
            // Показываем конкретную ошибку сервера (например, 403 Forbidden)
            showStatus(`Ошибка сервера: ${response.status}. Подробности: ${errorText.substring(0, 150)}`, true);
            return; // Прекращаем выполнение
        }

        const logs = await response.json();

        let historyHtml = logs.length > 0 ? logs.map(log => `
            <div style="border-bottom:1px solid #eee; padding:10px 0;">
                <small style="color:gray">${fmt(log.timestamp)}</small><br>
                <b>${log.username}:</b> ${log.action}<br>
                <i style="font-size:12px">${log.details || ''}</i>
            </div>
        `).join('') : '<p>История изменений пуста</p>';

        const body = document.getElementById('order-items-body');
        document.getElementById('modal-title').innerText = `История заказа #${orderId}`;

        // Очищаем таблицу и вставляем историю в первую ячейку
        body.innerHTML = `<tr><td colspan="5">${historyHtml}</td></tr>`;

        // Кнопка возврата к составу заказа
        document.getElementById('order-footer-actions').innerHTML = `
            <button class="btn-primary" onclick="openOrderDetails(${orderId})">🔙 Назад к заказу</button>
        `;

    } catch (e) {
        // Эта ошибка возникает только при проблеме с сетью (сервер недоступен)
        console.error(e);
        showStatus("Критическая ошибка сети: не удалось подключиться к API аудита.", true);
        // Возвращаем исходное сообщение из вашего кода, если нужно
        // showToast("Эндпоинт аудита не найден. Сначала создайте контроллер для AuditLog!");
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
    showConfirmModal("Удалить возврат?", "Вы уверены, что хотите удалить этот возврат?", async () => {
        try {
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
    });
}


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

//todo Toast//
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




function openUserDetailsModal(id) {
    const user = usersData.find(u => u.id == id);
    if (!user) return;

    // Используем модальное окно, которое уже есть для клиентов
    const modalId = 'modal-client-view';

    // Заголовок модалки
    document.getElementById('modal-client-title').innerHTML = `
        Профиль сотрудника <span class="badge">${user.fullName}</span>
    `;

    // Основная информация (используем существующий контейнер client-info)
    const info = document.getElementById('client-info');
    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Логин:</small><br><b>${user.username}</b></div>
            <div><small>ФИО:</small><br><b>${user.fullName}</b></div>
            <div><small>Роль:</small><br><b>${user.role}</b></div>
        </div>
        <!-- Если у пользователя есть телефон или другие детали, добавьте их здесь -->
        <div class="modal-info-row">
             <div><small>Телефон:</small><br><b>${user.phone || '---'}</b></div>
             <div><small>Email:</small><br><b>${user.email || '---'}</b></div>
        </div>
    `;

    // Действия в футере
    document.getElementById('client-footer-actions').innerHTML = `
        <button class="btn-warning" onclick="event.stopPropagation(); resetPassword(${user.id})">
            Сброс пароля (1111)
        </button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('${modalId}')">
            Закрыть
        </button>
    `;

    // Открываем модальное окно
    openModal(modalId);
}


// Универсальная функция для современного модального подтверждения
function showConfirmModal(title, text, onConfirm) {
    const modal = document.getElementById('confirm-modal');
    document.getElementById('confirm-title').innerText = title;
    document.getElementById('confirm-text').innerText = text;

    const yesBtn = document.getElementById('confirm-yes');
    const noBtn = document.getElementById('confirm-no');

    // Очищаем предыдущие обработчики
    yesBtn.onclick = null;
    noBtn.onclick = null;

    yesBtn.onclick = () => {
        modal.close();
        onConfirm();
    };

    noBtn.onclick = () => modal.close();

    modal.showModal();
}

// script.js

// Функция для открытия модалки создания пользователя
function openCreateUserModal() {
    openModal('modal-user-create');
}

// Функция отправки нового пользователя на сервер
async function submitCreateUser() {
    const data = {
        username: document.getElementById('new-u-username').value,
        fullName: document.getElementById('new-u-fullname').value,
        role: document.getElementById('new-u-role').value,
        password: document.getElementById('new-u-password').value
    };

    if (!data.username || !data.fullName) {
        showToast("Заполните все обязательные поля!");
        return;
    }

    try {
        const response = await fetch('/api/admin/users/create', { // Убедись, что этот API существует
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });
        if (response.ok) {
            showToast("✅ Сотрудник успешно добавлен", "success");
            location.reload(); // Обновляем страницу, чтобы увидеть нового пользователя в таблице
        } else {
            const error = await response.json();
            showToast(error.error || "Ошибка при сохранении пользователя", "error");
        }
    } catch (e) {
        console.error(e);
        showToast("Ошибка сети", "error");
    }
}



async function resetPassword(userId) {
    showConfirmModal("Сброс пароля", "Сбросить пароль пользователю на стандартный 'qwerty'?", async () => {
        try {
            const response = await fetch(`/api/admin/users/reset-password/${userId}`, {method: 'POST'});
            if (response.ok) {
                showToast("Пароль сброшен на 'qwerty'", "success");
            } else {
                showToast("Ошибка при сбросе пароля", "error");
            }
        } catch (e) {
            showToast("Ошибка сети", "error");
        }
    });
}

// Эти функции должны быть ГЛОБАЛЬНЫМИ, чтобы onclick их видел
window.printOrder = function(id) {
    console.log("Запуск печати заказа:", id);
    const url = `/admin/orders/print/${id}`;
    printAction(url);
}

window.printReturn = function(id) {
    console.log("Запуск печати возврата:", id);
    const url = `/admin/returns/print/${id}`;
    printAction(url);
}

function printAction(url) {
    const frame = document.getElementById('printFrame');
    if (!frame) {
        window.open(url, '_blank');
        return;
    }


    // Очистка фрейма перед новой загрузкой
    frame.src = "about:blank";

    setTimeout(() => {
        frame.src = url;
        frame.onload = function() {
            // Печатаем только один раз, когда фрейм загрузился
            try {
                // Добавляем проверку, чтобы не печатать пустой src
                if (frame.src === "about:blank") return;

                setTimeout(() => {
                    frame.contentWindow.focus();
                    frame.contentWindow.print();
                }, 500);
            } catch (e) {
                console.error("Ошибка печати:", e);
                window.open(url, '_blank');
            }
        };
    }, 100);
}

function printRouteSheet() {
    const mId = document.getElementById('route-manager-select').value;
    const date = document.getElementById('route-date-select').value;
    if(!date) return showToast("Выберите дату", "error");

    const url = `/admin/logistic/route-list?managerId=${mId}&date=${date}`;
    printAction(url); // Используем вашу готовую функцию печати
}

// В script.js
// В script.js

function connectWebSocket() {
    // Проверка: загрузилась ли библиотека? (Можно удалить эту проверку, если файлы теперь локальные и загружаются сразу)
    if (typeof SockJS === 'undefined') {
        // console.warn("Библиотека SockJS еще не загружена. Повтор через 1 сек...");
        setTimeout(connectWebSocket, 1000);
        return;
    }

    const socket = new SockJS('/ws-sellion');
    const stompClient = Stomp.over(socket);

    stompClient.debug = null;

    stompClient.connect({}, function (frame) {
        console.log('Уведомления Sellion 2026 подключены');
        stompClient.subscribe('/topic/new-order', function (message) {
            showToast("🔔 " + message.body, "info");

            // СТРОКА ДЛЯ ЗВУКА УДАЛЕНА. Теперь только визуальное уведомление.
            // new Audio('https://www.soundjay.com').play().catch(() => {});

            if (localStorage.getItem('sellion_tab') === 'tab-orders') {
                setTimeout(() => location.reload(), 2000);
            }
        });
    }, function(error) {
        console.error('Ошибка WS:', error);
        setTimeout(connectWebSocket, 5000); // Реконнект
    });
}



async function doInventory() {
    const id = window.currentProductId;
    const product = productsData.find(p => p.id == id);
    const realQty = prompt(`Инвентаризация: ${product.name}. Введите ФАКТИЧЕСКОЕ количество на полке:`, product.stockQuantity);

    if (realQty !== null) {
        const diff = parseInt(realQty) - product.stockQuantity;
        // Отправляем на сервер PUT запрос для обновления остатка и записи в StockMovement
        // ... реализация API запроса
    }
}


// Запустить при старте
document.addEventListener("DOMContentLoaded", () => {
    connectWebSocket();
});





// Функции для печати всего списка заказов/возвратов (для доставщиков)

window.printOrderList = function() {
    const form = document.querySelector('#tab-orders .filter-bar form');
    const mId = form.querySelector('select[name="orderManagerId"]').value;
    const s = form.querySelector('input[name="orderStartDate"]').value;
    const e = form.querySelector('input[name="orderEndDate"]').value;

    const url = `/admin/orders/print-all?orderManagerId=${mId}&orderStartDate=${s}&orderEndDate=${e}`;
    printAction(url);
}

window.printReturnList = function() {
    const form = document.querySelector('#tab-returns .filter-bar form');
    const mId = form.querySelector('select[name="returnManagerId"]').value;
    const s = form.querySelector('input[name="returnStartDate"]').value;
    const e = form.querySelector('input[name="returnEndDate"]').value;

    const url = `/admin/returns/print-all?returnManagerId=${mId}&returnStartDate=${s}&returnEndDate=${e}`;
    printAction(url);
}



document.addEventListener("DOMContentLoaded", async () => {
    // Используем await, чтобы дождаться загрузки списка менеджеров перед переключением вкладок
    await loadManagerIds();
    showTab(localStorage.getItem('sellion_tab') || 'tab-orders');
});

