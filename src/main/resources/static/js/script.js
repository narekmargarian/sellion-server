// Гарантируем, что если сервер не прислал данные, массивы не будут undefined
if (typeof productsData === 'undefined') window.productsData = [];
if (typeof clientsData === 'undefined') window.clientsData = [];
if (typeof ordersData === 'undefined') window.ordersData = [];
if (typeof returnsData === 'undefined') window.returnsData = [];


let tempItems = {};

// --- 1. Навигация и Утилиты ---
function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return console.error(`Модальное окно с ID ${id} не найдено.`);

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';

    const sc = modal.querySelector('.table-container, .order-items-scroll');
    if (sc) sc.scrollTop = 0;
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.remove('active');
    document.body.style.overflow = '';
}


function formatOrderDate(dateVal) {
    if (!dateVal || dateVal === '---') return '---';

    // Обработка объектов Java (если придут)
    if (typeof dateVal === 'object' && dateVal.year) {
        const d = String(dateVal.dayOfMonth || dateVal.day).padStart(2, '0');
        const m = String(dateVal.monthValue || dateVal.month || 1).padStart(2, '0');
        const y = dateVal.year;
        const time = dateVal.hour !== undefined ?
            ` ${String(dateVal.hour).padStart(2, '0')}:${String(dateVal.minute).padStart(2, '0')}` : '';
        return `${d}.${m}.${y}${time}`;
    }

    // Обработка строк (ISO формат 2026-01-20T01:17:00)
    if (typeof dateVal === 'string') {
        // Убираем возможные запятые или слэши от ошибок ввода
        let clean = dateVal.replace(/[,/]/g, '.');

        // Если в строке есть дата и время (содержит T или пробел)
        if (clean.includes('T') || (clean.includes('-') && clean.includes(':'))) {
            const parts = clean.split(/[T ]/);
            const dParts = parts[0].split('-'); // yyyy-mm-dd
            if (dParts.length === 3) {
                const date = `${dParts[2]}.${dParts[1]}.${dParts[0]}`;
                const time = parts[1].substring(0, 5); // hh:mm
                return `${date} ${time}`;
            }
        }

        // Если в строке только дата (yyyy-mm-dd)
        if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) {
            const d = clean.split('-');
            return `${d[2]}.${d[1]}.${d[0]}`;
        }
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

function translateReturnStatus(status) {
    switch (status) {
        case 'CONFIRMED':
            return {text: 'Проведено', class: 'bg-success text-white'};
        case 'SENT':
            return {text: 'Новый', class: 'bg-info text-white'};
        case 'DRAFT':
            return {text: 'Черновик', class: 'bg-warning text-dark'};
        default:
            return {text: status, class: 'bg-secondary text-white'};
    }
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



// TODO NOR HATVAC 20:55-------------------


function updateRowInTable(order) {
    // Находим строку заказа
    const row = document.querySelector(`tr[onclick*="openOrderDetails(${order.id})"]`);
    if (!row) return;

    // Используем поиск по содержимому или смыслу, чтобы не зависеть от порядка колонок
    const cells = row.cells;

    // Дата создания (обычно первая колонка)
    cells[0].innerText = formatOrderDate(order.createdAt);

    // Название магазина (ищем по тексту, если нужно, но тут оставим индексы с защитой)
    if (cells[2]) cells[2].innerText = order.shopName;

    // Сумма
    if (cells[3]) cells[3].innerText = (order.totalAmount || 0).toLocaleString() + ' ֏';

    // Дата доставки
    if (cells[4]) cells[4].innerText = formatOrderDate(order.deliveryDate);

    // Статус (создаем красивый бадж)
    if (cells[5]) {
        const status = order.status || 'NEW';
        let badgeClass = 'bg-primary';
        if (status === 'CONFIRMED') badgeClass = 'bg-success';
        if (status === 'RESERVED') badgeClass = 'bg-info';
        if (status === 'CANCELLED') badgeClass = 'bg-danger';

        cells[5].innerHTML = `<span class="badge ${badgeClass}">${status}</span>`;
    }
}



// Исправленный расчет суммы (итерируем по ID)
function calculateCurrentTempTotal() {
    let total = 0;
    Object.entries(tempItems).forEach(([pId, pQty]) => {
        const prod = (productsData || []).find(p => p.id == pId);
        if (prod) total += prod.price * pQty;
    });
    const totalPriceElement = document.getElementById('order-total-price');
    if (totalPriceElement) {
        totalPriceElement.innerText = "Итого: " + total.toLocaleString() + " ֏";
    }
    return total;
}

function applySingleQty(pId) {
    const input = document.getElementById(`input-qty-${pId}`);
    if (!input) return;

    // Если поле пустое, мы ничего не делаем (ждем пока пользователь введет число)
    if (input.value.trim() === "") return;

    let newVal = parseInt(input.value);
    const product = (productsData || []).find(p => p.id == pId);

    if (isNaN(newVal) || newVal < 0) {
        input.value = tempItems[pId] || 1; // Возвращаем как было при ошибке ввода
        return;
    }

    if (newVal === 0) {
        // Если ввели 0 - удаляем
        delete tempItems[pId];
        showStatus(`Товар удален`);
        renderItemsTable(tempItems, true);
        return;
    }

    const modalTitle = document.getElementById('modal-title').innerText.toLowerCase();
    const isReturn = modalTitle.includes("возврат");

    // Проверка склада
    if (!isReturn && product && newVal > product.stockQuantity) {
        showStatus(`Недостаточно товара! Доступно: ${product.stockQuantity}`, true);
        input.value = product.stockQuantity;
        tempItems[pId] = product.stockQuantity;
    } else {
        tempItems[pId] = newVal;
        showStatus(`Количество обновлено ✅`);
    }

    // Пересчитываем только итоговую сумму без полной перерисовки таблицы (для плавности)
    calculateCurrentTempTotal();
}



function renderItemsTable(itemsMap, isEdit) {
    const container = document.getElementById('table-scroll-container');
    const scrollPos = container ? container.scrollTop : 0;
    const body = document.getElementById('order-items-body');
    if (!body) return;

    let html = '';
    Object.entries(itemsMap).forEach(([pId, qty]) => {
        const pInfo = (productsData || []).find(p => p.id == pId);
        if (!pInfo) return; // Пропускаем, если товар не найден в базе

        const price = pInfo.price || 0;
        const total = price * qty;

        let qtyDisplay = isEdit ?
            `<div style="display:flex; align-items:center; gap:5px;">
                <input type="number" id="input-qty-${pId}" class="qty-input-active" 
                       value="${qty}" min="0" style="width:65px;">
                <button onclick="applySingleQty('${pId}')" class="btn-check-qty">✅</button>
            </div>` : `<b>${qty} шт.</b>`;

        html += `<tr>
            <td>
                ${pInfo.name} 
                ${isEdit ? `<button onclick="removeItemFromEdit('${pId}')" style="color:#ef4444; border:none; background:none; cursor:pointer;">&times;</button>` : ''}
            </td>
            <td>${qtyDisplay}</td>
            <td>${price.toLocaleString()} ֏</td>
            <td style="font-weight:700;">${total.toLocaleString()} ֏</td>
            <td><small>${pInfo.category || '---'}</small></td>
        </tr>`;
    });

    if (isEdit) {
        let options = (productsData || []).map(p => `<option value="${p.id}">${p.name} (${p.price} ֏)</option>`).join('');
        html += `<tr style="background:#f8fafc; position: sticky; bottom: 0;">
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

    body.innerHTML = html;

    if (container) {
        requestAnimationFrame(() => {
            container.scrollTop = scrollPos;
        });
    }
    calculateCurrentTempTotal();
}

function removeItemFromEdit(pId) {
    delete tempItems[pId];
    renderItemsTable(tempItems, true);
}


function openOrderDetails(id) {
    // Используем безопасный поиск
    const order = (ordersData || []).find(o => o.id == id);
    if (!order) return;

    // ИСПРАВЛЕНИЕ: Трансформируем items из массива/объекта в карту {ID: Количество}
    // Это решает ошибку "Cannot deserialize Map key of type java.lang.Long from String"
    tempItems = {};
    if (order.items) {
        // Если order.items пришел как Map (объект) от бэкенда
        Object.entries(order.items).forEach(([key, qty]) => {
            // Если ключ — это имя товара, ищем его ID в productsData
            if (isNaN(key)) {
                const product = (productsData || []).find(p => p.name === key);
                if (product) tempItems[product.id] = qty;
            } else {
                // Если ключ уже ID
                tempItems[key] = qty;
            }
        });
    }

    document.getElementById('modal-title').innerHTML = `Детали операции <span class="badge" style="margin-left:10px;">ЗАКАЗ №${order.id}</span>`;

    const info = document.getElementById('order-info');
    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Магазин:</small><br><b>${order.shopName}</b></div>
            <div><small>Дата заказа:</small><br><b>${formatOrderDate(order.createdAt)}</b></div>
            <div><small>Менеджер:</small><br><b>${order.managerId}</b></div>
        </div>
        <div class="modal-info-row">
            <div><small>Доставка:</small><br><b>${formatOrderDate(order.deliveryDate)}</b></div>
            <div><small>Оплата:</small><br><b>${translatePayment(order.paymentMethod)}</b></div>
            <div><small>Фактура:</small><br><b>${order.needsSeparateInvoice ? 'ДА' : 'НЕТ'}</b></div>
        </div>
    `;

    // Теперь renderItemsTable получит карту с ID и отобразит всё корректно
    renderItemsTable(tempItems, false);

    const totalPriceElement = document.getElementById('order-total-price');
    if (totalPriceElement) {
        totalPriceElement.innerText = "Итого: " + (order.totalAmount || 0).toLocaleString() + " ֏";
    }

    const footer = document.getElementById('order-footer-actions');
    // Логика кнопок футера
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


function openReturnDetails(id) {
    // 1. Безопасный поиск данных (защита от undefined)
    const ret = (returnsData || []).find(r => r.id == id);
    if (!ret) return console.error(`Возврат с ID ${id} не найден.`);

    // 2. ИСПРАВЛЕНИЕ: Трансформируем items в карту {ID: Количество}
    // Это критично для исправления ошибки десериализации на бэкенде
    tempItems = {};
    if (ret.items) {
        Object.entries(ret.items).forEach(([key, qty]) => {
            // Если ключ — это строка (название), ищем соответствующий ID в продуктах
            if (isNaN(key)) {
                const product = (productsData || []).find(p => p.name === key);
                if (product) {
                    tempItems[product.id] = qty;
                } else {
                    console.warn(`Товар "${key}" не найден в справочнике товаров.`);
                }
            } else {
                // Если ключ уже является числовым ID
                tempItems[key] = qty;
            }
        });
    }

    const statusText = ret.status === 'CONFIRMED' ? 'Проведено' : (ret.status === 'DRAFT' ? 'Черновик' : ret.status);
    const statusClass = ret.status === 'CONFIRMED' ? 'bg-success' : 'bg-warning';
    const footer = document.getElementById('order-footer-actions');
    const printBtnHtml = `<button class="btn-primary" style="background:#475569" onclick="printReturn(${ret.id})">🖨 Печать</button>`;
    const displayReason = translateReason(ret.returnReason);

    // 3. Обновляем заголовок
    document.getElementById('modal-title').innerHTML = `
        Детали операции 
        <span class="badge ${statusClass}" style="margin-left:10px;">${statusText}</span>
        <span class="badge" style="margin-left:5px;">ВОЗВРАТ №${ret.id}</span>
    `;

    // 4. Обновляем инфо-блок
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background: #fff1f2; padding: 15px; border-radius: 10px; margin-top: 20px;">
            <div><small>Магазин:</small><br><b>${ret.shopName}</b></div>
            <div><small>Дата возврата:</small><br><b>${formatOrderDate(ret.returnDate)}</b></div>
            <div><small>Причина:</small><br><b style="color:#ef4444;">${displayReason}</b></div>
        </div>
    `;

    // 5. Рендерим таблицу (теперь она работает через ID)
    renderItemsTable(tempItems, false);

    // 6. Обновляем итоговую сумму
    const totalPriceElement = document.getElementById('order-total-price');
    if (totalPriceElement) {
        totalPriceElement.innerText = "Сумма возврата: " + (ret.totalAmount || 0).toLocaleString() + " ֏";
    }

    // 7. Управление кнопками футера
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
            <div style="flex: 1; display: flex; align-items: center; justify-content: center; color: #64748b; font-weight: bold;">
                <span>✓ Операция проведена</span>
            </div>
            ${printBtnHtml}
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
        `;
    }

    openModal('modal-order-view');
}


function addItemToEdit() {
    const selectElement = document.getElementById('add-item-select');
    const productId = selectElement.value;
    const qtyInput = document.getElementById('add-item-qty');
    const qty = parseInt(qtyInput.value) || 1;

    // Безопасный поиск продукта
    const product = (productsData || []).find(p => p.id == productId);

    if (product) {
        const modalTitle = document.getElementById('modal-title').innerText.toLowerCase();
        const isReturn = modalTitle.includes("возврат");

        // Считаем: сколько уже добавлено + сколько добавляем сейчас
        const alreadyInCart = tempItems[product.id] || 0;
        const totalRequested = alreadyInCart + qty;

        // Если это НЕ возврат, проверяем общий остаток
        if (!isReturn && totalRequested > product.stockQuantity) {
            showStatus(`Ошибка: На складе всего ${product.stockQuantity} шт. У вас уже добавлено ${alreadyInCart} шт.`, true);
            return;
        }

        // Добавляем в список по ID
        tempItems[product.id] = totalRequested;

        renderItemsTable(tempItems, true);
        showStatus(`Товар "${product.name}" добавлен`);
        qtyInput.value = 1; // Сброс поля после добавления
    } else {
        showStatus("Выберите товар из списка", true);
    }
}


function getManagerOptionsHTML() {
    // Если список еще не загружен, добавляем хотя бы текущего пользователя или OFFICE
    if (!managerIdList || managerIdList.length === 0) {
        return `<option value="OFFICE">OFFICE (загрузка...)</option>`;
    }
    return managerIdList.map(m => `<option value="${m}">${m}</option>`).join('');
}


function fmt(dateVal) {
    if (!dateVal) return '---';
    // Используем уже готовую у вас функцию форматирования
    return formatOrderDate(dateVal);
}

async function saveNewManualOperation(type) {
    // 1. ПРИНУДИТЕЛЬНЫЙ СБОР ДАННЫХ:
    // Проходим по всем активным инпутам количества и обновляем tempItems перед отправкой.
    // Это гарантирует, что данные, введенные, но не подтвержденные кнопкой, попадут в заказ.
    document.querySelectorAll('.qty-input-active').forEach(input => {
        const pId = input.id.replace('input-qty-', '');
        const val = parseInt(input.value);
        if (!isNaN(val) && val > 0) {
            tempItems[pId] = val;
        } else if (val <= 0) {
            delete tempItems[pId];
        }
    });

    // 2. Получаем дату (с учетом разных возможных ID для заказов и возвратов)
    const dateInput = document.getElementById('new-op-date') || document.getElementById('edit-ret-date');
    const baseDate = dateInput ? dateInput.value : null;

    // 3. Валидация перед отправкой
    if (Object.keys(tempItems).length === 0) {
        showToast("Ошибка: Состав операции пуст!", "error");
        return;
    }

    if (!baseDate) {
        showToast("Пожалуйста, выберите дату!", "error");
        return;
    }

    const url = type === 'order' ? '/api/admin/orders/create-manual' : '/api/returns/sync';

    // Формируем дату создания с текущим временем 2026 года
    const now = new Date();
    const currentTime = now.toTimeString().substring(0, 8); // "hh:mm:ss"
    const formattedDateTime = `${baseDate}T${currentTime}`;

    // 4. Формируем финальный объект данных
    // Важно: calculateCurrentTempTotal() теперь вызывается после сбора данных из инпутов
    const data = {
        shopName: document.getElementById('new-op-shop').value,
        managerId: document.getElementById('new-op-manager').value,
        items: tempItems, // Теперь здесь точно актуальные ID и Qty
        totalAmount: calculateCurrentTempTotal(),
        createdAt: formattedDateTime,
        androidId: "MANUAL-" + Date.now()
    };

    // Специфические поля для Заказа или Возврата
    if (type === 'order') {
        data.comment = document.getElementById('new-op-comment')?.value || "";
        data.deliveryDate = baseDate;
        data.paymentMethod = document.getElementById('new-op-payment').value;
        data.needsSeparateInvoice = document.getElementById('new-op-invoice').value === "true";
    } else {
        data.returnReason = document.getElementById('new-op-reason').value;
        data.returnDate = baseDate;
    }

    try {
        // Для возвратов бэкенд ожидает массив [data], для заказов — один объект data
        const bodyData = type === 'order' ? data : [data];

        const response = await fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(bodyData)
        });

        if (response.ok) {
            showToast("✅ Операция успешно сохранена", "success");
            // Задержка перед релоадом, чтобы пользователь успел увидеть Toast
            setTimeout(() => {
                location.reload();
            }, 1000);
        } else {
            const err = await response.text();
            // Обработка ошибки склада или валидации от бэкенда
            showStatus(err || "Ошибка сохранения", true);
            showToast("Ошибка сервера", "error");
        }
    } catch (e) {
        console.error("Критическая ошибка при сохранении:", e);
        showToast("Ошибка сети: сервер недоступен", "error");
    }
}
















// TODO NOR HATVAC 20:55-------------------


function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    document.getElementById('modal-title').innerText = "Редактирование заказа #" + id;
    const info = document.getElementById('order-info');

    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === order.shopName ? 'selected' : ''}>${c.name}</option>`).join('');
    let paymentOptions = paymentMethods.map(m => {
        const val = (typeof m === 'object') ? m.name : m;
        const label = translatePayment(m);
        return `<option value="${val}" ${order.paymentMethod === val ? 'selected' : ''}>${label}</option>`;
    }).join('');

    // ИСПРАВЛЕНО: Теперь вызываем formatOrderDate вместо прямой вставки объекта
    const formattedDeliveryDate = convertDateToISO(order.deliveryDate);

    info.innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин</label><select id="edit-shop">${clientOptions}</select></div>
            <div><label>Доставка</label><input type="date" id="edit-delivery" value="${formattedDeliveryDate}"></div>
            <div><label>Номер автомобиля</label><input type="text" id="edit-car-number" value="${order.carNumber || ''}"></div>
            <div><label>Оплата</label><select id="edit-payment">${paymentOptions}</select></div>
            <div><label>Отд. Фактура</label>
                <select id="edit-invoice-type">
                    <option value="false" ${!order.needsSeparateInvoice ? 'selected' : ''}>НЕТ</option>
                    <option value="true" ${order.needsSeparateInvoice ? 'selected' : ''}>ДА</option>
            </select>
            </div>
        </div>`;

    setMinDateToday('edit-delivery');
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
        carNumber: document.getElementById('edit-car-number').value,
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

    // Подготовка опций для выпадающих списков
    let reasonOptions = returnReasons.map(r => {
        const val = (typeof r === 'object') ? r.name : r;
        const label = translateReason(r); // Используем вашу функцию перевода
        return `<option value="${val}" ${ret.returnReason === val ? 'selected' : ''}>${label}</option>`;
    }).join('');

    let clientOptions = clientsData.map(c => `<option value="${c.name}" ${c.name === ret.shopName ? 'selected' : ''}>${c.name}</option>`).join('');

    // ИСПРАВЛЕНО: Используем оригинальную дату из БД (формат YYYY-MM-DD)
    // чтобы <input type="date"> мог её прочитать.

    const formattedReturnDate = convertDateToISO(ret.returnDate);

    info.innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин</label><select id="edit-ret-shop">${clientOptions}</select></div>
            <div><label>Дата возврата</label><input type="date" id="edit-ret-date" value="${formattedReturnDate}"></div>
            <div><label>Причина</label><select id="edit-ret-reason">${reasonOptions}</select></div>
        </div>`;

    setMinDateToday('edit-ret-date');
    // ... остальной код функции ...

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


// Обновление строки ВОЗВРАТА
function updateReturnRowInTable(ret) {
    const row = document.querySelector(`tr[onclick*="openReturnDetails(${ret.id})"]`);
    if (row) {
        // Для возвратов обычно показываем дату создания или returnDate
        row.cells[0].innerText = formatOrderDate(ret.returnDate || ret.createdAt);

        row.cells[2].innerText = ret.shopName;
        row.cells[3].innerText = translateReason(ret.returnReason);
        row.cells[4].innerText = (ret.totalAmount || 0).toLocaleString() + ' ֏';

        const status = ret.status || 'DRAFT';
        const badgeClass = status === 'CONFIRMED' ? 'bg-success' : (status === 'SENT' ? 'bg-info' : 'bg-warning');
        row.cells[5].innerHTML = `<span class="badge ${badgeClass}">${status}</span>`;
    }
}


// --- НОВАЯ ЛОГИКА ДЛЯ КЛИЕНТОВ (CLIENTS) ---
function cancelClientEdit(id) {
    openClientDetails(id);
}

// 2. Полная карточка клиента (все поля)
// 2. Полная карточка клиента (все поля)
async function openClientDetails(id) {
    const client = clientsData.find(c => c.id == id);
    if (!client) return;
    window.currentClientId = id;

    document.getElementById('modal-client-title').innerHTML = `Детали клиента <span class="badge">${client.name}</span>`;

    // Установка дат по умолчанию (с 1-го числа текущего месяца по сегодня 2026 года)
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const today = now.toISOString().split('T')[0];

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

        <!-- БЛОК ВЫБОРА ПЕРИОДА (Как в 1С) -->
        <div style="margin-top:20px; background: #f1f5f9; padding: 12px; border-radius: 12px; border: 1px solid #cbd5e1;">
            <label style="font-size: 11px; font-weight: 800; color: var(--text-muted); display:block; margin-bottom:5px;">📅 ПЕРИОД АКТА СВЕРКИ</label>
            <div style="display: flex; gap: 10px; align-items: center;">
                <input type="date" id="statement-start" class="form-control" style="font-size: 12px; height: 30px;" value="${firstDay}">
                <input type="date" id="statement-end" class="form-control" style="font-size: 12px; height: 30px;" value="${today}">
                <button class="btn-primary" style="padding: 5px 15px; font-size: 12px;" onclick="loadClientStatement(${id})">Обновить</button>
            </div>
        </div>
    `;

    const historyContainer = document.getElementById('table-scroll-container-client');
    historyContainer.innerHTML = `
        <div class="table-container" style="max-height: 250px; font-size: 11px; margin-top: 15px;">
            <table class="table">
                <thead>
                    <tr>
                        <th>Дата</th>
                        <th>Тип</th>
                        <th>Сумма</th>
                        <th>Комментарий</th>
                        <th>Остаток</th>
                    </tr>
                </thead>
                <tbody id="client-transactions-body">
                    <tr><td colspan="5" style="text-align:center;">Загрузка истории...</td></tr>
                </tbody>
            </table>
        </div>
    `;

    document.getElementById('client-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#475569" onclick="printClientStatement(${client.id})">🖨 Печать Акта</button>
        <button class="btn-primary" onclick="enableClientEdit()">Изменить данные</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-client-view')">Закрыть</button>
    `;

    openModal('modal-client-view');

    // Первичная загрузка за выбранный период
    loadClientStatement(id);
}

// ФУНКЦИЯ ЗАГРУЗКИ ТРАНЗАКЦИЙ ЗА ПЕРИОД
async function loadClientStatement(id) {
    const start = document.getElementById('statement-start').value;
    const end = document.getElementById('statement-end').value;
    const tbody = document.getElementById('client-transactions-body');
    const scrollContainer = document.getElementById('table-scroll-container-client'); // Контейнер таблицы

    if (!start || !end) return showToast("Выберите даты периода", "error");

    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;">Загрузка данных...</td></tr>';

    try {
        const response = await fetch(`/api/clients/${id}/statement?start=${start}&end=${end}`);
        if (response.ok) {
            const data = await response.json();
            const transactions = data.transactions;

            tbody.innerHTML = transactions.map(tx => {
                const isDebit = tx.type === 'ORDER';
                const color = isDebit ? '#ef4444' : '#10b981';
                return `
                <tr>
                    <td>${fmt(tx.timestamp)}</td>
                    <td><span class="badge" style="background:${color}; color:white;">${tx.type}</span></td>
                    <td style="color:${color}"><b>${isDebit ? '+' : '-'}${tx.amount.toLocaleString()}</b></td>
                    <td><small>${tx.comment || '---'}</small></td>
                    <td style="font-weight:700;">${tx.balanceAfter.toLocaleString()} ֏</td>
                </tr>`;
            }).join('') || '<tr><td colspan="5" style="text-align:center;">За этот период операций не найдено</td></tr>';

            // --- НОВОЕ: Автоматический скролл вниз после загрузки ---
            if (scrollContainer) {
                setTimeout(() => {
                    scrollContainer.scrollTop = scrollContainer.scrollHeight;
                }, 100);
            }
        }
    } catch (e) {
        tbody.innerHTML = '<tr><td colspan="5" style="color:red; text-align:center;">Ошибка загрузки</td></tr>';
    }
}


// ПЕЧАТЬ С УЧЕТОМ ВЫБРАННЫХ ДАТ
window.printClientStatement = function (id) {
    const start = document.getElementById('statement-start').value;
    const end = document.getElementById('statement-end').value;

    if (!start || !end) {
        showToast("Сначала выберите период", "error");
        return;
    }

    const url = `/admin/clients/print-statement/${id}?start=${start}&end=${end}`;
    printAction(url);
};


function enableClientEdit() {
    const client = clientsData.find(c => c.id === window.currentClientId);
    if (!client) return;
    const info = document.getElementById('client-info');
    info.innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин</label><input type="text" id="edit-client-name" value="${client.name}"></div>
            <div><label>Расчетный счет (IBAN)</label><input type="text" id="edit-client-bank" value="${client.bankAccount || ''}"></div>
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
        debt: parseFloat(document.getElementById('edit-client-debt').value) || 0,
        bankAccount: document.getElementById('edit-client-bank').value
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

async function openProductDetails(id) {
    window.currentProductId = id;
    const product = productsData.find(p => p.id == id);
    if (!product) return;

    document.getElementById('modal-product-title').innerHTML = `Детали товара <span class="badge" style="margin-left:10px;">${product.name}</span>`;
    const info = document.getElementById('product-info');

    // ОБЪЕДИНЕННЫЙ БЛОК (Инфо о товаре + Складские данные + Контейнер истории)
    info.innerHTML = `
        <div class="modal-info-row">
            <div><small>Название:</small><br><b>${product.name}</b></div>
            <div><small>Цена продажи:</small><br><b class="price-up">${(product.price || 0).toLocaleString()} ֏</b></div>
            <div><small>Категория:</small><br><b>${product.category || '---'}</b></div>
        </div>
        <div class="modal-info-row">
            <div><small>Остаток на складе:</small><br><b>${product.stockQuantity || 0} шт.</b></div>
            <div><small>Штрих-код:</small><br><b>${product.barcode || '---'}</b></div>
            <div><small>В коробке:</small><br><b>${product.itemsPerBox || '---'} шт.</b></div>
        </div>
        <!-- Секция истории (теперь она не затрется) -->
        <div id="product-history-container" style="margin-top:20px;">
            <label style="font-size: 11px; font-weight: 800; color: var(--text-muted);">📜 ИСТОРИЯ ДВИЖЕНИЯ ТОВАРА</label>
            <div class="table-container" style="max-height: 200px; font-size: 11px; margin-top: 10px;">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Дата</th>
                            <th>Тип</th>
                            <th>Кол-во</th>
                            <th>Причина</th>
                        </tr>
                    </thead>
                    <tbody id="product-history-body">
                        <tr><td colspan="4" style="text-align:center;">Загрузка истории...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;

    // 2. Загружаем историю с сервера (адрес /api/products/{name}/history у нас уже есть в контроллере)
    try {
        const response = await fetch(`/api/products/${encodeURIComponent(product.name)}/history`);
        if (response.ok) {
            const history = await response.json();
            const tbody = document.getElementById('product-history-body');
            tbody.innerHTML = history.map(h => `
                <tr>
                    <td>${fmt(h.timestamp)}</td>
                    <td><span class="badge">${h.type}</span></td>
                    <td style="color:${h.quantityChange > 0 ? '#10b981' : '#ef4444'}">
                        <b>${h.quantityChange > 0 ? '+' : ''}${h.quantityChange}</b>
                    </td>
                    <td><small>${h.reason || '---'}</small></td>
                </tr>
            `).join('') || '<tr><td colspan="4" style="text-align:center;">Движений не найдено</td></tr>';
        }
    } catch (e) {
        console.error("Ошибка загрузки истории:", e);
        document.getElementById('product-history-body').innerHTML = '<tr><td colspan="4" style="color:red;">Ошибка загрузки</td></tr>';
    }

    // 3. Футер с кнопкой Инвентаризации
    const footer = document.getElementById('product-footer-actions');
    footer.innerHTML = `
        <button class="btn-primary" style="background:#f59e0b" onclick="doInventory()">⚖️ Инвентаризация</button>
        <button class="btn-primary" onclick="enableProductEdit()">Изменить товар</button>
        <button class="btn-danger" onclick="deleteProduct(${product.id})">Удалить</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-product-view')">Закрыть</button>
    `;

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
            <div><label>Код SKU (для 1С)</label><input type="text" id="edit-product-hsn" value="${product.hsnCode || ''}"></div>

        </div>
        <div class="modal-info-row">
            <div><label>Остаток</label><input type="number" id="edit-product-qty" value="${product.stockQuantity || 0}"></div>
            <div><label>Штрих-код</label><input type="text" id="edit-product-barcode" value="${product.barcode || ''}"></div>
            <div><label>Упаковка</label><input type="number" id="edit-product-perbox" value="${product.itemsPerBox || 0}"></div>
            <div><label>Ед. измерения (шт/кг/кор)</label><input type="text" id="edit-product-unit" value="${product.unit || 'шт'}"></div>

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
        category: document.getElementById('edit-product-category').value,
        hsnCode: document.getElementById('edit-product-hsn').value,
        unit: document.getElementById('edit-product-unit').value
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






async function openCreateOrderModal() {
    await loadManagerIds();
    tempItems = {};
    document.getElementById('modal-title').innerText = "Создание нового заказа";

    let clientOptions = clientsData.map(c => `<option value="${c.name}">${c.name}</option>`).join('');
    // ИСПРАВЛЕНО: Теперь переменная определена
    let managerOptions = getManagerOptionsHTML();
    const today = new Date().toLocaleDateString('en-CA');
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин:</label><select id="new-op-shop">${clientOptions}</select></div>
            <div><label>Менеджер:</label><select id="new-op-manager">${managerOptions}</select></div>
               <div><label>Доставка:</label><input type="date" id="new-op-date" value="${today}"></div>
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
        </div>
        `;

    setMinDateToday('new-op-date');
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

    const today = new Date().toLocaleDateString('en-CA');
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row">
            <div><label>Магазин:</label><select id="new-op-shop">${clientOptions}</select></div>
            <div><label>Менеджер:</label><select id="new-op-manager">${managerOptions}</select></div>
            <div><label>Причина:</label><select id="new-op-reason">${reasonOptions}</select></div>
             <div><label>Дата возврата::</label><input type="date" id="edit-ret-date" value="${today}"></div>
           
          
        </div>`;

    setMinDateToday('edit-ret-date');
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


// --- ИСПРАВЛЕННАЯ ФУНКЦИЯ ПЕРЕКЛЮЧЕНИЯ ТАБОВ ---
function showTab(tabId) {
    // 1. Стандартная логика переключения
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    const target = document.getElementById(tabId);
    if (target) target.classList.add('active');

    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const btnId = tabId.replace('tab-', 'btn-');
    const activeBtn = document.getElementById(btnId);
    if (activeBtn) activeBtn.classList.add('active');

    localStorage.setItem('sellion_tab', tabId);

    // 2. Вызываем обновление только если мы перешли на главную вкладку
    if (tabId === 'tab-main') {
        updateDashboardStats();
    }
}

// --- ИСПРАВЛЕННАЯ ФУНКЦИЯ ОБНОВЛЕНИЯ СТАТИСТИКИ (БЕЗ ОШИБОК) ---
function updateDashboardStats() {
    // Проверяем наличие элементов перед тем как что-то в них писать
    const statAvgCheck = document.getElementById('stat-avg-check');
    const statPendingOrders = document.getElementById('stat-pending-orders');
    const onlineList = document.getElementById('online-users-list');

    // Расчет данных
    const totalSum = ordersData.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
    const avg = ordersData.length > 0 ? (totalSum / ordersData.length) : 0;
    const pendingCount = ordersData.filter(o => o.status === 'NEW' || o.status === 'RESERVED').length;

    // Безопасная запись данных
    if (statAvgCheck) {
        statAvgCheck.innerText = Math.round(avg).toLocaleString() + " ֏";
    }

    if (statPendingOrders) {
        statPendingOrders.innerText = pendingCount;
    }

    if (onlineList) {
        onlineList.innerHTML = `<span class="badge" style="background:#dcfce7; color:#166534;">● Администратор (Вы)</span>`;
        const operators = ["Оператор Арам", "Оператор Анна"];
        operators.forEach(op => {
            onlineList.innerHTML += `<span class="badge" style="background:#f1f5f9; color:#475569;">● ${op}</span>`;
        });
    }
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

// //todo Toast//
// function showToast(text, type = 'info') {
//     const container = document.getElementById('toast-container');
//     const toast = document.createElement('div');
//     toast.className = `toast-msg toast-${type}`;
//     const icon = type === 'success' ? '✅' : (type === 'error' ? '❌' : 'ℹ️');
//     toast.innerHTML = `<span>${icon}</span> <span>${text}</span>`;
//     container.appendChild(toast);
//     setTimeout(() => {
//         toast.style.opacity = '0';
//         setTimeout(() => toast.remove(), 500);
//     }, 4000);
// }


function showToast(text, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) {
        console.error("Контейнер для тостов не найден!");
        return;
    }

    const toast = document.createElement('div');
    // Добавляем класс, который мы только что определили в CSS
    toast.className = `toast-msg toast-${type}`;

    const icon = type === 'success' ? '✅' : (type === 'error' ? '❌' : 'ℹ️');
    toast.innerHTML = `<span>${icon}</span> <span>${text}</span>`;
    container.appendChild(toast);

    // Убедитесь, что начальная видимость не '0'
    toast.style.opacity = '1';

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
window.printOrder = function (id) {
    console.log("Запуск печати заказа:", id);
    const url = `/admin/orders/print/${id}`;
    printAction(url);
}

window.printReturn = function (id) {
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

    // 1. Сначала «очищаем» фрейм
    frame.src = "about:blank";

    // 2. Устанавливаем обработчик события загрузки ПЕРЕД тем как задать URL
    frame.onload = function () {
        // Пропускаем, если это очистка фрейма
        if (frame.src === "about:blank" || frame.contentWindow.location.href === "about:blank") return;

        // Небольшая пауза, чтобы стили 2026 года успели примениться
        setTimeout(() => {
            try {
                frame.contentWindow.focus();
                frame.contentWindow.print();
            } catch (e) {
                console.error("Ошибка печати через iframe, открываю в новом окне:", e);
                window.open(url, '_blank');
            }
        }, 300);

        // Сбрасываем обработчик, чтобы он не сработал повторно
        frame.onload = null;
    };

    // 3. Загружаем реальный URL
    frame.src = url;
}


function printRouteSheet() {
    const mId = document.getElementById('route-manager-select').value;
    const date = document.getElementById('route-date-select').value;
    if (!date) return showToast("Выберите дату", "error");

    const url = `/admin/logistic/route-list?managerId=${mId}&date=${date}`;
    printAction(url); // Используем вашу готовую функцию печати
}

let stompClient = null;

function connectWebSocket() {
    // Если уже подключены - не создаем дубликат
    if (stompClient !== null && stompClient.connected) return;

    const socket = new SockJS('/ws-sellion');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Отключаем лишний спам в консоли

    stompClient.connect({}, function (frame) {
        console.log('✅ Sellion Realtime Connected');
        stompClient.subscribe('/topic/new-order', function (message) {
            showToast("🔔 " + message.body, "info");

            // Получаем текущую активную вкладку
            const currentTab = localStorage.getItem('sellion_tab');

            // Если пользователь сейчас смотрит вкладку заказов ИЛИ возвратов — обновляем страницу
            if (currentTab === 'tab-orders' || currentTab === 'tab-returns') {
                console.log("Обновление данных для вкладки: " + currentTab);
                setTimeout(() => location.reload(), 1500);
            }
        });
    }, function (error) {
        console.log('🔄 WS Reconnecting...');
        setTimeout(connectWebSocket, 5000);
    });
}

async function deleteProduct(id) {
    showConfirmModal("Удалить товар?", "Он будет скрыт из списков, но останется в старых заказах.", async () => {
        const response = await fetch(`/api/products/${id}`, {method: 'DELETE'});
        if (response.ok) {
            showToast("Товар успешно удален (скрыт)!", "success");
            location.reload();
        } else {
            showToast("Ошибка удаления", "error");
        }
    });
}

async function deleteClient(id) {
    showConfirmModal("Удалить клиента?", "Он будет скрыт из списков, но останется в старых счетах и заказах.", async () => {
        const response = await fetch(`/api/clients/${id}`, {method: 'DELETE'});
        if (response.ok) {
            showToast("Клиент успешно удален (скрыт)!", "success");
            location.reload();
        } else {
            showToast("Ошибка удаления", "error");
        }
    });
}


// Открывает нашу новую красивую модалку
function doInventory() {
    const id = window.currentProductId;
    const product = productsData.find(p => p.id == id);
    if (!product) return;

    // Заполняем поля в модалке данными
    document.getElementById('inv-product-id').value = id;
    document.getElementById('inv-product-name').innerText = product.name;
    document.getElementById('inv-actual-qty').value = product.stockQuantity;
    document.getElementById('inv-reason').value = 'Плановая проверка';

    openModal('modal-inventory');
}

/**
 * Сворачивание и разворачивание категории товаров
 * @param {string} categoryClass - уникальный ID категории (напр. 'cat-0')
 */
function toggleCategory(categoryClass) {
    // Находим все строки товаров, у которых есть этот класс
    const rows = document.getElementsByClassName(categoryClass);
    const icon = document.getElementById('icon-' + categoryClass);

    if (rows.length === 0) return;

    // Проверяем состояние по первой строке
    const isHidden = rows[0].style.display === "none";

    for (let i = 0; i < rows.length; i++) {
        rows[i].style.display = isHidden ? "" : "none";
    }

    // Меняем иконку
    if (icon) {
        icon.innerText = isHidden ? "▼" : "▶";
    }
}


// Отправляет данные с модалки на сервер
async function submitInventoryAdjustment() {
    const id = document.getElementById('inv-product-id').value;
    const newQty = parseInt(document.getElementById('inv-actual-qty').value);
    const reason = document.getElementById('inv-reason').value;

    if (isNaN(newQty) || newQty < 0 || !reason) {
        showToast("Введите корректное количество и причину!", "error");
        return;
    }

    try {
        const response = await fetch(`/api/admin/products/${id}/inventory`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({newQty: newQty, reason: reason})
        });

        if (response.ok) {
            showToast("Склад скорректирован ✅", "success");
            location.reload();
        } else {
            const error = await response.json();
            showToast(error.message || "Ошибка при сохранении", "error");
        }
    } catch (e) {
        showToast("Ошибка сети", "error");
    }
}


// Функция для скачивания отдельного типа отчета
function downloadExcel(type) {
    const start = document.getElementById('report-start').value;
    const end = document.getElementById('report-end').value;

    if (!start || !end) {
        showToast("Выберите период!", "error");
        return;
    }

    const url = type === 'orders' ?
        `/api/reports/excel/orders-detailed?start=${start}&end=${end}` :
        `/api/reports/excel/returns-detailed?start=${start}&end=${end}`;

    // Используем fetch для контроля ответа
    fetch(url)
        .then(response => {
            if (response.ok) {
                // Если OK, обрабатываем скачивание файла
                return response.blob().then(blob => {
                    const downloadUrl = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.style.display = 'none';
                    a.href = downloadUrl;
                    a.download = `${type}_report_${start}.xlsx`;
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(downloadUrl);
                    showToast('Отчет успешно скачан!', 'success');
                });
            } else {
                // Если ошибка (например, 404), показываем toast
                return response.json().then(data => {
                    showToast(data.message || 'Ошибка сервера', 'error');
                });
            }
        })
        .catch(error => {
            showToast('Ошибка сети при скачивании отчета.', 'error');
        });
}

// Функция для отправки отчета по Email (использует чекбоксы)
function sendToEmail() {
    const start = document.getElementById('report-start').value;
    const end = document.getElementById('report-end').value;
    const email = document.getElementById('report-email').value;

    if (!start || !end || !email) {
        showToast("Выберите период и введите email!", "error");
        return;
    }

    // Собираем выбранные типы отчетов из чекбоксов
    const types = [];
    if (document.getElementById('check-orders').checked) {
        types.push('orders');
    }
    if (document.getElementById('check-returns').checked) {
        types.push('returns');
    }

    if (types.length === 0) {
        showToast("Выберите хотя бы один тип отчета (заказы или возвраты)!", "error");
        return;
    }

    // Формируем тело запроса для POST (URLSearchParams удобен для form-data)
    const params = new URLSearchParams();
    params.append('start', start);
    params.append('end', end);
    params.append('email', email);
    types.forEach(type => params.append('types', type)); // Добавляем каждый тип как отдельный параметр

    fetch('/api/reports/excel/send-to-accountant', {
        method: 'POST',
        body: params
    })
        .then(response => response.json())
        .then(data => {
            if (data.message) {
                showToast(data.message, 'success');
            } else if (data.error) {
                showToast(data.error, 'error');
            }
        })
        .catch(error => {
            showToast('Ошибка сети при отправке отчета.', 'error');
        });
}

// Убедитесь, что эта функция showToast() у вас определена
// function showToast(text, type = 'info') { ... }


async function saveAllSettings() {
    const settings = {
        COMPANY_NAME: document.getElementById('set-COMPANY_NAME').value,
        ACCOUNTANT_EMAIL: document.getElementById('set-ACCOUNTANT_EMAIL').value,
        COMPANY_INN: document.getElementById('set-COMPANY_INN').value,
        COMPANY_IBAN: document.getElementById('set-COMPANY_IBAN').value,
        COMPANY_ADDRESS: document.getElementById('set-COMPANY_ADDRESS').value
    };

    try {
        const response = await fetch('/api/admin/settings/update-all', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(settings)
        });
        if (response.ok) {
            showToast("Настройки успешно применены!", "success");
        }
    } catch (e) {
        showToast("Ошибка при сохранении", "error");
    }
}


/**
 * Преобразует различные форматы даты в формат ISO YYYY-MM-DD, необходимый для <input type="date">.
 */
function convertDateToISO(dateVal) {
    if (!dateVal) return '';

    // Если это уже строка ISO "2026-01-20", возвращаем её как есть
    if (typeof dateVal === 'string' && /^\d{4}-\d{2}-\d{2}/.test(dateVal)) {
        return dateVal.substring(0, 10);
    }

    // Если это объект Java/Hibernate с полями year, month, dayOfMonth
    if (typeof dateVal === 'object' && dateVal.year) {
        const y = dateVal.year;
        const m = String(dateVal.monthValue || dateVal.monthIndex + 1).padStart(2, '0');
        const d = String(dateVal.dayOfMonth || dateVal.day).padStart(2, '0');
        return `${y}-${m}-${d}`;
    }

    // Если это русская строка типа "20 января 2026", пытаемся распарсить
    if (typeof dateVal === 'string' && dateVal.includes('января')) {
        const parts = dateVal.split(' ');
        const monthMap = {
            'января': '01', 'февраля': '02', 'марта': '03', 'апреля': '04', 'мая': '05', 'июня': '06',
            'июля': '07', 'августа': '08', 'сентября': '09', 'октября': '10', 'ноября': '11', 'декабря': '12'
        };
        const month = monthMap[parts[1]];
        return `${parts[2]}-${month}-${String(parts[0]).padStart(2, '0')}`;
    }

    // В крайнем случае пытаемся создать объект Date и форматировать его
    try {
        const d = new Date(dateVal);
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    } catch (e) {
        return '';
    }
}

/**
 * Устанавливает минимальную дату для поля ввода <input type="date">,
 * предотвращая выбор прошедших дней.
 */
function setMinDateToday(inputId) {
    const dateInput = document.getElementById(inputId);
    if (dateInput) {
        // Получаем текущую дату в формате YYYY-MM-DD
        const today = new Date();
        const yyyy = today.getFullYear();
        const mm = String(today.getMonth() + 1).padStart(2, '0');
        const dd = String(today.getDate()).padStart(2, '0');
        const todayISO = `${yyyy}-${mm}-${dd}`;

        // Устанавливаем минимальное значение (min="2026-01-20")
        dateInput.min = todayISO;
    }
}


// Функции для печати всего списка заказов/возвратов (для доставщиков)

window.printOrderList = function () {
    const form = document.querySelector('#tab-orders .filter-bar form');
    const mId = form.querySelector('select[name="orderManagerId"]').value;
    const s = form.querySelector('input[name="orderStartDate"]').value;
    const e = form.querySelector('input[name="orderEndDate"]').value;

    const url = `/admin/orders/print-all?orderManagerId=${mId}&orderStartDate=${s}&orderEndDate=${e}`;
    printAction(url);
}

window.printReturnList = function () {
    const form = document.querySelector('#tab-returns .filter-bar form');
    const mId = form.querySelector('select[name="returnManagerId"]').value;
    const s = form.querySelector('input[name="returnStartDate"]').value;
    const e = form.querySelector('input[name="returnEndDate"]').value;

    const url = `/admin/returns/print-all?returnManagerId=${mId}&returnStartDate=${s}&returnEndDate=${e}`;
    printAction(url);
}


// Авто-исправление разделителей в полях ввода дат
document.addEventListener('input', function (e) {
    if (e.target.classList.contains('date-input-check')) {
        // Заменяем запятые и слэши на точки мгновенно
        e.target.value = e.target.value.replace(/[,/]/g, '.');
    }
});

// Пример того, как должна выглядеть проверка перед отправкой на сервер
function validateDate(dateStr) {
    // Регулярное выражение для dd.mm.yyyy
    const regex = /^\d{2}\.\d{2}\.\d{4}$/;
    if (!regex.test(dateStr)) {
        alert("Ошибка! Введите дату в формате ДД.ММ.ГГГГ (например 20.01.2026)");
        return false;
    }
    return true;
}

document.addEventListener("DOMContentLoaded", async () => {
    console.log("Sellion ERP 2026 initialized");

    // 1. WebSocket подключаем один раз
    if (typeof connectWebSocket === 'function') connectWebSocket();

    // 2. Менеджеров грузим сразу при старте, чтобы модалки открывались мгновенно
    if (typeof loadManagerIds === 'function') {
        try {
            await loadManagerIds();
        } catch (e) {
            console.error("Ошибка загрузки менеджеров");
        }
    }

    // 3. Восстановление вкладки
    const lastTab = localStorage.getItem('sellion_tab') || 'tab-main';
    if (typeof showTab === 'function') showTab(lastTab);

    // 4. Форматирование всех дат и статусов в таблицах (делегирование)
    const formatInitialData = () => {
        document.querySelectorAll('.js-date-format').forEach(cell => {
            const raw = cell.innerText;
            if (raw && raw !== '---' && !raw.includes('.')) {
                cell.innerText = formatOrderDate(raw);
            }
        });

        document.querySelectorAll('.js-reason-translate').forEach(cell => {
            cell.innerText = translateReason(cell.innerText);
        });

        document.querySelectorAll('.js-status-translate').forEach(cell => {
            const statusInfo = translateReturnStatus(cell.innerText);
            cell.innerHTML = `<span class="badge ${statusInfo.class}">${statusInfo.text}</span>`;
        });
    };

    formatInitialData();
});


