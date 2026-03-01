if (typeof productsData === 'undefined') window.productsData = [];
if (typeof clientsData === 'undefined') window.clientsData = [];
if (typeof ordersData === 'undefined') window.ordersData = [];
if (typeof returnsData === 'undefined') window.returnsData = [];


let tempItems = {};
let managerIdList = [];
let tempPromoItems = {};
let currentPromoData = null;

function roundHalfUp(num) {

    return Math.round((num + Number.EPSILON) * 100) / 100;
}



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

    // ОЧИСТКА ГЛОБАЛЬНЫХ ДАННЫХ
    tempItems = {};
    window.currentOrderPromos = {}; // Очищаем акции
    console.log("Данные сессии очищены");
}

function translatePayment(m) {
    if (!m) return '';
    const val = (typeof m === 'object') ? (m.name || m) : m;
    const mapping = {
        'CASH': 'Наличный',
        'TRANSFER': 'Перевод'
    };
    return mapping[val] || val;
}

function translateReason(r) {
    if (!r) return '';
    // Обработка случая, если пришел объект или строка
    const val = (typeof r === 'object') ? (r.name || r) : r;

    const mapping = {
        'EXPIRED': 'Просрочка',
        'DAMAGED': 'Поврежденная упаковка',
        'WAREHOUSE': 'На склад',
        'CORRECTION_ORDER': 'Корректировка заказа',    // Добавлено
        'CORRECTION_RETURN': 'Корректировка возврата', // Добавлено
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


function getManagerOptionsHTML(selectedManager = "") {
    // 1. Собираем доступные данные
    const webList = (typeof fullManagersList !== 'undefined') ? fullManagersList : [];
    const apiList = (typeof window.managerIdList !== 'undefined') ? window.managerIdList : [];

    // 2. Объединяем их и убираем дубликаты (Set), чтобы точно ничего не потерять
    // Это гарантирует, что если в одном списке есть "Офис", а в другом нет — он появится.
    const combinedList = [...new Set([...webList, ...apiList])];

    // 3. Если списки вдруг пусты (ошибка загрузки), добавляем "Офис" вручную как страховку
    if (combinedList.length === 0) {
        combinedList.push("Офис");
    }

    // 4. Формируем HTML
    return combinedList.map(m =>
        `<option value="${m}" ${m === selectedManager ? 'selected' : ''}>${m}</option>`
    ).join('');
}


function syncTempItems(items) {
    let synced = {};
    if (!items) return synced;

    Object.entries(items).forEach(([key, qty]) => {
        // Если ключ — название (не число), ищем ID. Если ID — оставляем как есть.
        const productId = isNaN(key)
            ? (productsData || []).find(p => p.name === key)?.id
            : key;

        if (productId) synced[productId] = qty;
    });
    return synced;
}


function printSelectedOperations(type) {
    const checkboxClass = type === 'order' ? '.order-print-check' : '.return-print-check';
    const selectedIds = Array.from(document.querySelectorAll(`${checkboxClass}:checked`)).map(cb => cb.value);

    if (selectedIds.length === 0) {
        showToast("Сначала выберите записи галочкой!", "error");
        return;
    }

    const frame = document.getElementById('printFrame');
    const url = type === 'order' ? '/admin/orders/print-batch' : '/admin/returns/print-batch';

    // Очищаем предыдущие обработчики, создавая клон фрейма (самый надежный метод)
    const newFrame = frame.cloneNode(true);
    frame.parentNode.replaceChild(newFrame, frame);

    // Вешаем событие ОДИН раз
    newFrame.addEventListener('load', function() {
        if (newFrame.contentWindow.location.href === "about:blank") return;

        setTimeout(() => {
            newFrame.contentWindow.focus();
            newFrame.contentWindow.print();
        }, 300);
    }, { once: true });

    submitAsPost(url, selectedIds, 'printFrame');
}


function submitAsPost(url, ids, targetName) {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = url;
    form.target = targetName; // Здесь должно быть имя (name) фрейма

    // CSRF
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    if (csrfMeta) {
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = '_csrf';
        csrfInput.value = csrfMeta.content;
        form.appendChild(csrfInput);
    }

    // IDs
    ids.forEach(id => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'ids';
        input.value = id;
        form.appendChild(input);
    });

    document.body.appendChild(form);
    form.submit();

    // Удаляем форму из DOM через секунду
    setTimeout(() => document.body.removeChild(form), 1000);
}


async function confirmReturn(id) {
    const ret = (returnsData || []).find(r => r.id == id);
    if (!ret) return showToast("Возврат не найден", "error");

    // 1. Проверяем, есть ли несохраненные изменения в ценах или количестве
    // Если мы в режиме редактирования (есть инпуты), сначала сохраняем
    const isEditMode = !!document.querySelector('.item-price-input');

    if (isEditMode) {
        showToast("Сначала сохраните изменения перед подтверждением", "info");
        return;
    }

    // 2. Используем модальное окно подтверждения
    showConfirmModal(
        "Провести возврат?",
        `Сумма ${window.currentOrderTotal.toLocaleString()} ֏ будет вычтена из долга клиента. Склад будет обновлен автоматически (если применимо).`,
        async () => {
            try {
                // Блокируем кнопку, чтобы избежать двойного клика
                const confirmBtn = document.querySelector('#confirm-modal-ok');
                if (confirmBtn) confirmBtn.disabled = true;

                // 3. Отправка запроса на подтверждение
                const response = await fetch(`/api/admin/returns/${id}/confirm`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });

                const result = await response.json();

                if (response.ok) {
                    // Формируем детальное сообщение
                    let successMsg = `Возврат #${id} проведен. `;
                    if (result.stockUpdated) {
                        successMsg += "Товар возвращен на склад.";
                    }

                    showToast(successMsg, "success");

                    // 4. Обновляем статус в локальном массиве (для красоты до релоада)
                    ret.status = 'CONFIRMED';

                    setTimeout(() => {
                        location.reload();
                    }, 800);
                } else {
                    showToast(result.error || "Ошибка при подтверждении", "error");
                    if (confirmBtn) confirmBtn.disabled = false;
                }
            } catch (e) {
                console.error("Confirm return error:", e);
                showToast("Ошибка сети: проверьте соединение", "error");
            }
        }
    );
}


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


async function openClientDetails(id) {
    const client = clientsData.find(c => c.id == id);
    if (!client) return;
    window.currentClientId = id;

    document.getElementById('modal-client-title').innerHTML = `Детали клиента <span class="badge">${client.name}</span>`;

    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const today = now.toISOString().split('T')[0];

    const info = document.getElementById('client-info');
    // Стили для рядов сетки
    const rowStyle = 'display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 15px;';

    info.innerHTML = `
        <!-- РЯД 1 -->
        <div style="${rowStyle}">
            <div><small>Название:</small><br><b>${client.name}</b></div>
            <div><small>Владелец:</small><br><b>${client.ownerName || '---'}</b></div>
            <div><small>ИНН:</small><br><b>${client.inn || '---'}</b></div>
            <div><small>Категория:</small><br><b>${client.category || '---'}</b></div>
        </div>

        <!-- РЯД 2 -->
        <div style="${rowStyle}">
            <div><small>Адрес:</small><br><b>${client.address || '---'}</b></div>
            <div><small>Менеджер:</small><br><b>${client.managerId || '---'}</b></div>
            <div><small>Текущий долг:</small><br><b class="price-down">${(client.debt || 0).toLocaleString()} ֏</b></div>
            <div><small>День маршрута:</small><br><b>${client.routeDay || '---'}</b></div>
        </div>

        <!-- РЯД 3 (ДОБАВЛЕН ПРОЦЕНТ) -->
        <div style="${rowStyle}">
            <div><small>Название банка:</small><br><b>${client.bankName || '---'}</b></div>
            <div><small>Расчетный счет:</small><br><b>${client.bankAccount || '---'}</b></div>
            <div><small>Телефон:</small><br><b>${client.phone || '---'}</b></div>
            <!-- НОВОЕ ПОЛЕ -->
            <div>
                <small style="color: var(--accent); font-weight: 800;">ПРОЦЕНТ МАГАЗИНА:</small><br>
                <b style="font-size: 14px; color: var(--accent);">${client.defaultPercent || 0}%</b>
            </div>
        </div>

        <!-- БЛОК ВЫБОРА ПЕРИОДА -->
        <div style="margin-top:10px; background: #f1f5f9; padding: 12px; border-radius: 12px; border: 1px solid #cbd5e1;">
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
        <button class="btn-primary" style="background:#64748b" onclick="enableClientEdit()">Изменить данные</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-client-view')">Закрыть</button>
    `;

    openModal('modal-client-view');
    loadClientStatement(id);
}

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

    const managerOptions = managerIdList.map(m =>
        `<option value="${m}" ${m === client.managerId ? 'selected' : ''}>${m}</option>`
    ).join('');

    // Стили для обеспечения сетки 4 колонки
    const rowStyle = 'display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 15px;';

    info.innerHTML = `
         <!-- Ряд 1: Название, Владелец, ИНН, Категория -->
         <div style="${rowStyle}">
            <div><label>Название</label><input type="text" id="edit-client-name" value="${client.name}"></div>
            <div><label>Владелец</label><input type="text" id="edit-client-owner" value="${client.ownerName || ''}"></div>
            <div><label>ИНН</label><input type="text" id="edit-client-inn" value="${client.inn || ''}"></div>
            <div><label>Категория</label><input type="text" id="edit-client-category" value="${client.category || ''}"></div>
        </div>

        <!-- Ряд 2: Адрес, Менеджер, Долг, День маршрута -->
        <div style="${rowStyle}">
            <div><label>Адрес</label><input type="text" id="edit-client-address" value="${client.address || ''}"></div>
            <div><label>Менеджер</label><select id="edit-client-manager" class="form-select">${managerOptions}</select></div>
            <div><label>Долг (֏)</label><input type="number" id="edit-client-debt" value="${client.debt || 0}"></div>
            <div><label>День маршрута</label><input type="text" id="edit-client-route-day" value="${client.routeDay || ''}"></div>
        </div>

        <!-- Ряд 3: Название банка, Расчетный счет, Телефон, ПРОЦЕНТ -->
        <div style="${rowStyle}">
            <div><label>Название банка</label><input type="text" id="edit-client-bank-name" value="${client.bankName || ''}"></div>
            <div><label>Расчетный счет</label><input type="text" id="edit-client-bank" value="${client.bankAccount || ''}"></div>
            <div><label>Телефон</label><input type="text" id="edit-client-phone" value="${client.phone || ''}"></div>
            <!-- НОВОЕ ПОЛЕ ПРОЦЕНТА -->
            <div>
                <label style="color: var(--accent); font-weight: 800;">Процент магазина (%)</label>
                <input type="number" id="edit-client-percent"
                       value="${client.defaultPercent || 0}"
                       step="0.1"
                       style="border: 2px solid var(--accent); font-weight: bold;">
            </div>
        </div>
    `;

    document.getElementById('client-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveClientChanges(${client.id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openClientDetails(${client.id})">Отмена</button>`;
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


async function submitCreateProduct() {
    const data = {
        category: document.getElementById('new-p-cat').value,
        price: parseFloat(document.getElementById('new-p-price').value) || 0,
        hsnCode: document.getElementById('new-p-hsn').value,
        expiryDate: document.getElementById('new-p-expiry').value,
        name: document.getElementById('new-p-name').value,
        stockQuantity: parseInt(document.getElementById('new-p-qty').value) || 0,
        barcode: document.getElementById('new-p-code').value,
        itemsPerBox: parseInt(document.getElementById('new-p-box').value) || 1,
        unit: document.getElementById('new-p-unit').value
    };

    if (!data.name) return showToast("Укажите название товара!", "error");

    const response = await fetch('/api/products/create', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]')?.content
        },
        body: JSON.stringify(data)
    });

    if (response.ok) {
        showToast("Товар успешно создан", "success");
        location.reload();
    } else {
        const err = await response.json();
        showToast("Ошибка: " + (err.error || "Не удалось сохранить"), "error");
    }
}


async function openCreateClientModal() {
    // 1. Сначала открываем окно
    openModal('modal-client');

    // 2. Сброс поля процента на 0 при каждом новом открытии (чтобы не оставалось от старых вводов)
    const percentInput = document.getElementById('new-client-percent');
    if (percentInput) {
        percentInput.value = "0";
    }

    // 3. Находим select менеджеров
    const select = document.getElementById('new-client-manager-id');
    if (!select) {
        console.error("Критическая ошибка: Select #new-client-manager-id не найден!");
        return;
    }

    // 4. Если список менеджеров пуст, ждем загрузки
    if (!window.managerIdList || window.managerIdList.length === 0) {
        select.innerHTML = '<option value="">⏳ Загрузка...</option>';
        await loadManagerIds();
    }

    // 5. Финальное заполнение списка менеджеров
    const finalList = window.managerIdList || [];
    if (finalList.length > 0) {
        select.innerHTML = finalList.map(m => `<option value="${m}">${m}</option>`).join('');
    } else {
        select.innerHTML = '<option value="OFFICE">OFFICE (дефолт)</option>';
    }
}


function applyClientFilters() {
    const searchVal = document.getElementById('search-clients').value;
    const categoryVal = document.getElementById('filter-client-category').value;

    const url = new URL(window.location.href);
    url.searchParams.set('activeTab', 'tab-clients');
    url.searchParams.set('clientSearch', searchVal);
    url.searchParams.set('clientCategory', categoryVal);
    url.searchParams.set('clientPage', '0'); // Сброс на 1 страницу

    window.location.href = url.toString();
}


async function loadManagerIds() {
    try {
        const response = await fetch('/api/public/managers');
        if (response.ok) {
            const data = await response.json();
            // Сохраняем глобально, чтобы было доступно везде
            window.managerIdList = data;
            managerIdList = data;
            console.log("Список менеджеров из Enum загружен:", window.managerIdList);
            return data;
        } else {
            console.error("Не удалось загрузить список менеджеров из Enum.");
            return [];
        }
    } catch (e) {
        console.error("Ошибка сети при загрузке Enum менеджеров.");
        return [];
    }
}


async function openCreateOrderModal() {
    // await loadManagerIds();
    tempItems = {};

    // СБРОС СОСТОЯНИЙ (чтобы кнопки не прыгали и Итого не пропадало)
    const footer = document.getElementById('order-footer-actions');
    const totalEl = document.getElementById('order-total-price');
    if (footer) {
        footer.style.display = 'flex';
        footer.style.justifyContent = 'flex-end'; // Кнопки вправо
        footer.style.gap = '10px';
    }
    if (totalEl) {
        totalEl.style.display = 'block'; // Показываем Итого
        totalEl.innerText = "Итого: 0 ֏";
    }

    const dates = getSmartDeliveryDates();

    document.getElementById('modal-title').innerText = "🛒 Создание нового заказа";
    let managerOptions = getManagerOptionsHTML();

    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row" style="display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 10px; background: #f8fafc; padding: 15px; border-radius: 10px;">
            <div style="grid-column: span 1;">
                <label>МАГАЗИН (Поиск):</label>
                <input type="text" id="new-op-shop" class="form-control" list="clients-datalist" placeholder="Введите название...">
                <datalist id="clients-datalist"></datalist>
            </div>
            <div><label>МЕНЕДЖЕР:</label><select id="new-op-manager" class="form-select">${managerOptions}</select></div>
            <div><label>НОМЕР АВТО:</label><input type="text" id="new-op-car" class="form-control" placeholder="35XX000"></div>
        </div>

        <div class="modal-info-row" style="display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-top:10px; background: #f8fafc; padding: 15px; border-radius: 10px;">
            <div>
                <label>ДОСТАВКА:</label>
                <input type="date" id="new-op-date" class="form-control" min="${dates.min}" value="${dates.default}">
            </div>
            <div><label>ОПЛАТА:</label><select id="new-op-payment" class="form-select"><option value="CASH">Наличный</option><option value="TRANSFER">Перевод</option></select></div>
            <div><label>ФАКТУРА:</label>
                <select id="new-op-separate" class="form-select" style="border: 1px solid #6366f1;">
                    <option value="false">Общая</option><option value="true">Раздельная</option>
                </select>
            </div>

            <div style="display: none;">
                <input type="number" id="new-op-percent" value="0">
            </div>

            <div><label>КОММЕНТАРИЙ:</label><input type="text" id="new-op-comment" class="form-control" placeholder="..."></div>
        </div>`;

    initSmartClientSearch('new-op-shop', 'clients-datalist');

    // Перерисовываем таблицу товаров
    renderItemsTable(tempItems, true);

    if (footer) {
        footer.innerHTML = `
            <button class="btn-primary" style="background:#10b981" onclick="saveNewManualOperation('order')">Создать заказ</button>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Отмена</button>
        `;
    }

    openModal('modal-order-view');
}


async function cancelOrder(id) {
    // 1. Используем подтверждение
    showConfirmModal("Отменить заказ?", "Товар вернется на склад, суммы заказа будут обнулены.", async () => {
        try {
            // Блокируем кнопку в модальном окне (если есть доступ к DOM), чтобы избежать дублей
            const confirmBtn = document.querySelector('.modal-confirm-btn');
            if (confirmBtn) confirmBtn.disabled = true;

            // 2. Выполняем запрос
            // Благодаря вашему новому fetch, если статус 400 (уже отменен) или 403 (нет прав),
            // выполнение ПРЕРВЕТСЯ здесь и уйдет в catch.
            const response = await fetch(`/api/admin/orders/${id}/cancel`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value
                }
            });

            // 3. Обработка успешного ответа (только для 200 OK)
            const result = await response.json();

            showToast(result.message || "Заказ успешно отменен", "success");

            // 4. Перезагрузка страницы
            setTimeout(() => {
                location.reload();
            }, 800);

        } catch (e) {
            // Блок остается пустым для уведомлений, так как глобальный fetch
            // уже показал тост: "Этот заказ уже был отменен ранее" или "Доступ запрещен".
            console.warn("Отмена заказа отклонена:", e.message);

            // Если заказ уже отменен, обновляем страницу через паузу, чтобы данные стали актуальными
            if (e.message && e.message.includes("уже был отменен")) {
                setTimeout(() => location.reload(), 1500);
            }
        }
    });
}

async function showOrderHistory(orderId) {
    const body = document.getElementById('order-items-body');
    const footer = document.getElementById('order-footer-actions');
    const title = document.getElementById('modal-title');
    const totalEl = document.getElementById('order-total-price');

    try {
        body.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:20px;">⌛ Загрузка истории...</td></tr>';
        const response = await fetch(`/api/admin/audit/order/${orderId}`);
        if (!response.ok) throw new Error("Ошибка загрузки");
        const logs = await response.json();

        title.innerHTML = `📜 ИСТОРИЯ ИЗМЕНЕНИЙ #${orderId}`;

        if (logs.length === 0) {
            body.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:40px; color:#94a3b8;">История пуста</td></tr>';
        } else {
            body.innerHTML = logs.map(log => `
                <tr style="border-bottom: 1px solid #f1f5f9;">
                    <td style="white-space: nowrap; color: #64748b; font-size: 12px;">${formatDate(log.timestamp)}</td>
                    <td><span class="badge" style="background:#e0f2fe; color:#0369a1;">${log.username}</span></td>
                    <td style="font-weight: 600; color: #1e293b;">${log.action}</td>
                    <td colspan="2" style="font-size: 13px; color: #475569; font-style: italic;">${log.details || '---'}</td>
                </tr>`).join('');
        }

        if (totalEl) totalEl.style.display = 'none';

        // ИСПРАВЛЕНИЕ: Гарантируем выравнивание кнопки "Назад" вправо
        footer.style.display = 'flex';
        footer.style.justifyContent = 'flex-end';
        footer.innerHTML = `
            <button class="btn-primary" style="background:#64748b; min-width: 200px; padding: 10px;" onclick="restoreModalState(${orderId})">
                🔙 ВЕРНУТЬСЯ К ДЕТАЛЯМ
            </button>
        `;

    } catch (e) {
        showToast("Ошибка сети", "error");
        restoreModalState(orderId); // Возвращаемся, если произошла ошибка
    }
}

function restoreModalState(orderId) {
    const totalEl = document.getElementById('order-total-price');
    if (totalEl) totalEl.style.display = 'block'; // Возвращаем Итого
    openOrderDetails(orderId); // Перерисовываем детали заказа
}

function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return showToast("Данные не найдены", "error");

    window.currentOrderPromos = order.appliedPromoItems || {};
    window.tempItemPrices = order.itemPrices || {};
    tempItems = syncTempItems(order.items);

    const isWriteOff = order.shopName === 'СПИСАНИЕ' || order.type === 'WRITE_OFF';
    const discountPercent = order.discountPercent || 0;

    // 1. Заголовок
    document.getElementById('modal-title').innerHTML = isWriteOff
        ? `<span style="color: #ef4444;">СПИСАНИЕ №${order.id}</span>`
        : `ЗАКАЗ №${order.id} <span class="badge" style="background: #6366f1; margin-left: 10px;">${discountPercent}%</span>`;

    const info = document.getElementById('order-info');

    if (isWriteOff) {
        info.innerHTML = `
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; background: #f8fafc; padding: 15px; border-radius: 10px; margin-top: 15px; border: 1px solid #e2e8f0;">
                <div><small style="color: #94a3b8; font-weight: 700;">МАГАЗИН:</small><br><b style="color: #cbd5e1;">null</b></div>
                <div><small style="color: #64748b; font-weight: 700;">МЕНЕДЖЕР:</small><br><b>${order.managerId || 'Офис'}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">ДАТА СОЗДАНИЯ:</small><br><b>${formatDate(order.createdAt)}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #94a3b8; font-weight: 700;">ДОСТАВКА:</small><br><b style="color: #cbd5e1;">---</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #94a3b8; font-weight: 700;">АВТО:</small><br><b style="color: #cbd5e1;">---</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #94a3b8; font-weight: 700;">ПРОЦЕНТ МАГАЗИНА:</small><br><b style="color: #cbd5e1;">0%</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #94a3b8; font-weight: 700;">ОПЛАТА:</small><br><b style="color: #cbd5e1;">---</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #94a3b8; font-weight: 700;">ФАКТУРА:</small><br><b style="color: #1e293b;">Общая</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">КОММЕНТАРИЙ:</small><br><b>${order.comment || '---'}</b></div>
            </div>
            <input type="hidden" id="order-discount-percent" value="0">`;
    } else {
        info.innerHTML = `
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; background: #f8fafc; padding: 15px; border-radius: 10px; margin-top: 15px; border: 1px solid #e2e8f0;">
                <div><small style="color: #64748b; font-weight: 700;">МАГАЗИН:</small><br><b style="color: #1e293b;">${order.shopName}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">МЕНЕДЖЕР:</small><br><b>${order.managerId}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">ДАТА СОЗДАНИЯ:</small><br><b>${formatDate(order.createdAt)}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">ДОСТАВКА:</small><br><b>${formatDate(order.deliveryDate).split(' ')[0]}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">АВТО:</small><br><b>${order.carNumber || '---'}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #6366f1; font-weight: 800;">ПРОЦЕНТ МАГАЗИНА:</small><br><b style="color:#6366f1;">${discountPercent}%</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">ОПЛАТА:</small><br><b>${translatePayment(order.paymentMethod)}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">ФАКТУРА:</small><br><b>${order.needsSeparateInvoice ? 'Раздельная' : 'Общая'}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">КОММЕНТАРИЙ:</small><br><i>${order.comment || '---'}</i></div>
            </div>
            <input type="hidden" id="order-discount-percent" value="${discountPercent}">`;
    }

    renderItemsTable(tempItems, false);

    let btnsHtml = `<button class="btn-primary" style="background:#6366f1" onclick="showOrderHistory(${order.id})">📜 История</button>`;

    if (isWriteOff) {
        btnsHtml += `<button class="btn-primary" style="background:#475569" onclick="printOrder(${order.id})">🖨 Печать</button>`;
        btnsHtml += `<button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        btnsHtml += `<button class="btn-primary" style="background:#475569" onclick="printOrder(${order.id})">🖨 Печать</button>`;
        if (!order.invoiceId) {
            btnsHtml += `
                <button class="btn-primary" onclick="enableOrderEdit(${order.id})">✏️ Изменить</button>
                <button class="btn-primary" style="background:#ef4444" onclick="cancelOrder(${order.id})">🗑 Отмена</button>`;
        } else {
            btnsHtml += `<div style="color:#15803d; font-weight:700; padding: 0 10px;">✅ ПРОВЕРЕНО</div>`;
        }
        btnsHtml += `<button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    }

    const footer = document.getElementById('order-footer-actions');

    // ИСПРАВЛЕНИЕ: Добавляем распорку (spacer), если это списание.
    // Она заберет всё место слева и прижмет кнопки вправо.
    const spacer = isWriteOff ? '<div style="margin-right: auto;"></div>' : '';
    footer.innerHTML = spacer + btnsHtml;

    if (footer) {
        footer.style.display = 'flex';
        footer.style.width = '100%';
        footer.style.justifyContent = 'flex-end';
        footer.style.alignItems = 'center';
        footer.style.gap = '10px';
    }

    const totalEl = document.getElementById('order-total-price');
    if (totalEl) totalEl.style.display = isWriteOff ? 'none' : 'block';

    openModal('modal-order-view');
}


function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return showToast("Ошибка: Заказ не найден", "error");

    tempItems = syncTempItems(order.items);

    // ДОБАВЛЕНО: Синхронизируем уже примененные акции заказа для корректного отображения
    window.currentOrderPromos = order.appliedPromoItems || {};

    const dates = getSmartDeliveryDates();

    document.getElementById('modal-title').innerText = "📝 Редактирование заказа #" + id;

    const currentPercent = order.discountPercent || 0;

    const info = document.getElementById('order-info');
    info.innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; background: #f1f5f9; padding: 15px; border-radius: 10px;">
            <div>
                <label>МАГАЗИН (Поиск):</label>
                <!-- ИСПРАВЛЕНО: input + datalist вместо простого select -->
                <input type="text" id="edit-shop" class="form-control"
                       list="edit-order-clients-datalist"
                       value="${order.shopName}"
                       placeholder="Введите название...">
                <datalist id="edit-order-clients-datalist"></datalist>
            </div>
            <div><label>ДОСТАВКА</label>
                <input type="date" id="edit-delivery" class="form-control"
                       min="${dates.min}"
                       value="${convertDateToISO(order.deliveryDate)}">
            </div>
            <div><label>АВТО</label><input type="text" id="edit-car-number" class="form-control" value="${order.carNumber || ''}"></div>

            <div style="margin-top:10px;"><label>ОПЛАТА</label>
                <select id="edit-payment" class="form-select">
                    <option value="CASH" ${order.paymentMethod === 'CASH' ? 'selected' : ''}>Наличный</option>
                    <option value="TRANSFER" ${order.paymentMethod === 'TRANSFER' ? 'selected' : ''}>Перевод</option>
                </select>
            </div>
            <div style="margin-top:10px;"><label>ФАКТУРА</label>
                <select id="edit-invoice-type" class="form-select">
                    <option value="false" ${!order.needsSeparateInvoice ? 'selected' : ''}>Общая</option>
                    <option value="true" ${order.needsSeparateInvoice ? 'selected' : ''}>Раздельная</option>
                </select>
            </div>

            <!-- СКРЫТОЕ ПОЛЕ ПРОЦЕНТА -->
            <div style="display: none;">
                <input type="number" id="order-discount-percent" value="${currentPercent}">
            </div>

            <div style="margin-top:10px;"><label>КОММЕНТАРИЙ</label>
                <input type="text" id="edit-comment" class="form-control" value="${order.comment || ''}">
            </div>
        </div>`;

    // АКТИВАЦИЯ ПОИСКА: Инициализируем живой поиск для нового поля
    initSmartClientSearch('edit-shop', 'edit-order-clients-datalist');

    renderItemsTable(tempItems, true);

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveFullChanges(${id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openOrderDetails(${id})">Отмена</button>`;
}


function recalculateWithPercent() {
    const percent = parseFloat(document.getElementById('order-discount-percent').value) || 0;
    let total = 0;

    // Проходим по всем строкам товаров в таблице редактирования
    document.querySelectorAll('#order-items-body tr').forEach(row => {
        const priceBase = parseFloat(row.dataset.basePrice); // Нужно сохранить базовую цену в data-атрибут
        const qty = parseInt(row.querySelector('.qty-input')?.value) || 0;

        // Расчет: Цена + Процент (может быть отрицательным для скидки)
        const newPrice = priceBase + (priceBase * (percent / 100));
        const subtotal = newPrice * qty;

        row.querySelector('.item-price').innerText = newPrice.toLocaleString() + " ֏";
        row.querySelector('.item-subtotal').innerText = subtotal.toLocaleString() + " ֏";

        total += subtotal;
    });

    document.getElementById('order-total-price').innerText = `Итого (с уч. ${percent}%): ${total.toLocaleString()} ֏`;
}


async function openCreateReturnModal() {
    // await loadManagerIds();
    tempItems = {};
    // Инициализируем пустой объект цен, чтобы renderItemsTable не выдавала ошибок
    window.tempItemPrices = {};
    const dates = getSmartDeliveryDates();

    document.getElementById('modal-title').innerText = "🔄 Новый возврат";
    let managerOptions = getManagerOptionsHTML();
    let reasonOptions = returnReasons.map(r => `<option value="${r.name || r}">${translateReason(r)}</option>`).join('');

    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-row" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; background: #fff1f2; padding: 15px; border-radius: 10px;">
            <div>
                <label>МАГАЗИН (Поиск):</label>
                <input type="text" id="new-op-shop" class="form-control" list="returns-clients-datalist" placeholder="Введите название...">
                <datalist id="returns-clients-datalist"></datalist>
            </div>
            <div><label>МЕНЕДЖЕР:</label><select id="new-op-manager" class="form-select">${managerOptions}</select></div>
            <div><label>НОМЕР АВТО:</label><input type="text" id="new-op-car" class="form-control" placeholder="111xx11"></div>
        </div>
        <div class="modal-info-row" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top:10px; background: #fff1f2; padding: 15px; border-radius: 10px;">
            <div><label>ПРИЧИНА:</label><select id="new-op-reason" class="form-select">${reasonOptions}</select></div>
            <div><label>ДАТА ВОЗВРАТА:</label><input type="date" id="new-op-date" class="form-control" min="${dates.min}" value="${dates.default}"></div>
            <div><label>КОММЕНТАРИЙ:</label><input type="text" id="new-op-comment" class="form-control" placeholder="..."></div>
        </div>`;

    initSmartClientSearch('new-op-shop', 'returns-clients-datalist');
    renderItemsTable(tempItems, true);

    const totalEl = document.getElementById('order-total-price');
    if (totalEl) {
        totalEl.style.display = 'block'; // Возвращаем видимость, если до этого открывали списание
        totalEl.innerText = "Итого: 0 ֏";
    }

    document.getElementById('order-footer-actions').style.justifyContent = 'flex-end'; // Выравнивание вправо
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#ef4444" onclick="saveNewManualOperation('return')">Создать возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Отмена</button>
    `;
    openModal('modal-order-view');
}


function initSmartClientSearch(inputId, datalistId) {
    const input = document.getElementById(inputId);
    const datalist = document.getElementById(datalistId);
    let fullClientsData = [];

    const updateSearch = async () => {
        const query = input.value.trim();
        try {
            const response = await fetch(`/api/clients/search-fast?keyword=${encodeURIComponent(query)}`);
            const clients = await response.json();
            fullClientsData = clients;
            datalist.innerHTML = clients.map(c => `<option value="${c.name}">`).join('');
        } catch (err) { console.error("Ошибка поиска клиентов:", err); }
    };

    input.addEventListener('input', updateSearch);
    input.addEventListener('focus', updateSearch);

    input.addEventListener('change', () => {
        const val = input.value.trim();
        const selectedClient = fullClientsData.find(c => c.name === val);

        if (selectedClient) {
            const percentInput = document.getElementById('new-op-percent') || document.getElementById('order-discount-percent');
            if (percentInput) {
                const clientPercent = selectedClient.defaultPercent || 0;
                percentInput.value = clientPercent;
                showToast(`Магазин: ${selectedClient.name} (Процент: ${clientPercent}%)`, "success");

                if (typeof recalculateAllPricesByPercent === 'function') {
                    recalculateAllPricesByPercent();
                }
            }
            // Сбрасываем стили ошибки при корректном выборе
            input.style.border = "";
            input.style.backgroundColor = "";
        }
    });

    // ИСПРАВЛЕННАЯ ВАЛИДАЦИЯ
    input.addEventListener('blur', () => {
        const val = input.value.trim();
        if (val === "") {
            input.style.border = "";
            input.style.backgroundColor = "";
            return;
        }

        // 1. Проверяем режим (Мягкая валидация для Редактирования и Возврата)
        const modalTitle = document.getElementById('modal-title')?.innerText.toUpperCase() || "";
        const isSoftMode = modalTitle.includes("РЕДАКТИРОВАНИЕ") || modalTitle.includes("ВОЗВРАТ");

        const exists = fullClientsData.some(c => c.name === val);
        if (!exists) {
            if (isSoftMode) {
                // МЯГКИЙ РЕЖИМ: Только красим рамку и фон, НЕ стираем текст
                showToast("Внимание: Магазин не найден в списке!", "info");
                input.style.border = "2px solid #ef4444";
                input.style.backgroundColor = "#fef2f2";
            } else {
                // ЖЕСТКИЙ РЕЖИМ (Создание): Стираем текст, как раньше
                showToast("Ошибка: Выберите магазин из списка!", "error");
                input.value = "";
                input.style.border = "2px solid red";
                input.style.backgroundColor = "";
            }
        } else {
            // Если все верно — очищаем стили
            input.style.border = "";
            input.style.backgroundColor = "";
        }
    });
}



function toggleSelectAll(className, source) {
    document.querySelectorAll(`.${className}`).forEach(cb => cb.checked = source.checked);
}


function getCurrentTimeFormat() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    return `${hours}:${minutes}:${seconds}`;
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



function showTab(tabId) {
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

function updateDashboardStats() {
    const statAvgCheck = document.getElementById('stat-avg-check');
    const statPendingOrders = document.getElementById('stat-pending-orders');
    const onlineList = document.getElementById('online-users-list');

    // Настройки формата: 1 знак после запятой
    const f = { minimumFractionDigits: 1, maximumFractionDigits: 1 };

    // Расчет данных
    const totalSum = ordersData.reduce((sum, o) => sum + (o.totalAmount || 0), 0);

    // ИСПРАВЛЕНО: Средний чек теперь с точностью до 0.1
    const avg = ordersData.length > 0 ? parseFloat((totalSum / ordersData.length).toFixed(1)) : 0;

    const pendingCount = ordersData.filter(o => o.status === 'NEW' || o.status === 'RESERVED').length;

    // Безопасная запись данных
    if (statAvgCheck) {
        // ИСПРАВЛЕНО: Заменил Math.round на toLocaleString с параметрами формата
        statAvgCheck.innerText = avg.toLocaleString(undefined, f) + " ֏";
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
    // ВАЖНО: usersData должен быть доступен глобально (как clientsData)
    const user = usersData.find(u => u.id == id);
    if (!user) return;

    window.currentEditingUserId = id;
    const modalId = 'modal-client-view';

    document.getElementById('modal-client-title').innerHTML = `
        Редактирование сотрудника <span class="badge">${user.username}</span>
    `;

    const info = document.getElementById('client-info');
    info.innerHTML = `
        <div class="modal-info-row" style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
            <div>
                <label>Логин (Username)</label>
                <input type="text" id="edit-u-username" value="${user.username}">
            </div>
            <div>
                <label>Полное ФИО</label>
                <input type="text" id="edit-u-fullname" value="${user.fullName}">
            </div>
        </div>
        <div class="modal-info-row" style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-top: 10px;">
            <div>
                <label>Роль</label>
                <select id="edit-u-role" class="form-select">
                    <option value="OPERATOR" ${user.role === 'OPERATOR' ? 'selected' : ''}>Оператор</option>
                    <option value="ACCOUNTANT" ${user.role === 'ACCOUNTANT' ? 'selected' : ''}>Бухгалтер</option>
                    <option value="ADMIN" ${user.role === 'ADMIN' ? 'selected' : ''}>Админ</option>
                </select>
            </div>
            <div>
                <label>Новый пароль (оставьте пустым, чтобы не менять)</label>
                <input type="password" id="edit-u-password" placeholder="********">
            </div>
        </div>
    `;

    document.getElementById('client-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="submitEditUser(${user.id})">Сохранить изменения</button>
        <button class="btn-danger" style="background:#ef4444" onclick="deleteUser(${user.id})">Удалить</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-client-view')">Отмена</button>
    `;

    openModal(modalId);
}

async function deleteUser(id) {
    showConfirmModal("Удалить сотрудника?", "Доступ в систему будет полностью заблокирован.", async () => {
        try {
            const response = await fetch(`/api/admin/users/${id}`, {method: 'DELETE'});
            if (response.ok) {
                showToast("Сотрудник удален", "success");
                location.reload();
            } else {
                showToast("Ошибка при удалении", "error");
            }
        } catch (e) {
            showToast("Ошибка сети", "error");
        }
    });
}


async function submitEditUser(id) {
    const passwordValue = document.getElementById('edit-u-password').value;

    const data = {
        id: id,
        username: document.getElementById('edit-u-username').value,
        fullName: document.getElementById('edit-u-fullname').value,
        role: document.getElementById('edit-u-role').value
    };

    // Добавляем пароль в объект только если он был введен
    if (passwordValue && passwordValue.trim() !== "") {
        data.password = passwordValue;
    }

    try {
        const response = await fetch(`/api/admin/users/edit/${id}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });

        if (response.ok) {
            showToast("Данные сотрудника обновлены", "success");
            location.reload();
        } else {
            showToast("Ошибка при сохранении", "error");
        }
    } catch (e) {
        console.error(e);
        showToast("Ошибка сети", "error");
    }
}


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


function openCreateUserModal() {
    openModal('modal-user-create');
}

async function submitCreateUser() {
    const username = document.getElementById('new-u-username').value.trim();
    const fullName = document.getElementById('new-u-fullname').value.trim();
    const role = document.getElementById('new-u-role').value;
    const password = document.getElementById('new-u-password').value;

    // Валидация на стороне фронтенда
    if (!username || !fullName || !password) {
        showToast("Заполните все поля, включая пароль!", "error");
        return;
    }

    const data = {
        username: username,
        fullName: fullName,
        role: role,
        password: password // Сервер зашифрует этот пароль сам через BCrypt
    };

    try {
        const response = await fetch('/api/admin/users/create', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });

        if (response.ok) {
            showToast("Сотрудник успешно добавлен", "success");
            // Вместо полной перезагрузки можно просто закрыть модалку,
            // но location.reload() — самый надежный способ обновить таблицу пользователей
            location.reload();
        } else {
            // Пытаемся прочитать текст ошибки от сервера
            const errorData = await response.json().catch(() => ({}));
            showToast(errorData.message || "Ошибка при сохранении пользователя", "error");
        }
    } catch (e) {
        console.error("Ошибка при создании пользователя:", e);
        showToast("Ошибка сети или сервера", "error");
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


window.printOrder = function (id) {
    console.log("Запуск печати заказа:", id);
    const url = `/admin/orders/print/${id}`;
    printAction(url);
}

window.printAction = function(url) {
    const frame = document.getElementById('printFrame');
    if (!frame) return;

    // 1. ОЧИСТКА
    frame.onload = null;
    frame.src = "about:blank";

    // 2. ПРОВЕРКА ДОСТУПА ПЕРЕД ПЕЧАТЬЮ
    // Вместо прямой вставки в src, сначала проверяем, есть ли у пользователя права
    fetch(url, { method: 'GET' })
        .then(response => {
            // Если fetch вернул 200, значит доступ есть и страница готова
            showToast("⏳ Подготовка документа...", "info");

            setTimeout(() => {
                frame.src = url;

                frame.onload = function() {
                    if (frame.contentWindow.location.href.includes("about:blank")) return;

                    // 3. РЕНДЕРИНГ И ПЕЧАТЬ
                    setTimeout(() => {
                        try {
                            frame.contentWindow.focus();
                            frame.contentWindow.print();
                            frame.onload = null; // Удаляем обработчик после успеха
                        } catch (e) {
                            console.error("Ошибка печати:", e);
                            // Фоллбек: если фрейм заблокирован, открываем в новом окне
                            // window.open(url, '_blank');
                        }
                    }, 500);
                };
            }, 100);
        })
        .catch(error => {
            // Если прав нет (403), сработает ваш новый глобальный fetch
            // и покажет "Доступ запрещен". Здесь ничего делать не нужно.
            console.warn("Печать отменена: нет доступа или ошибка сети");
        });
};

window.printOrder = (id) => window.printAction(`/admin/orders/print/${id}`);
window.printReturn = (id) => window.printAction(`/admin/returns/print/${id}`);


window.printOrderList = () => {
    const manager = document.querySelector('select[name="orderManagerId"]').value;
    const start = document.querySelector('input[name="orderStartDate"]').value;
    const end = document.querySelector('input[name="orderEndDate"]').value;
    printAction(`/admin/orders/print-all?orderManagerId=${manager}&orderStartDate=${start}&orderEndDate=${end}`);
};


function printRouteSheet() {
    const mId = document.getElementById('route-manager-select').value;
    const date = document.getElementById('route-date-select').value;
    if (!date) return showToast("Выберите дату", "error");

    const url = `/admin/logistic/route-list?managerId=${mId}&date=${date}`;
    printAction(url);
}

let stompClient = null;


function connectWebSocket() {
    if (stompClient !== null && stompClient.connected) return;

    const socket = new SockJS('/ws-sellion');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Отключаем лог в консоли для чистоты

    stompClient.connect({}, function (frame) {
        console.log('Sellion Realtime Connected [2026]');

        stompClient.subscribe('/topic/new-order', function (notification) {
            const data = JSON.parse(notification.body);

            showToast("🔔 " + (data.message || "Поступили новые данные"), "info");

            const pendingOrdersEl = document.getElementById('stat-pending-orders');
            if (pendingOrdersEl) {
                let currentCount = parseInt(pendingOrdersEl.innerText) || 0;
                pendingOrdersEl.innerText = currentCount + (data.count || 1);
                // Добавляем эффект пульсации для привлечения внимания
                pendingOrdersEl.style.color = "var(--accent)";
                setTimeout(() => pendingOrdersEl.style.color = "", 2000);
            }

            const activeTab = localStorage.getItem('activeTab') || 'tab-main';

            if (activeTab === 'tab-orders' || activeTab === 'tab-returns') {
                const refreshBtn = document.querySelector(`#${activeTab} button[title="Обновить данные"]`);
                if (refreshBtn) {
                    refreshBtn.classList.add('btn-pulse'); // Добавьте этот класс в CSS для мигания
                    console.log("Новые данные доступны. Кнопка обновления подсвечена.");
                } else {
                    console.log("Авто-обновление через 3 сек...");
                    setTimeout(() => location.reload(), 3000);
                }
            }
        });
    }, function (error) {
        console.warn('🔄 Соединение потеряно. Повтор через 5 секунд...');
        stompClient = null;
        setTimeout(connectWebSocket, 5000);
    });
}

async function deleteProduct(id) {
    showConfirmModal("Удалить товар?", "Он будет скрыт...", async () => {
        try {
            const response = await fetch(`/api/products/${id}`, {method: 'DELETE'});
            // Если мы здесь, значит fetch прошел успешно (status 200)
            showToast("Товар успешно удален (скрыт)!", "success");
            location.reload();
        } catch (e) {
            // Ошибка уже показана глобальным перехватчиком
            console.log("Удаление отменено из-за прав");
        }
    });
}


async function openProductDetails(id) {
    const p = productsData.find(prod => prod.id == id);
    if (!p) return;
    window.currentProductId = id;

    document.getElementById('modal-product-title').innerHTML = `ТОВАР: <span class="badge" style="background:var(--accent)">${p.name}</span>`;

    const info = document.getElementById('product-info');
    info.innerHTML = `
        <div class="modal-info-container" style="margin-top:15px;">
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; background: #f8fafc; padding: 15px; border-radius: 10px; border: 1px solid #e2e8f0; margin-bottom: 10px;">
                <div><small style="color: #64748b; font-weight: 700;">КАТЕГОРИЯ:</small><br><b>${p.category || '---'}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">ЦЕНА:</small><br><b class="price-up">${(p.price || 0).toLocaleString()} ֏</b></div>
                <div><small style="color: #64748b; font-weight: 700;">КОД SKU (1С):</small><br><b style="font-family: monospace;">${p.hsnCode || '---'}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">СРОК ГОДНОСТИ:</small><br><b>${p.expiryDate ? formatDate(p.expiryDate) : '---'}</b></div>
            </div>
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; background: #fff; padding: 15px; border-radius: 10px; border: 1px solid #e2e8f0;">
                <div><small style="color: #64748b; font-weight: 700;">ОСТАТОК:</small><br><span class="badge ${p.stockQuantity > 10 ? 'bg-light text-dark' : 'bg-danger text-white'}">${p.stockQuantity || 0} шт.</span></div>
                <div><small style="color: #64748b; font-weight: 700;">ШТРИХ-КОД:</small><br><b style="font-family: monospace;">${p.barcode || '---'}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">УПАКОВКА:</small><br><b>${p.itemsPerBox || 1} шт.</b></div>
                <div><small style="color: #64748b; font-weight: 700;">ЕД. ИЗМЕРЕНИЯ:</small><br><b class="text-primary">${p.unit || 'шт'}</b></div>
            </div>
        </div>
    `;

    document.getElementById('product-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#f59e0b" onclick="doInventory()">⚖️ Инвентарь</button>
        <button class="btn-primary" onclick="enableProductEdit()">✏️ Изменить</button>
        <button class="btn-primary" style="background:#ef4444;" onclick="deleteProduct(${p.id})">🗑 Удалить</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-product-view')">Закрыть</button>
    `;
    openModal('modal-product-view');
}


function enableProductEdit() {
    const p = productsData.find(prod => prod.id == window.currentProductId);
    const info = document.getElementById('product-info');

    info.innerHTML = `
        <div class="modal-info-container" style="margin-top:15px;">
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; background: #f1f5f9; padding: 15px; border-radius: 10px; margin-bottom: 10px;">
                <div><label class="label-small">КАТЕГОРИЯ</label><input type="text" id="edit-product-category" class="form-control" value="${p.category || ''}"></div>
                <div><label class="label-small">ЦЕНА</label><input type="number" id="edit-product-price" class="form-control" value="${p.price}"></div>
                <div><label class="label-small">КОД SKU</label><input type="text" id="edit-product-hsn" class="form-control" value="${p.hsnCode || ''}"></div>
                <div><label class="label-small">СРОК ГОДНОСТИ</label><input type="date" id="edit-product-expiry" class="form-control" value="${convertDateToISO(p.expiryDate)}"></div>
            </div>
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; background: #fff; padding: 15px; border-radius: 10px; border: 1px solid #e2e8f0;">
                <div><label class="label-small">НАЗВАНИЕ</label><input type="text" id="edit-product-name" class="form-control" value="${p.name}"></div>
                <div><label class="label-small">ОСТАТОК</label><input type="number" id="edit-product-qty" class="form-control" value="${p.stockQuantity}"></div>
                <div><label class="label-small">ШТРИХ-КОД</label><input type="text" id="edit-product-barcode" class="form-control" value="${p.barcode || ''}"></div>
                <div style="display: flex; gap: 5px;">
                    <div style="flex:1"><label class="label-small">УПАКОВКА</label><input type="number" id="edit-product-perbox" class="form-control" value="${p.itemsPerBox}"></div>
                    <div style="flex:1"><label class="label-small">ЕД. ИЗМ.</label>
                        <select id="edit-product-unit" class="form-select">
                            <option value="шт" ${p.unit === 'шт' ? 'selected' : ''}>шт</option>
                            <option value="кг" ${p.unit === 'кг' ? 'selected' : ''}>кг</option>
                            <option value="кор" ${p.unit === 'кор' ? 'selected' : ''}>кор</option>
                        </select>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('product-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveProductChanges(${p.id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openProductDetails(${p.id})">Отмена</button>
    `;
}



function toggleProductEdit(isEdit) {
    const view = document.getElementById('product-view-mode');
    const edit = document.getElementById('product-edit-mode');
    if (view && edit) {
        view.style.display = isEdit ? 'none' : 'block';
        edit.style.display = isEdit ? 'block' : 'none';
    }
}


async function saveProductChanges(id) {
    // 1. Собираем данные (ID полей теперь соответствуют новой форме 4x2)
    const data = {
        category: document.getElementById('edit-product-category').value,
        price: parseFloat(document.getElementById('edit-product-price').value) || 0,
        hsnCode: document.getElementById('edit-product-hsn').value,
        expiryDate: document.getElementById('edit-product-expiry').value,

        name: document.getElementById('edit-product-name').value,
        stockQuantity: parseInt(document.getElementById('edit-product-qty').value) || 0,
        barcode: document.getElementById('edit-product-barcode').value,
        itemsPerBox: parseInt(document.getElementById('edit-product-perbox').value) || 0,
        unit: document.getElementById('edit-product-unit').value
    };

    try {
        // 2. Отправка на сервер через PUT
        await secureFetch(`/api/admin/products/${id}/edit`, {
            method: 'PUT',
            body: data
        });

        // 3. Обновляем локальный массив данных Sellion 2026
        const idx = productsData.findIndex(p => p.id == id);
        if (idx !== -1) {
            productsData[idx] = {...productsData[idx], ...data};

            // 4. Умное обновление строки в основной таблице склада
            const row = document.querySelector(`tr[onclick*="openProductDetails(${id})"]`);
            if (row) {
                // Название (внутри div для стиля)
                const nameDiv = row.cells[0].querySelector('div');
                if (nameDiv) nameDiv.innerText = data.name;

                // Цена
                row.cells[1].innerText = data.price.toLocaleString() + ' ֏';

                // Остаток (Badge-стиль)
                const qtyBadge = row.cells[2].querySelector('span');
                if (qtyBadge) {
                    qtyBadge.innerText = data.stockQuantity + ' шт.';
                    qtyBadge.className = data.stockQuantity > 10 ? 'badge bg-light text-dark' : 'badge bg-danger text-white';
                }

                // Упаковка + Единица измерения
                row.cells[3].innerText = `${data.itemsPerBox} ${data.unit}/уп`;

                // Штрих-код
                row.cells[4].innerText = data.barcode || '---';

                // Срок годности
                if (row.cells[5]) {
                    row.cells[5].innerText = data.expiryDate ? formatDate(data.expiryDate) : '---';
                    // Подсветка красным, если срок истекает
                    const isExpired = data.expiryDate && new Date(data.expiryDate) < new Date(new Date().getTime() + 30 * 24 * 60 * 60 * 1000);
                    row.cells[5].className = isExpired ? 'text-danger fw-bold' : '';
                }
            }
        }

        showToast("Товар успешно обновлен", "success");

        // 5. Возвращаемся в режим просмотра деталей с новыми данными
        openProductDetails(id);

    } catch (e) {
        console.error("Ошибка сохранения продукта:", e);
        showToast("Не удалось сохранить изменения", "error");
    }
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


function doInventory() {
    const id = window.currentProductId;
    const product = productsData.find(p => p.id == id);
    if (!product) return;

    document.getElementById('inv-product-id').value = id;
    document.getElementById('inv-product-name').innerText = product.name;
    document.getElementById('inv-actual-qty').value = product.stockQuantity;
    document.getElementById('inv-reason').value = 'Плановая проверка';

    openModal('modal-inventory');
}


async function submitInventoryAdjustment() {
    const id = document.getElementById('inv-product-id').value;
    const newQty = parseInt(document.getElementById('inv-actual-qty').value);
    const reason = document.getElementById('inv-reason').value;

    if (isNaN(newQty) || newQty < 0 || !reason) {
        showToast("Введите корректное количество и причину!", "error");
        return;
    }

    try {
        // Выполняем запрос. Если доступа нет, глобальный fetch
        // покажет "Доступ запрещен" и выбросит ошибку, прервав выполнение здесь.
        await fetch(`/api/admin/products/${id}/inventory`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({newQty: newQty, reason: reason})
        });

        // Этот код выполнится ТОЛЬКО при успешном ответе (status 200)
        showToast("Склад скорректирован", "success");
        location.reload();

    } catch (e) {
        // Блок пустой, чтобы не было второго тоста "Ошибка".
        // Ошибка уже выведена глобальным перехватчиком fetch.
        console.warn("Инвентаризация не удалась:", e.message);
    }
}


function downloadExcel(type) {
    const start = document.getElementById('report-start').value;
    const end = document.getElementById('report-end').value;

    if (!start || !end) {
        showToast("Выберите период!", "error");
        return;
    }

    // 1. Проверка наличия данных в DOM (для визуальной скорости)
    const tableId = type === 'orders' ? 'orders-table-body' : 'returns-table-body';
    // Ищем строки, которые не являются заглушками "нет данных"
    const rows = document.querySelectorAll(`#${tableId} tr`);
    const hasData = Array.from(rows).some(row => row.cells.length > 1);

    if (!hasData) {
        showToast(`Нет данных (${type === 'orders' ? 'заказов' : 'возвратов'}) за этот период!`, "error");
        return;
    }

    showToast(`⏳ Формирование Excel...`, "info");

    // 2. Формируем URL (убедитесь, что в Java добавлен /returns-detailed)
    const url = type === 'orders' ?
        `/api/reports/excel/orders-detailed?start=${start}&end=${end}` :
        `/api/reports/excel/returns-detailed?start=${start}&end=${end}`;

    // 3. Используем fetch с обработкой Blob
    fetch(url)
        .then(async response => {
            if (response.ok) {
                const blob = await response.blob();
                const downloadUrl = window.URL.createObjectURL(blob);

                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = downloadUrl;
                // Красивое имя файла для 2026 года
                a.download = `Sellion_${type.toUpperCase()}_${start}_${end}.xlsx`;

                document.body.appendChild(a);
                a.click();

                // Очистка памяти
                setTimeout(() => {
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(downloadUrl);
                }, 100);

                showToast(`Отчет успешно скачан!`, 'success');
            } else {
                // Пытаемся получить текст ошибки от сервера (JSON)
                const errorData = await response.json().catch(() => ({ message: "Ошибка сервера (500/404)" }));
                showToast(errorData.message || 'Не удалось сгенерировать файл', 'error');
            }
        })
        .catch(error => {
            console.error('Download error:', error);
            showToast('Ошибка сети при скачивании отчета.', 'error');
        });
}


function sendToEmail() {
    // 1. Получаем элементы
    const startInput = document.getElementById('report-start');
    const endInput = document.getElementById('report-end');
    const emailInput = document.getElementById('report-email');

    // Проверка на существование элементов (на всякий случай)
    if (!startInput || !endInput || !emailInput) {
        console.error("Элементы интерфейса не найдены");
        return;
    }

    const start = startInput.value;
    const end = endInput.value;
    const email = emailInput.value;

    if (!start || !end || !email) {
        showToast("Выберите период и введите email!", "error");
        return;
    }

    // 2. Собираем типы отчетов
    const types = [];
    const checkOrders = document.getElementById('check-orders');
    const checkReturns = document.getElementById('check-returns');

    if (checkOrders && checkOrders.checked) types.push('orders');
    if (checkReturns && checkReturns.checked) types.push('returns');

    if (types.length === 0) {
        showToast("Выберите хотя бы один тип данных (Заказы или Возвраты)!", "info");
        return;
    }

    // 3. Подготовка данных для отправки
    // Мы отправляем объект, который Spring легко распарсит через @RequestBody или как Map
    const payload = {
        start: start,
        end: end,
        email: email,
        types: types
    };

    showToast(`⏳ Отправка отчета на ${email}...`, "info");

    const url = '/api/reports/excel/send-to-accountant';

    // 4. Отправка через JSON
    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json', // МЕНЯЕМ НА JSON
            'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value || ""
        },
        body: JSON.stringify(payload) // ПРЕВРАЩАЕМ В СТРОКУ
    })
        .then(response => response.json())
        .then(data => {
            showToast(data.message || '✅ Отчет успешно отправлен!', 'success');
        })
        .catch(error => {
            // Ошибка уже обработана вашим глобальным перехватчиком fetch
            console.warn("Ошибка отправки email:", error.message);
        });
}


function showManagerInvoices(managerName) {
    showTab('tab-invoices');

    const searchInput = document.getElementById('search-invoices');
    if (searchInput) {
        searchInput.value = managerName;
        filterTable('search-invoices', 'invoices-table-body');
    }
}

function showManagerReport(managerName) {
    const start = document.querySelector('input[name="kpiStart"]').value;
    const end = document.querySelector('input[name="kpiEnd"]').value;

    if (!start || !end) {
        showToast("Выберите период для отчета", "error");
        return;
    }

    const url = `/admin/reports/manager-summary?managerId=${managerName}&start=${start}&end=${end}`;
    printAction(url);
}


function openSetTargetModal(managerId) {
    document.getElementById('target-manager-name').innerText = managerId;
    document.getElementById('target-amount-input').value = 0;
    openModal('modal-set-target');
}


async function saveTargetSales() {
    const managerId = document.getElementById('target-manager-name').innerText;
    const amount = parseFloat(document.getElementById('target-amount-input').value) || 0;

    if (!managerId || amount <= 0) {
        showToast("Введите корректную сумму цели", "error");
        return;
    }

    const data = {
        managerId: managerId,
        targetAmount: amount
    };

    try {
        const response = await fetch('/api/admin/targets/save', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });

        if (response.ok) {
            showToast("Цель успешно сохранена", "success");
            closeModal('modal-set-target');
            location.reload();
        } else {
            const error = await response.json();
            showToast(error.message || "Ошибка сохранения цели", "error");
        }
    } catch (e) {
        showToast("Ошибка сети или сервера", "error");
    }
}


function setMinDateToday(inputId) {
    const dateInput = document.getElementById(inputId);
    if (dateInput) {
        // Получаем текущую дату в формате YYYY-MM-DD
        const today = new Date();
        const yyyy = today.getFullYear();
        const mm = String(today.getMonth() + 1).padStart(2, '0');
        const dd = String(today.getDate()).padStart(2, '0');
        const todayISO = `${yyyy}-${mm}-${dd}`;

        dateInput.min = todayISO;
    }
}


function updateSelectedCount() {
    const checked = document.querySelectorAll('.correction-checkbox:checked').length;
    const counter = document.getElementById('selected-count');
    if (counter) {
        counter.innerText = checked;
    }
}


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


document.addEventListener('input', function (e) {
    if (e.target.classList.contains('date-input-check')) {
        e.target.value = e.target.value.replace(/[,/]/g, '.');
    }
});

function validateDate(dateStr) {
    // Регулярное выражение для dd.mm.yyyy
    const regex = /^\d{2}\.\d{2}\.\d{4}$/;
    if (!regex.test(dateStr)) {
        alert("Ошибка! Введите дату в формате ДД.ММ.ГГГГ (например 20.01.2026)");
        return false;
    }
    return true;
}


document.addEventListener('change', function (e) {
    if (e.target.classList.contains('correction-checkbox') || e.target.id === 'select-all-corrections') {
        const checked = document.querySelectorAll('.correction-checkbox:checked').length;
        const counter = document.getElementById('selected-count');
        if (counter) {
            counter.innerText = checked;
        }
    }
});

function toggleAllCorrections(source) {
    const checkboxes = document.querySelectorAll('.correction-checkbox');
    checkboxes.forEach(cb => {
        cb.checked = source.checked;
    });
    const checked = document.querySelectorAll('.correction-checkbox:checked').length;
    document.getElementById('selected-count').innerText = checked;
}


function sendSelectedCorrections() {
    const selectedIds = Array.from(document.querySelectorAll('.correction-checkbox:checked')).map(cb => cb.value);
    const emailInput = document.getElementById('report-email');
    const email = emailInput ? emailInput.value : 'accountant@company.am';

    if (selectedIds.length === 0) {
        showToast("Выберите хотя бы одну корректировку", "info");
        return;
    }

    showConfirmModal(
        "Подтверждение отправки",
        `Отправить реестр из ${selectedIds.length} корректировок на почту ${email}?`,
        () => {
            // Эта часть выполнится только после нажатия "Да" в модальном окне
            executeSendingCorrections(selectedIds, email);
        }
    );
}

function executeSendingCorrections(selectedIds, email) {
    showToast("⏳ Подготовка и отправка реестра...");

    fetch('/api/reports/excel/send-selected-corrections', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            ids: selectedIds,
            email: email
        })
    })
        .then(res => {
            if (!res.ok) throw new Error("Ошибка сервера");
            return res.json();
        })
        .then(data => {
            if (data.success) {
                showToast("Реестр успешно отправлен бухгалтеру", "success");
                document.querySelectorAll('.correction-checkbox').forEach(cb => cb.checked = false);
                const selectAll = document.getElementById('select-all-corrections');
                if (selectAll) selectAll.checked = false;
                document.getElementById('selected-count').innerText = "0";
            } else {
                showToast("Ошибка: " + (data.error || "Не удалось отправить"), "error");
            }
        })
        .catch(err => {
            console.error('Error:', err);
            showToast("Ошибка соединения с сервером", "error");
        });
}

function applyGlobalDateFormatting() {
    document.querySelectorAll('.js-date-format').forEach(el => {
        const rawDate = el.innerText.trim();
        if (rawDate && rawDate !== '---') {
            el.innerText = fmt(rawDate); // Используем вашу функцию fmt
        }
    });
}

function loadApiKeys() {
    const tbody = document.getElementById('api-keys-list');

    if (!tbody) {
        return;
    }

    fetch('/api/admin/manager-keys')
        .then(response => {
            if (!response.ok) throw new Error('Ошибка сети');
            return response.json();
        })
        .then(keys => {
            tbody.innerHTML = '';

            if (!keys || keys.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" class="text-center">Ключи не найдены</td></tr>';
                return;
            }

            keys.forEach(key => {
                const row = tbody.insertRow();
                row.innerHTML = `
                    <td>${key.managerId}</td>
                    <td><code>${key.apiKeyHash}</code></td>
                    <td>
                        <button onclick="deleteApiKey('${key.managerId}')" class="btn-primary" style="background: #ef4444; padding: 5px 10px;">Удалить</button>
                    </td>
                `;
            });
        })
        .catch(err => {
            console.warn("API ключи не загружены:", err.message);
        });
}


function generateApiKeyForManager() {
    const managerId = prompt("Введите ID менеджера (например, 1011):");
    if (managerId) {
        fetch('/api/admin/manager-keys/generate', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({managerId: managerId})
        })
            .then(response => response.json())
            .then(data => {
                showToast(`Ключ сгенерирован: ${data.apiKeyHash}`);
                loadApiKeys();
            });
    }
}


function deleteApiKey(managerId) {
    if (confirm(`Уверены, что хотите удалить ключ для ${managerId}?`)) {
        fetch(`/api/admin/manager-keys/delete/${managerId}`, {method: 'DELETE'})
            .then(() => {
                showToast("Ключ удален");
                loadApiKeys(); // Обновляем список
            });
    }
}

function refreshReportCounters() {

    const verifiedOrders = Array.from(document.querySelectorAll('#orders-table-body tr')).filter(tr => {
        return tr.innerText.includes('Счет №') || tr.innerText.includes('ПРОВЕРЕНО');
    }).length;

    const processedReturns = Array.from(document.querySelectorAll('#returns-table-body tr')).filter(tr => {
        return tr.innerText.includes('Проведено') || tr.innerText.includes('COMPLETED');
    }).length;

    if (document.getElementById('count-verified-orders'))
        document.getElementById('count-verified-orders').innerText = verifiedOrders + " поз.";
    if (document.getElementById('count-processed-returns'))
        document.getElementById('count-processed-returns').innerText = processedReturns + " поз.";

    if (document.getElementById('btn-count-orders'))
        document.getElementById('btn-count-orders').innerText = verifiedOrders;
    if (document.getElementById('btn-count-returns'))
        document.getElementById('btn-count-returns').innerText = processedReturns;
}


function applyReportFilters() {
    const start = document.getElementById('report-start').value;
    const end = document.getElementById('report-end').value;

    if (!start || !end) {
        showToast("Выберите начало и конец периода!", "error");
        return;
    }

    // Показываем пользователю, что данные обновляются
    showToast("⏳ Загрузка данных за период...", "info");

    // Формируем URL с сохранением активной вкладки и дат
    const url = new URL(window.location.href);
    url.searchParams.set('activeTab', 'tab-reports');
    url.searchParams.set('orderStartDate', start); // Используем те же имена, что в контроллере
    url.searchParams.set('orderEndDate', end);
    url.searchParams.set('returnStartDate', start);
    url.searchParams.set('returnEndDate', end);

    window.location.href = url.toString();
}

function printCompactOrders() {
    const checkboxes = document.querySelectorAll('.order-print-check:checked');
    if (checkboxes.length === 0) return showToast("Выберите хотя бы один заказ", "error");

    // Формируем строку параметров: type=order&ids=1&ids=2...
    const params = new URLSearchParams();
    params.append('type', 'order');
    checkboxes.forEach(cb => params.append('ids', cb.value));

    const url = `/admin/logistic/print-compact?${params.toString()}`;
    printAction(url);
}

function printCompactReturns() {
    const checkboxes = document.querySelectorAll('.return-print-check:checked');
    if (checkboxes.length === 0) return showToast("Выберите хотя бы один возврат", "error");

    // Формируем строку параметров: type=return&ids=1&ids=2...
    const params = new URLSearchParams();
    params.append('type', 'return');
    checkboxes.forEach(cb => params.append('ids', cb.value));

    const url = `/admin/logistic/print-compact?${params.toString()}`;
    printAction(url);
}


const csrfToken = document.querySelector('input[name="_csrf"]')?.value;

async function secureFetch(url, options = {}) {
    if (!options.headers) options.headers = {};

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    const method = (options.method || 'GET').toUpperCase();
    if (csrfToken && csrfHeader && method !== 'GET') {
        options.headers[csrfHeader] = csrfToken;
    }

    if (options.body && typeof options.body === 'object') {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, options);

    if (!response.ok) {
        let errorMessage = `Ошибка сервера: ${response.status}`;
        try {
            const errorData = await response.json();
            errorMessage = errorData.message || errorMessage;
        } catch (e) {
        }
        showToast(errorMessage, 'error');
        throw new Error(errorMessage);
    }

    if (response.status === 204 || response.headers.get('content-length') === '0') {
        return null;
    }

    return response.json();
}


function printSelectedRows(tableId) {
    const selected = Array.from(document.querySelectorAll(`#${tableId} .row-checkbox:checked`))
        .map(cb => cb.value);
    if (selected.length === 0) return alert("Выберите хотя бы одну запись");

    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/admin/orders/print-batch';
    form.target = '_blank';

    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfInput = document.createElement('input');
    csrfInput.name = '_csrf';
    csrfInput.value = csrfToken;
    form.appendChild(csrfInput);

    selected.forEach(id => {
        const input = document.createElement('input');
        input.name = 'ids';
        input.value = id;
        form.appendChild(input);
    });

    document.body.appendChild(form);
    form.submit();
    form.remove();
}


async function submitWriteOff() {
    const comment = document.getElementById('write-off-comment').value;
    if (!comment) return showToast("Укажите причину списания!", "error");
    if (Object.keys(tempItems).length === 0) return showToast("Список пуст!", "error");

    const data = {
        comment: comment,
        items: tempItems
    };

    const response = await fetch('/api/admin/orders/write-off', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
        },
        body: JSON.stringify(data)
    });

    if (response.ok) {
        showToast("Товар списан");
        location.reload();
    }
}

function saveAllSettings() {
    const settings = {
        'COMPANY_NAME': document.getElementById('set-COMPANY_NAME').value,
        'ACCOUNTANT_EMAIL': document.getElementById('set-ACCOUNTANT_EMAIL').value,
        'COMPANY_INN': document.getElementById('set-COMPANY_INN').value,
        'COMPANY_BANK_NAME': document.getElementById('set-COMPANY_BANK_NAME').value,
        'COMPANY_BANK_ACCOUNT': document.getElementById('set-COMPANY_BANK_ACCOUNT').value,
        'COMPANY_ADDRESS': document.getElementById('set-COMPANY_ADDRESS').value
    };

    // Получаем CSRF токены для безопасности
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    fetch('/api/admin/settings/update-all', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify(settings)
    })
        .then(res => {
            if (res.ok) {
                showToast("Настройки сохранены", "success");
                setTimeout(() => location.reload(), 1000);
            } else {
                showToast("Ошибка сохранения", "error");
            }
        });
}


function printManagerDebts() {
    const managerId = document.getElementById('filter-invoice-manager').value;
    const start = document.getElementById('inv-date-start').value;
    const end = document.getElementById('inv-date-end').value;

    if (!managerId) {
        showToast("Сначала выберите менеджера из списка!", "info");
        return;
    }

    if (!start || !end) {
        showToast("Выберите период (начало и конец)!", "info");
        return;
    }

    // Формируем URL с учетом менеджера и дат
    // Добавляем параметры start и end, чтобы Java-контроллер мог их прочитать
    const url = `/admin/invoices/print-debts?managerId=${encodeURIComponent(managerId)}&start=${start}&end=${end}`;

    // Печать через фрейм
    printAction(url);
}

function setDefaultInvoiceDates() {
    const now = new Date();
    // Первый день текущего месяца (гггг-мм-01)
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    // Сегодняшний день (гггг-мм-дд)
    const today = now.toISOString().split('T')[0];

    const startInput = document.getElementById('inv-date-start');
    const endInput = document.getElementById('inv-date-end');

    if (startInput && !startInput.value) startInput.value = firstDay;
    if (endInput && !endInput.value) endInput.value = today;
}

setDefaultInvoiceDates();



function formatDate(dateVal) {
    if (!dateVal || dateVal === '---' || dateVal === null) return '---';

    try {
        // 1. Если пришел объект LocalDateTime из Java
        if (typeof dateVal === 'object' && dateVal.year) {
            const d = String(dateVal.dayOfMonth || dateVal.day || 1).padStart(2, '0');
            const m = String(dateVal.monthValue || dateVal.month || 1).padStart(2, '0');
            const y = dateVal.year;
            const h = String(dateVal.hour || 0).padStart(2, '0');
            const min = String(dateVal.minute || 0).padStart(2, '0');
            return `${d}.${m}.${y} ${h}:${min}`;
        }

        // 2. Если пришла строка (ISO или обычная)
        if (typeof dateVal === 'string') {
            let clean = dateVal.replace(/[,/]/g, '.');

            // ISO формат: 2026-01-20T01:17:00
            if (clean.includes('T') || (clean.includes('-') && clean.includes(':'))) {
                const parts = clean.split(/[T ]/);
                const dParts = parts[0].split('-');
                if (dParts.length === 3) {
                    const date = `${dParts[2]}.${dParts[1]}.${dParts[0]}`;
                    const time = parts[1].substring(0, 5);
                    return `${date} ${time}`;
                }
            }

            // Только дата: 2026-01-20
            if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) {
                const d = clean.split('-');
                return `${d[2]}.${d[1]}.${d[0]}`;
            }
        }

        // Резервный вариант через стандартный Date
        const date = new Date(dateVal);
        if (!isNaN(date.getTime())) {
            const d = String(date.getDate()).padStart(2, '0');
            const m = String(date.getMonth() + 1).padStart(2, '0');
            const y = date.getFullYear();
            return `${d}.${m}.${y}`;
        }

    } catch (e) {
        console.warn("Ошибка форматирования даты:", dateVal);
    }

    return dateVal;
}

const fmt = formatDate;
const formatOrderDate = formatDate;


async function saveNewManualOperation(type, btnElement) { // Добавили btnElement
    const shopName = document.getElementById('new-op-shop')?.value.trim();
    const dateVal = document.getElementById('new-op-date')?.value;

    if (!shopName || !dateVal) {
        return showToast("Заполните магазин и дату!", "info");
    }

    const itemsToSave = collectItemsFromUI();
    if (Object.keys(itemsToSave).length === 0) {
        return showToast("Список товаров пуст!", "error");
    }

    // Используем переданную кнопку или ищем старым способом
    const saveBtn = btnElement || document.querySelector(`button[onclick*="saveNewManualOperation('${type}')"]`);

    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = "⏳ Проверка...";
    }

    if (type === 'order') {
        // ВАЖНО: Проверьте, что функция checkAndApplyPromos доступна в этом файле
        checkAndApplyPromos(itemsToSave, async (selectedPromos) => {
            const promoMap = {};
            selectedPromos.forEach(promo => {
                if (promo.items) {
                    Object.entries(promo.items).forEach(([pId, percent]) => {
                        if (itemsToSave[pId]) promoMap[pId] = percent;
                    });
                }
            });

            const data = {
                shopName: shopName,
                managerId: document.getElementById('new-op-manager')?.value,
                items: itemsToSave,
                appliedPromoItems: promoMap,
                carNumber: document.getElementById('new-op-car')?.value || "",
                comment: document.getElementById('new-op-comment')?.value || "",
                deliveryDate: dateVal,
                paymentMethod: document.getElementById('new-op-payment')?.value,
                needsSeparateInvoice: document.getElementById('new-op-separate')?.value === "true",
                discountPercent: parseFloat(document.getElementById('new-op-percent')?.value) || 0,
                androidId: `MANUAL-${Date.now()}`
            };

            await executeManualPost('/api/admin/orders/create-manual', data, saveBtn);
        });
    } else {
        // Логика для возврата остается без изменений
        const data = {
            shopName: shopName,
            managerId: document.getElementById('new-op-manager')?.value,
            items: itemsToSave,
            itemPrices: window.tempItemPrices || {},
            returnDate: dateVal,
            returnReason: document.getElementById('new-op-reason')?.value,
            carNumber: document.getElementById('new-op-car')?.value || "",
            comment: document.getElementById('new-op-comment')?.value || "",
            androidId: `MANUAL-${Date.now()}`
        };
        await executeManualPost('/api/admin/returns/create-manual', data, saveBtn);
    }
}

async function executeManualPost(endpoint, data, saveBtn) {
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = "⏳ Сохранение...";
    }

    try {
        // Вызываем fetch (наш глобальный перехватчик уже настроен)
        // Если придет 403 или 400, выполнение ПРЕРВЕТСЯ здесь и уйдет в catch
        await fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value || ""
            },
            body: JSON.stringify(data)
        });

        // Этот код сработает ТОЛЬКО при успешном ответе (status 200)
        showToast("Успешно сохранено!", "success");
        setTimeout(() => location.reload(), 600);

    } catch (e) {
        // ВАЖНО: Блок catch пустой для уведомлений!
        // Глобальный fetch уже вывел тост "Доступ запрещен" или текст ошибки с сервера.
        console.warn("Операция отклонена:", e.message);

        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = "Попробовать снова";
        }
    }
}



function calculateCurrentTempTotal() {
    let total = 0;
    Object.entries(tempItems).forEach(([pId, pQty]) => {
        const prod = (productsData || []).find(p => p.id == pId);
        if (prod) total += (prod.price || 0) * pQty;
    });
    const totalPriceElement = document.getElementById('order-total-price');
    if (totalPriceElement) {
        totalPriceElement.innerText = "Итого: " + total.toLocaleString() + " ֏";
    }
    return total;
}


function removeItemFromEdit(pId) {
    delete tempItems[pId];
    renderItemsTable(tempItems, true);
    showToast("Товар удален из списка", "info"); // Добавляем уведомление
}


function updateQtyAndRecalculate(pId, shouldRedraw = false) {
    // 1. Получаем строку и элементы
    const row = document.getElementById(`row-${pId}`);
    if (!row) return;

    const qtyInput = document.getElementById(`input-qty-${pId}`);
    const priceInput = row.querySelector('.item-price-input');

    const p = productsData.find(prod => prod.id == pId);
    if (!p) return;

    // 2. Сбор актуальных данных из инпутов
    let newQty = qtyInput ? (parseInt(qtyInput.value)) : (tempItems[pId] || 0);

    // --- КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: УДАЛЕНИЕ ПРИ 0 ---
    if (newQty <= 0) {
        // Вызываем функцию удаления и выходим
        if (typeof removeItemFromEdit === 'function') {
            removeItemFromEdit(pId);
        } else {
            // Если функции нет, удаляем вручную из буферов
            delete tempItems[pId];
            if (window.tempItemPrices) delete window.tempItemPrices[pId];
            renderItemsTable(tempItems, true);
        }
        return;
    }

    const modalTitleEl = document.getElementById('modal-title');
    const modalTitle = modalTitleEl ? modalTitleEl.innerText.toUpperCase() : "";
    const isReturnOrWriteOff = modalTitle.includes("ВОЗВРАТ") || modalTitle.includes("СПИСАНИЕ") || modalTitle.includes("🔄");

    let basePriceToUse = p.price;
    if (isReturnOrWriteOff && priceInput) {
        basePriceToUse = parseFloat(priceInput.value) || 0;
        // Сохраняем в буфер цен, чтобы не потерять при перерисовке
        if (!window.tempItemPrices) window.tempItemPrices = {};
        window.tempItemPrices[pId] = basePriceToUse;
    }

    // Сохраняем в буфер товаров
    if (typeof tempItems !== 'undefined') {
        tempItems[pId] = newQty;
    }

    // 4. Если требуется полная перерисовка (например, после изменения кол-ва)
    if (shouldRedraw) {
        renderItemsTable(tempItems, true);
        return;
    }

    // 5. ЛОКАЛЬНОЕ ОБНОВЛЕНИЕ СТРОКИ (без перерисовки всей таблицы)
    const percentInput = document.getElementById('order-discount-percent') || document.getElementById('new-op-percent');
    const shopPercent = parseFloat(percentInput?.value) || 0;

    const appliedPromos = window.currentOrderPromos || {};
    const hasPromo = !isReturnOrWriteOff && appliedPromos.hasOwnProperty(pId);
    const finalPercent = hasPromo ? parseFloat(appliedPromos[pId]) : (isReturnOrWriteOff ? 0 : shopPercent);

    const modifier = 1 - (finalPercent / 100);
    const priceWithDiscount = roundHalfUp(basePriceToUse * modifier);
    const rowSum = roundHalfUp(priceWithDiscount * newQty);

    // Обновляем ячейки
    const f = { minimumFractionDigits: 1, maximumFractionDigits: 1 };
    const subtotalCell = document.getElementById(`total-row-${pId}`);
    const priceCell = row.querySelector('.item-price-cell');

    if (subtotalCell) {
        subtotalCell.innerText = rowSum.toLocaleString(undefined, f) + " ֏";
    }

    if (priceCell && !priceInput) {
        // Обновляем текст цены только если это не поле ввода (т.е. обычный заказ)
        priceCell.innerText = priceWithDiscount.toLocaleString(undefined, f) + " ֏";
    }

    // 6. Обновляем общий итог внизу модального окна
    if (typeof updateFinalTotalDisplay === 'function') {
        updateFinalTotalDisplay(shopPercent);
    } else {
        // Если отдельной функции нет, просто считаем сумму по буферу и обновляем текст
        let total = 0;
        Object.entries(tempItems).forEach(([id, q]) => {
            let pr = (isReturnOrWriteOff && window.tempItemPrices && window.tempItemPrices[id])
                     ? window.tempItemPrices[id]
                     : (productsData.find(prod => prod.id == id)?.price || 0);
            total += roundHalfUp(pr * q);
        });
        const totalEl = document.getElementById('order-total-price');
        if (totalEl) {
            totalEl.innerHTML = `<span style="font-size: 14px; color: #64748b; font-weight: normal;">Итого:</span> <span style="font-weight: 800;">${total.toLocaleString(undefined, f)} ֏</span>`;
        }
    }
}

function updateFinalTotalDisplay(shopPercent) {
    let total = 0;
    const f = { minimumFractionDigits: 1, maximumFractionDigits: 1 };

    const modalTitleEl = document.getElementById('modal-title');
    const modalTitle = modalTitleEl ? modalTitleEl.innerText.toUpperCase() : "";
    const isReturnOrWriteOff = modalTitle.includes("ВОЗВРАТ") || modalTitle.includes("СПИСАНИЕ");

    const appliedPromos = window.currentOrderPromos || {};
    const rows = document.querySelectorAll('#order-items-body tr[id^="row-"]');

    rows.forEach(row => {
        const pId = row.id.replace('row-', '');

        // --- ЛОГИКА ОПРЕДЕЛЕНИЯ ЦЕНЫ ---
        let basePrice = parseFloat(row.dataset.basePrice) || 0;

        // Если это возврат, приоритет отдаем инпуту или буферу цен
        if (isReturnOrWriteOff) {
            const priceInput = row.querySelector('.item-price-input');
            if (priceInput) {
                basePrice = parseFloat(priceInput.value) || 0;
            } else if (window.tempItemPrices && window.tempItemPrices[pId] !== undefined) {
                basePrice = window.tempItemPrices[pId];
            }
        }

        // Получаем количество
        const qtyInput = row.querySelector('.qty-input-active');
        const qty = qtyInput ? (parseInt(qtyInput.value) || 0) : (parseInt(row.querySelector('b')?.innerText) || 0);

        // Скидки (только для заказов)
        const currentItemPercent = isReturnOrWriteOff ? 0 : (appliedPromos.hasOwnProperty(pId) ? parseFloat(appliedPromos[pId]) : shopPercent);

        // --- РАСЧЕТ ---
        const modifier = 1 - (currentItemPercent / 100);
        const discountedPrice = roundHalfUp(basePrice * modifier);
        const rowSum = roundHalfUp(discountedPrice * qty);

        total += rowSum;

        // --- ВИЗУАЛЬНОЕ ОБНОВЛЕНИЕ СТРОКИ ---
        const priceCell = row.querySelector('.item-price-cell');
        if (priceCell) {
            // Обновляем текст цены только если там нет инпута (чтобы не затирать ввод в возвратах)
            if (!priceCell.querySelector('input')) {
                priceCell.innerText = discountedPrice.toLocaleString(undefined, f) + " ֏";
                priceCell.style.color = appliedPromos.hasOwnProperty(pId) ? "#ea580c" : (isReturnOrWriteOff ? "#1e293b" : "#6366f1");
                priceCell.style.fontWeight = (appliedPromos.hasOwnProperty(pId) || isReturnOrWriteOff) ? "800" : "700";
            }
        }

        const subtotalCell = document.getElementById(`total-row-${pId}`) || row.querySelector('.item-subtotal-cell');
        if (subtotalCell) {
            subtotalCell.innerText = rowSum.toLocaleString(undefined, f) + " ֏";
        }
    });

    total = roundHalfUp(total);

    const totalEl = document.getElementById('order-total-price') || document.getElementById('manual-order-total-price');
    if (totalEl) {
        totalEl.innerHTML = `<span style="font-size: 14px; color: #64748b; font-weight: normal;">Итого:</span> ${total.toLocaleString(undefined, f)} ֏`;
    }

    window.currentOrderTotal = total;
}


function openWriteOffModal() {
    tempItems = {};
    const today = new Date().toISOString().split('T')[0];

    // Синтаксис для Thymeleaf атрибутов в JS
    const userElement = document.querySelector('.sidebar [sec\\:authentication]');
    const currentUser = userElement?.innerText || "ADMIN";

    // 1. Сброс стилей футера (исправляет смещение кнопок влево)
    const footer = document.getElementById('order-footer-actions');
    if (footer) {
        footer.style.display = 'flex';
        footer.style.justifyContent = 'flex-end'; // Кнопки строго справа
        footer.style.gap = '10px';
    }

    document.getElementById('modal-title').innerText = "📉 НОВОЕ СПИСАНИЕ ТОВАРА";
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background: #fef2f2; padding: 15px; border-radius: 10px; border: 1px solid #fecdd3;">
            <div><label>КТО СПИСЫВАЕТ</label><input type="text" id="write-off-user" class="form-control" value="${currentUser}" readonly></div>
            <div><label>ДАТА СПИСАНИЯ</label><input type="date" id="write-off-date" class="form-control" value="${today}"></div>
            <div><label>ПРИЧИНА СПИСАНИЯ</label><input type="text" id="write-off-comment" class="form-control" placeholder="Брак / Срок годности"></div>
        </div>`;

    renderItemsTable(tempItems, true);

    // 2. Скрываем Итого для списаний (это корректно для данного типа операции)
    const totalEl = document.getElementById('order-total-price');
    if (totalEl) totalEl.style.display = 'none';

    // 3. Отрисовка кнопок
    if (footer) {
        footer.innerHTML = `
            <button class="btn-primary" style="background:#ef4444" onclick="submitWriteOff()">✅ ПОДТВЕРДИТЬ СПИСАНИЕ</button>
            <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">ОТМЕНА</button>
        `;
    }

    openModal('modal-order-view');
}

function collectItemsFromUI() {
    const items = {};

    // Ищем во всех возможных телах таблиц (и в создании, и в редактировании)
    const bodies = [
        document.getElementById('order-items-body'),
        document.getElementById('manual-order-items-body')
    ];

    bodies.forEach(body => {
        if (!body || body.offsetParent === null) return; // Пропускаем, если тела нет или оно скрыто

        body.querySelectorAll('tr').forEach(row => {
            // 1. Пытаемся найти ID товара
            // Либо из атрибута data-pid, либо из ID строки row-123
            let pId = row.dataset.pid || row.id.replace('row-', '');

            // Если это строка выбора нового товара (как внизу вашего скрина) — у неё нет ID
            if (!pId || isNaN(pId)) {
                const select = row.querySelector('select');
                if (select && select.value) pId = select.value;
            }

            // 2. Ищем количество
            const qtyInput = row.querySelector('input[type="number"]');
            if (qtyInput && pId && !isNaN(pId)) {
                const qty = parseInt(qtyInput.value) || 0;
                if (qty > 0) {
                    items[pId] = qty;
                }
            }
        });
    });

    return items;
}

function formatSmartJS(num) {
    if (num === null || num === undefined) return "0";
    // 1. Округляем до 2 знаков для точности
    let fixed = Math.round((num + Number.EPSILON) * 100) / 100;
    // 2. Форматируем: убираем .00 или финальный 0 в дроби, добавляем пробел как разделитель тысяч
    return parseFloat(fixed).toLocaleString('ru-RU', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
    }).replace(',', '.');
}

function renderItemsTable(itemsMap, isEdit) {
    const body = document.getElementById('order-items-body');
    if (!body) return;

    const modalTitleEl = document.getElementById('modal-title');
    const modalTitle = modalTitleEl ? modalTitleEl.innerText.toUpperCase() : "";

    const isReturnOrWriteOff = modalTitle.includes("ВОЗВРАТ") ||
        modalTitle.includes("СПИСАНИЕ") ||
        modalTitle.includes("🔄");

    const tableHeader = document.querySelector('#modal-order-view table thead tr');
    if (tableHeader) {
        if (isReturnOrWriteOff) {
            tableHeader.innerHTML = `<th>Товар</th><th>Кол-во</th><th>Цена</th><th>Итого</th><th>Кат.</th>`;
        } else {
            tableHeader.innerHTML = `<th>Товар</th><th>Кол-во</th><th>Прайс</th><th>Прайс - %</th><th style="color:#f59e0b">Промо %</th><th>Итого</th><th>Кат.</th>`;
        }
    }

    const percentInput = document.getElementById('order-discount-percent') || document.getElementById('new-op-percent');
    const shopPercent = isReturnOrWriteOff ? 0 : (percentInput ? parseFloat(percentInput.value) || 0 : 0);
    const appliedPromos = window.currentOrderPromos || {};

    let html = '';
    let totalSumForCalculation = 0;

    Object.entries(itemsMap).forEach(([pId, qty]) => {
        const p = productsData.find(prod => prod.id == pId);
        if (!p) return;

        let currentPrice = (isReturnOrWriteOff && window.tempItemPrices && window.tempItemPrices[pId] !== undefined)
            ? window.tempItemPrices[pId]
            : p.price;

        const hasPromo = appliedPromos.hasOwnProperty(pId);
        const currentItemPercent = hasPromo ? parseFloat(appliedPromos[pId]) : shopPercent;
        const modifier = 1 - (currentItemPercent / 100);

        const priceWithPercent = roundHalfUp(currentPrice * modifier);
        const rowSum = roundHalfUp(priceWithPercent * qty);
        totalSumForCalculation += rowSum;

        const qtyDisplay = isEdit ?
            `<div class="qty-edit-box" style="display: flex; align-items: center; gap: 3px;">
                <input type="number" id="input-qty-${pId}" class="qty-input-active"
                       value="${qty}" onchange="updateQtyAndRecalculate('${pId}', true)" style="width: 50px;">
            </div>` : `<b>${qty} шт.</b>`;

        if (isReturnOrWriteOff) {
            const priceDisplay = isEdit ?
                `<div style="display: flex; align-items: center; gap: 4px; white-space: nowrap;">
                                <input type="number" step="0.1" class="form-control item-price-input"
                                       data-pid="${pId}"
                                       value="${currentPrice}"
                                       oninput="updateQtyAndRecalculate('${pId}', false)"
                                       style="width: 75px; font-weight: bold; border: 1px solid #f87171; padding: 2px 5px; height: 30px;">
                                <span style="font-weight: bold;">֏</span>
                            </div>` :
                `<b style="white-space: nowrap;">${formatSmartJS(currentPrice)} ֏</b>`;

            html += `<tr data-base-price="${currentPrice}" id="row-${pId}">
                            <td style="padding-left: 15px;">
                                ${p.name}
                                ${isEdit ? `<span onclick="removeItemFromEdit('${pId}')" style="color: #ef4444; cursor: pointer; margin-left: 5px;">❌</span>` : ''}
                            </td>
                            <td>${qtyDisplay}</td>
                            <td class="item-price-cell">${priceDisplay}</td>
                            <td id="total-row-${pId}" class="item-subtotal-cell" style="font-weight:800; white-space: nowrap;">${formatSmartJS(rowSum)} ֏</td>
                            <td><small class="text-muted">${p.category || '---'}</small></td>
                        </tr>`;
        } else {
            const isDiscounted = currentItemPercent > 0;
            const priceStyle = isDiscounted ? 'text-decoration: line-through; color: #94a3b8;' : 'color: #1e293b;';

            html += `<tr data-base-price="${p.price}" id="row-${pId}">
                <td style="padding-left: 15px;">${p.name} ${isEdit ? `<span onclick="removeItemFromEdit('${pId}')" style="color: #ef4444; cursor: pointer; margin-left: 5px;">❌</span>` : ''}</td>
                <td>${qtyDisplay}</td>
                <td style="${priceStyle} font-size: 11px;">${formatSmartJS(p.price)} ֏</td>
                <td class="item-price-cell" style="color: #6366f1; font-weight: 700;">${formatSmartJS(priceWithPercent)} ֏</td>
                
                <td style="text-align:center;">
                    <!-- ИСПРАВЛЕНО: Показываем оранжевый бейдж, если применен любой процент (акция или скидка магазина) -->
                    ${currentItemPercent > 0 ?
                `<span class="badge" style="background:#fff7ed; color:#ea580c; border:1px solid #fdba74; padding: 2px 6px;">
                            ${parseFloat(currentItemPercent)}%
                        </span>` :
                `<span style="color:#cbd5e1;">---</span>`
            }
                </td>
                
                <td id="total-row-${pId}" class="item-subtotal-cell" style="font-weight:800;">${formatSmartJS(rowSum)} ֏</td>
                <td><small class="text-muted">${p.category || '---'}</small></td>
            </tr>`;
        }
    });

    if (isEdit) {
        const options = `<option value="" disabled selected>Выберите товар...</option>` +
            productsData.map(p => `<option value="${p.id}">${p.name} (${formatSmartJS(p.price)} ֏)</option>`).join('');

        const addRowColspan = isReturnOrWriteOff ? 3 : 5;
        html += `<tr class="add-row-sticky" style="background: #f8fafc;">
            <td><select id="add-item-select" class="form-select" style="font-size: 12px;">${options}</select></td>
            <td><input type="number" id="add-item-qty" value="1" class="form-control" style="width: 60px;"></td>
            <td colspan="${addRowColspan}">
                <button class="btn-primary w-70" onclick="addItemToEdit()" style="padding: 5px;">+ Добавить в список</button>
            </td>
        </tr>`;
    }

    body.innerHTML = html;
    totalSumForCalculation = roundHalfUp(totalSumForCalculation);

    const totalEl = document.getElementById('order-total-price');
    if (totalEl) {
        totalEl.style.display = 'flex';
        totalEl.style.alignItems = 'center';
        totalEl.style.whiteSpace = 'nowrap';
        totalEl.style.gap = '8px';

        totalEl.innerHTML = `
            <span style="font-size: 14px; color: #64748b; font-weight: normal;">Итого:</span>
            <span style="font-weight: 800;">${formatSmartJS(totalSumForCalculation)} ֏</span>
        `;
    }
    window.currentOrderTotal = totalSumForCalculation;
}


function addItemToEdit() {
    const select = document.getElementById('add-item-select');
    const qtyInput = document.getElementById('add-item-qty');
    const pId = select.value;

    const modalTitleEl = document.getElementById('modal-title');
    const modalTitle = modalTitleEl ? modalTitleEl.innerText.toUpperCase() : "";
    const isReturnOrWriteOff = modalTitle.includes("ВОЗВРАТ") || modalTitle.includes("СПИСАНИЕ") || modalTitle.includes("🔄");

    if (!pId) {
        return showToast("Сначала выберите товар из списка!", "error");
    }

    const qtyToAdd = parseInt(qtyInput.value);
    if (isNaN(qtyToAdd) || qtyToAdd <= 0) {
        return showToast("Введите корректное количество!", "error");
    }

    const product = productsData.find(p => p.id == pId);

    if (product) {
        const alreadyInList = tempItems[pId] || 0;
        const totalNewQty = alreadyInList + qtyToAdd;

        // Для заказов проверяем остаток, для возвратов — нет
        if (!isReturnOrWriteOff && totalNewQty > (product.stockQuantity || 0)) {
            qtyInput.style.border = "2px solid #ef4444";
            return showToast(`Недостаточно на складе! В наличии: ${product.stockQuantity}`, "error");
        }

        // --- НОВАЯ ЛОГИКА ДЛЯ ЦЕН ВОЗВРАТА ---
        if (isReturnOrWriteOff) {
            if (!window.tempItemPrices) window.tempItemPrices = {};
            // Если товара еще нет в буфере цен, берем текущую цену из справочника
            if (!window.tempItemPrices[pId]) {
                window.tempItemPrices[pId] = product.price;
            }
        } else {
            // Для обычных заказов сбрасываем акции, чтобы они пересчитались
            if (window.currentOrderPromos) {
                delete window.currentOrderPromos[pId];
            }
        }

        qtyInput.style.border = "";
        tempItems[pId] = totalNewQty;

        // Перерисовываем таблицу (она подхватит цену из window.tempItemPrices)
        renderItemsTable(tempItems, true);

        select.value = "";
        qtyInput.value = 1;
        showToast(`Добавлено: ${product.name}`, "success");
        select.focus();
    } else {
        showToast("Ошибка: Товар не найден", "error");
    }
}


async function saveFullChanges(id) {
    const shopInput = document.getElementById('edit-shop');
    const shopName = shopInput?.value.trim();
    const deliveryDate = document.getElementById('edit-delivery')?.value;

    if (!shopName || !deliveryDate) {
        return showToast("Магазин и дата доставки обязательны", "info");
    }

    // 1. Сбор товаров из UI
    const itemsToSave = {};
    document.querySelectorAll('.qty-input-active').forEach(input => {
        const pId = input.id.replace('input-qty-', '');
        const val = parseInt(input.value);
        if (!isNaN(val) && val > 0) {
            itemsToSave[pId] = val;
            tempItems[pId] = val;
        } else {
            delete tempItems[pId];
        }
    });

    if (Object.keys(itemsToSave).length === 0) {
        return showToast("Нельзя сохранить пустой заказ", "info");
    }

    // Блокируем кнопку сохранения
    const saveBtn = event?.target;
    if (saveBtn && saveBtn.tagName === 'BUTTON') saveBtn.disabled = true;

    // 2. ВЫЗЫВАЕМ ПРОВЕРКУ АКЦИЙ (ОТКРОЕТ ОКНО)
    checkAndApplyPromos(itemsToSave, async (selectedPromos) => {

        // Формируем карту акций для сервера
        const promoMap = {};
        selectedPromos.forEach(promo => {
            if (promo.items) {
                Object.entries(promo.items).forEach(([pId, promoPercent]) => {
                    if (itemsToSave[pId]) promoMap[pId] = promoPercent;
                });
            }
        });

        // 3. ПОДГОТОВКА ДАННЫХ ДЛЯ ОТПРАВКИ
        const percentInput = document.getElementById('order-discount-percent') || document.getElementById('new-op-percent');
        let discountPercent = percentInput ? parseFloat(percentInput.value) || 0 : 0;

        const data = {
            id: id,
            shopName: shopName,
            deliveryDate: deliveryDate,
            paymentMethod: document.getElementById('edit-payment').value,
            needsSeparateInvoice: document.getElementById('edit-invoice-type').value === "true",
            carNumber: document.getElementById('edit-car-number').value,
            discountPercent: discountPercent,
            comment: document.getElementById('edit-comment')?.value || "",
            items: itemsToSave,
            appliedPromoItems: promoMap // ПЕРЕДАЕМ ВЫБРАННЫЕ АКЦИИ
        };

        try {
            // Проверка магазина
            const checkRes = await fetch(`/api/clients/search-fast?keyword=${encodeURIComponent(shopName)}`);
            const clients = await checkRes.json();
            const foundClient = clients.find(c => c.name.toLowerCase() === shopName.toLowerCase());

            if (!foundClient) {
                shopInput.style.border = "2px solid #ef4444";
                if (saveBtn) saveBtn.disabled = false;
                return showToast(`Ошибка: Магазин "${shopName}" не найден!`, "error");
            }
            data.shopName = foundClient.name;

            // ФИНАЛЬНЫЙ ЗАПРОС НА СОХРАНЕНИЕ
            const response = await fetch(`/api/admin/orders/${id}/full-edit`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });

            const result = await response.json();

            if (!response.ok) {
                let rawMsg = result.message || result.error || "Ошибка сервера";
                let cleanMessage = rawMsg.replace(/^\d+\s+[A-Z_]+\s+"?|"?$/g, '').trim();
                showToast(cleanMessage, "error");

                if (cleanMessage.includes("Недостаточно")) {
                    document.querySelectorAll('#order-items-body tr').forEach(row => {
                        const productNameInRow = row.cells[0]?.innerText || "";
                        if (cleanMessage.includes(productNameInRow.trim())) {
                            row.style.backgroundColor = "#fee2e2";
                            row.style.border = "2px solid #ef4444";
                            row.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        }
                    });
                }
                if (saveBtn) saveBtn.disabled = false;
                return;
            }

            showToast("Заказ успешно обновлен", "success");
            setTimeout(() => { window.location.reload(); }, 800);

        } catch (e) {
            console.error("Ошибка при сохранении заказа:", e);
            showToast("Критическая ошибка: " + e.message, "error");
            if (saveBtn) saveBtn.disabled = false;
        }
    });
}

// Универсальный метод отправки изменений
async function executeEditRequest(data, btn) {
    try {
        const response = await fetch(`/api/admin/orders/${data.id}/edit`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value || ""
            },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            showToast("Изменения сохранены", "success");
            setTimeout(() => location.reload(), 600);
        } else {
            const err = await response.json();
            showToast(err.error || "Ошибка при сохранении", "error");
            if (btn) { btn.disabled = false; btn.innerText = "Сохранить"; }
        }
    } catch (e) {
        showToast("Ошибка сети", "error");
        if (btn) { btn.disabled = false; btn.innerText = "Сохранить"; }
    }
}



async function performEditSubmit(id, shopName, deliveryDate, items, promoMap) {
    const data = {
        shopName: shopName,
        deliveryDate: deliveryDate,
        paymentMethod: document.getElementById('edit-payment')?.value,
        needsSeparateInvoice: document.getElementById('edit-invoice-type')?.value === "true",
        carNumber: document.getElementById('edit-car-number')?.value,
        discountPercent: parseFloat(document.getElementById('order-discount-percent')?.value) || 0,
        comment: document.getElementById('edit-comment')?.value || "",
        items: items,
        appliedPromoItems: promoMap
    };

    const response = await fetch(`/api/admin/orders/${id}/full-edit`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });

    if (response.ok) {
        showToast("Заказ успешно обновлен", "success");
        setTimeout(() => location.reload(), 800);
    } else {
        const err = await response.json();
        showToast(err.error || "Ошибка сохранения", "error");
    }
}


async function saveReturnChanges(id) {
    const shopInput = document.getElementById('edit-ret-shop');
    const shopName = shopInput.value.trim();

    // 1. ЖЕСТКАЯ ВАЛИДАЦИЯ МАГАЗИНА
    try {
        const checkRes = await fetch(`/api/clients/search-fast?keyword=${encodeURIComponent(shopName)}`);
        const clients = await checkRes.json();

        const foundClient = clients.find(c => c.name.toLowerCase() === shopName.toLowerCase());

        if (!foundClient) {
            shopInput.style.border = "2px solid #ef4444";
            shopInput.focus();
            return showToast(`Ошибка: Магазин "${shopName}" не найден!`, "error");
        }

        shopInput.style.border = "";

        // 2. СБОР ТОВАРОВ И КАСТОМНЫХ ЦЕН ИЗ ТАБЛИЦЫ
        const itemsToSave = {};
        const itemPricesToSave = {};

        // Проходим по строкам таблицы, чтобы собрать и кол-во, и цену
        document.querySelectorAll('#order-items-body tr[data-base-price]').forEach(row => {
            const pId = row.id.replace('row-', '');
            const qtyInput = row.querySelector('.qty-input-active');
            const priceInput = row.querySelector('.item-price-input'); // Наш новый инпут цены

            const qty = qtyInput ? parseInt(qtyInput.value) || 0 : 0;
            // Берем цену из инпута, если его нет (режим просмотра) - из атрибута строки
            const price = priceInput ? parseFloat(priceInput.value) || 0 : parseFloat(row.dataset.basePrice) || 0;

            if (qty > 0) {
                itemsToSave[pId] = qty;
                itemPricesToSave[pId] = price; // Сохраняем индивидуальную цену для товара
            }
        });

        if (Object.keys(itemsToSave).length === 0) {
            return showToast("Состав возврата не может быть пустым", "info");
        }

        const originalReturn = returnsData.find(r => r.id == id);

        // 3. Сбор данных для отправки
        const data = {
            id: id,
            shopName: foundClient.name,
            managerId: originalReturn ? originalReturn.managerId : "OFFICE",
            returnDate: document.getElementById('edit-ret-date').value,
            returnReason: document.getElementById('edit-ret-reason').value,
            carNumber: document.getElementById('edit-ret-car').value.trim(),
            comment: document.getElementById('edit-ret-comment').value.trim(),
            items: itemsToSave,
            itemPrices: itemPricesToSave, // ОТПРАВЛЯЕМ КАРТУ КАСТОМНЫХ ЦЕН
            discountPercent: 0
        };

        // 4. Отправка на сервер
        const response = await fetch(`/api/admin/returns/${id}/edit`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            // 5. Обновление локальных данных (по желанию, так как ниже reload)
            if (originalReturn) {
                originalReturn.items = { ...itemsToSave };
                originalReturn.itemPrices = { ...itemPricesToSave };
                originalReturn.shopName = data.shopName;
            }

            showToast("Возврат успешно сохранен с учетом цен", "success");
            setTimeout(() => location.reload(), 600);
        } else {
            const err = await response.json();
            showToast(err.error || "Ошибка сохранения", "error");
        }

    } catch (e) {
        console.error("Ошибка при сохранении возврата:", e);
        showToast("Не удалось сохранить изменения возврата", "error");
    }
}


function enableReturnEdit(id) {
    // 1. Поиск возврата по ID
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return showToast("Ошибка: Возврат не найден", "error");

    // 2. СИНХРОНИЗАЦИЯ ТОВАРОВ И ЦЕН
    tempItems = syncTempItems(ret.items);

    // ИСПРАВЛЕНО: Копируем сохраненные цены в буфер редактирования
    // Если цен в документе еще нет (старый возврат), renderItemsTable возьмет базовые
    window.tempItemPrices = ret.itemPrices ? { ...ret.itemPrices } : {};

    // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: СБРОС ПРОЦЕНТА
    const percentInput = document.getElementById('order-discount-percent') || document.getElementById('new-op-percent');
    if (percentInput) {
        percentInput.value = "0";
    }

    // ПОЛУЧАЕМ ОГРАНИЧЕНИЯ ДАТ ДЛЯ 2026 ГОДА
    const dates = getSmartDeliveryDates();

    document.getElementById('modal-title').innerText = "✏️ Редактирование ВОЗВРАТА #" + id;

    const info = document.getElementById('order-info');

    // 3. Отрисовка сетки с УМНЫМ ПОИСКОМ МАГАЗИНА
    info.innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background: #fff1f2; padding: 15px; border-radius: 10px; border: 1px solid #fecdd3;">
            <div style="grid-column: span 2;">
                <label style="font-size:11px; font-weight:800; color:#9f1239;">МАГАЗИН (Поиск)</label>
                <input type="text" id="edit-ret-shop" class="form-control"
                       list="edit-ret-clients-datalist"
                       value="${ret.shopName}"
                       placeholder="Введите название...">
                <datalist id="edit-ret-clients-datalist"></datalist>
            </div>
            <div>
                <label style="font-size:11px; font-weight:800; color:#9f1239;">НОМЕР АВТО</label>
                <input type="text" id="edit-ret-car" class="form-control" value="${ret.carNumber || ''}" placeholder="35XX000">
            </div>

            <div style="margin-top:10px;">
                <label style="font-size:11px; font-weight:800; color:#9f1239;">ПРИЧИНА</label>
                <select id="edit-ret-reason" class="form-select">
                    ${returnReasons.map(r => {
                        const val = (typeof r === 'object') ? (r.name || r) : r;
                        return `<option value="${val}" ${ret.returnReason === val ? 'selected' : ''}>${translateReason(val)}</option>`;
                    }).join('')}
                </select>
            </div>
            <div style="margin-top:10px;">
                <label style="font-size:11px; font-weight:800; color:#9f1239;">ДАТА ВОЗВРАТА</label>
                <input type="date" id="edit-ret-date" class="form-control"
                       min="${dates.min}"
                       value="${convertDateToISO(ret.returnDate || ret.createdAt)}"
                       onchange="if(this.value < '${dates.min}') { alert('Нельзя выбрать прошедшую дату!'); this.value='${dates.min}'; }">
            </div>
            <div style="margin-top:10px;">
                <label style="font-size:11px; font-weight:800; color:#9f1239;">КОММЕНТАРИЙ</label>
                <input type="text" id="edit-ret-comment" class="form-control" value="${ret.comment || ''}" placeholder="Заметка...">
            </div>
        </div>
        <input type="hidden" id="order-discount-percent" value="0">`;

    // 4. АКТИВАЦИЯ ПОИСКА
    initSmartClientSearch('edit-ret-shop', 'edit-ret-clients-datalist');

    // 5. Рендерим состав товаров (второй параметр true включает редактирование и input-ы для цен)
    renderItemsTable(tempItems, true);

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981; padding: 10px 25px;" onclick="saveReturnChanges(${id})">Сохранить</button>
        <button class="btn-primary" style="background:#64748b; padding: 10px 25px;" onclick="openReturnDetails(${id})">Отмена</button>
    `;
}


function openReturnDetails(id) {
    const ret = (returnsData || []).find(r => r.id == id);
    if (!ret) return showToast("Возврат не найден", "error");

    tempItems = syncTempItems(ret.items);
    window.tempItemPrices = ret.itemPrices ? { ...ret.itemPrices } : {};

    const isConfirmed = ret.status === 'CONFIRMED';

    const percentInput = document.getElementById('order-discount-percent') || document.getElementById('new-op-percent');
    if (percentInput) {
        percentInput.value = "0";
    }

    document.getElementById('modal-title').innerHTML = `
        Детали операции
        <span class="badge ${isConfirmed ? 'bg-success' : 'bg-warning'}" style="margin-left:10px;">
            ${isConfirmed ? 'Проведено' : 'Черновик'}
        </span>
        <span class="badge" style="margin-left:5px; background-color: #64748b;">ВОЗВРАТ №${ret.id}</span>
    `;

    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background-color: #fff1f2; padding: 15px; border-radius: 10px; margin-top: 15px; border: 1px solid #fecdd3;">
            <div><small style="color: #9f1239; font-weight: 700;">МАГАЗИН:</small><br><b>${ret.shopName}</b></div>
            <div><small style="color: #9f1239; font-weight: 700;">МЕНЕДЖЕР:</small><br><b>${ret.managerId || '---'}</b></div>
            <div><small style="color: #9f1239; font-weight: 700;">НОМЕР АВТО:</small><br><b>${ret.carNumber || '---'}</b></div>
            <div style="border-top: 1px solid #fecdd3; padding-top: 10px;"><small style="color: #9f1239; font-weight: 700;">ПРИЧИНА:</small><br><b style="color:#ef4444;">${translateReason(ret.returnReason)}</b></div>
            <div style="border-top: 1px solid #fecdd3; padding-top: 10px;"><small style="color: #9f1239; font-weight: 700;">ДОСТАВКА:</small><br><b>${formatDate(ret.returnDate || ret.createdAt).split(' ')[0]}</b></div>
            <div style="border-top: 1px solid #fecdd3; padding-top: 10px;"><small style="color: #9f1239; font-weight: 700;">КОММЕНТАРИЙ:</small><br><i>${ret.comment || '---'}</i></div>
        </div>
        <input type="hidden" id="order-discount-percent" value="0">
    `;

    renderItemsTable(tempItems, false);

    // --- ИСПРАВЛЕНИЕ ОТОБРАЖЕНИЯ СУММЫ И КНОПОК ---
    const footer = document.getElementById('order-footer-actions');
    const totalEl = document.getElementById('order-total-price');

    // Настраиваем блок Итого, чтобы он не переносился и всегда был слева
    if (totalEl) {
        totalEl.style.display = 'flex';
        totalEl.style.alignItems = 'center';
        totalEl.style.whiteSpace = 'nowrap';
        totalEl.style.marginRight = 'auto'; // Отталкивает кнопки вправо
    }

    const commonBtns = `
        <button class="btn-primary" style="background-color:#475569" onclick="printReturn(${ret.id})">🖨 Печать</button>
        <button class="btn-primary" style="background-color:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>
    `;

    if (!isConfirmed) {
        footer.innerHTML = `
            <button class="btn-primary" style="background-color:#10b981" onclick="confirmReturn(${ret.id})">✅ Провести</button>
            <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">✏️ Изменить</button>
            <button class="btn-primary" style="background-color:#ef4444" onclick="deleteReturnOrder(${ret.id})">❌ Удалить</button>
            ${commonBtns}
        `;
    } else {
        // Убираем flex:1 у статуса, чтобы он не занимал всё место и не разрывал строку
        footer.innerHTML = `
            <div style="color: #166534; font-weight: bold; margin-right: 15px; white-space: nowrap;">✓ Операция проведена</div>
            ${commonBtns}
        `;
    }

    // Гарантируем выравнивание всего футера
    footer.style.display = 'flex';
    footer.style.justifyContent = 'flex-end';
    footer.style.alignItems = 'center';

    openModal('modal-order-view');
}


function handleClientChangeInEdit(clientName) {
    // 1. Находим данные клиента в локальном справочнике
    const client = clientsData.find(c => c.name === clientName);

    if (client) {
        // 2. Находим скрытое поле процента
        const pInput = document.getElementById('order-discount-percent') || document.getElementById('new-op-percent');

        if (pInput) {
            // 3. Обновляем значение процента из данных клиента
            const newPercent = client.defaultPercent || 0;
            pInput.value = newPercent;

            // 4. Показываем уведомление (опционально, для удобства админа)
            showToast(`Магазин изменен: ${client.name} (Скидка: ${newPercent}%)`, "info");

            // 5. ПЕРЕРИСОВЫВАЕМ ТАБЛИЦУ
            // Мы вызываем renderItemsTable с текущими товарами (tempItems)
            // и флагом редактирования (true). Она сама подтянет новый процент из pInput.
            renderItemsTable(tempItems, true);
        }
    }
}


async function saveClientChanges(id) {
    const data = {
        name: document.getElementById('edit-client-name').value,
        category: document.getElementById('edit-client-category').value,
        ownerName: document.getElementById('edit-client-owner').value,
        inn: document.getElementById('edit-client-inn').value,
        phone: document.getElementById('edit-client-phone').value,
        address: document.getElementById('edit-client-address').value,
        debt: parseFloat(document.getElementById('edit-client-debt').value) || 0,
        bankName: document.getElementById('edit-client-bank-name').value,
        bankAccount: document.getElementById('edit-client-bank').value,
        managerId: document.getElementById('edit-client-manager').value,
        routeDay: document.getElementById('edit-client-route-day').value,
        defaultPercent: parseFloat(document.getElementById('edit-client-percent')?.value) || 0
    };

    try {
        // 1. Отправляем запрос (используем ваш fetch с перехватчиком)
        // Если прав нет, глобальный fetch сделает Promise.reject и код уйдет в catch
        await fetch(`/api/admin/clients/${id}/edit`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        // 2. Код ниже сработает ТОЛЬКО при успехе (status 200)
        const idx = clientsData.findIndex(c => c.id == id);
        if (idx !== -1) {
            clientsData[idx] = {...clientsData[idx], ...data};

            const row = document.querySelector(`tr[onclick*="openClientDetails(${id})"]`);
            if (row) {
                row.cells[0].innerText = data.name;
                row.cells[1].innerText = data.address;
                row.cells[2].innerText = data.category || '---';
                row.cells[3].innerText = data.debt.toLocaleString() + ' ֏';
                row.cells[3].className = data.debt > 0 ? 'price-down' : '';
            }
        }

        showToast("Данные клиента успешно обновлены", "success");
        openClientDetails(id);

    } catch (e) {
        // ИСПРАВЛЕНИЕ: Блок пустой для уведомлений.
        // Тост об ошибке доступа или некорректных данных уже показал глобальный fetch.
        console.error("Операция отклонена или произошла ошибка:", e.message);
    }
}


function applyClientCategoryFilter(category) {
    // Получаем текущие параметры URL
    const url = new URL(window.location.href);

    // Устанавливаем нужные параметры
    url.searchParams.set('activeTab', 'tab-clients');
    url.searchParams.set('clientCategory', category);
    url.searchParams.set('clientPage', '0'); // Сбрасываем на первую страницу при смене фильтра

    // Переходим по новой ссылке
    window.location.href = url.toString();
}



function filterTable(inputId, tableBodyId) {
    const input = document.getElementById(inputId);
    if (!input) return;

    const filter = input.value.toUpperCase();
    const tbody = document.getElementById(tableBodyId);
    if (!tbody) return;

    const rows = tbody.getElementsByTagName("tr");

    for (let i = 0; i < rows.length; i++) {
        // Пропускаем строки-заголовки категорий на складе (у них есть спец. класс)
        if (rows[i].classList.contains('js-category-toggle')) continue;

        const text = rows[i].textContent || rows[i].innerText;
        // Если текст совпадает с фильтром, показываем строку, иначе скрываем
        rows[i].style.display = text.toUpperCase().includes(filter) ? "" : "none";
    }
}


function openPaymentModal(invoiceId) {
    const invoiceRow = document.querySelector(`tr[onclick*="openPaymentModal(${invoiceId})"]`) ||
        document.querySelector(`tr:has(button[onclick*="openPaymentModal(${invoiceId})"])`);

    document.getElementById('pay-invoice-id').value = invoiceId;

    // Пытаемся найти номер счета в таблице для отображения в модалке
    const invNum = invoiceRow ? invoiceRow.cells[0].innerText : `#${invoiceId}`;
    document.getElementById('pay-invoice-display').innerText = "СЧЕТ " + invNum;

    openModal('modal-payment');
}


function printDailySummary() {
    const tab = document.getElementById('tab-orders');
    const selectedIds = Array.from(tab.querySelectorAll('.order-print-check:checked')).map(cb => cb.value);

    if (selectedIds.length === 0) {
        showToast("Выберите заказы для сводки!", "error");
        return;
    }

    const frame = document.getElementById('printFrame');
    const url = '/admin/orders/print-daily-summary';

    const newFrame = frame.cloneNode(true);
    frame.parentNode.replaceChild(newFrame, frame);

    newFrame.addEventListener('load', function() {
        if (newFrame.contentWindow.location.href === "about:blank") return;
        setTimeout(() => {
            newFrame.contentWindow.focus();
            newFrame.contentWindow.print();
        }, 300);
    }, { once: true });

    submitAsPost(url, selectedIds, 'printFrame');
}


function convertDateToISO(dateVal) {
    if (!dateVal || dateVal === '---') return "";

    try {
        let date;
        // Если это объект из Java
        if (typeof dateVal === 'object' && dateVal.year) {
            date = new Date(dateVal.year, (dateVal.monthValue || dateVal.month) - 1, dateVal.dayOfMonth || dateVal.day);
        } else {
            // Если это строка (заменяем точки на дефисы для парсинга yyyy-mm-dd)
            let s = dateVal.split(' ')[0].replace(/\./g, '-');
            // Если формат dd-mm-yyyy, переделываем в yyyy-mm-dd
            if (s.indexOf('-') === 2) {
                const p = s.split('-');
                s = `${p[2]}-${p[1]}-${p[0]}`;
            }
            date = new Date(s);
        }

        if (isNaN(date.getTime())) return "";
        return date.toISOString().split('T')[0];
    } catch (e) {
        console.error("Ошибка ISO конвертации:", e);
        return "";
    }
}

function showStatus(text, isError = false) {
    const container = document.getElementById('order-footer-actions');
    const old = document.getElementById('status-notify');
    if (old) old.remove();

    const statusDiv = document.createElement('div');
    statusDiv.id = "status-notify";

    if (text.includes("Ошибка")) {
        // Для ошибок используем красный стиль
        statusDiv.className = "stock-error-box";
        statusDiv.innerHTML = `<div style="font-weight: 700; color: #ef4444;">${text}</div>`;
    } else {
        // Для успеха используем простой текст "Добавлено"
        statusDiv.style = `color: #10b981; font-weight: 700; margin-right: 15px;`;
        statusDiv.innerText = "Добавлено";
    }

    container.prepend(statusDiv);
    // Делаем уведомление менее навязчивым, исчезает быстрее
    setTimeout(() => {
        if (statusDiv) statusDiv.remove();
    }, 1500);
}


function applySingleQty(pId) {
    const input = document.getElementById(`input-qty-${pId}`);
    if (!input || input.value.trim() === "") return;

    let newVal = parseInt(input.value);
    const product = productsData.find(p => p.id == pId);

    if (isNaN(newVal) || newVal < 0) {
        input.value = tempItems[pId] || 1;
        return;
    }

    // Если 0 - удаляем
    if (newVal === 0) {
        removeItemFromEdit(pId);
        return;
    }

    // Проверка остатков
    const modalTitle = document.getElementById('modal-title').innerText.toLowerCase();
    if (modalTitle.includes("заказ") && !modalTitle.includes("списание") && product && newVal > product.stockQuantity) {
        showToast(`На складе только: ${product.stockQuantity}`, "error");
        newVal = product.stockQuantity;
        input.value = newVal;
    }

    // Обновляем данные
    tempItems[pId] = newVal;

    // Мгновенное обновление суммы в строке без перерисовки всей таблицы
    const rowTotalEl = document.getElementById(`total-row-${pId}`);
    if (rowTotalEl && product) {
        const newTotal = product.price * newVal;
        rowTotalEl.innerText = newTotal.toLocaleString() + " ֏";
        // Маленький эффект подсветки при обновлении
        rowTotalEl.style.color = "#10b981";
        setTimeout(() => rowTotalEl.style.color = "", 500);
    }

    calculateCurrentTempTotal();
    showStatus("Обновлено");
}

function getSmartDeliveryDates() {
    const now = new Date();
    // Сегодня в формате YYYY-MM-DD для атрибута min
    const todayStr = now.toISOString().split('T')[0];

    let deliveryDate = new Date();
    deliveryDate.setDate(now.getDate() + 1);

    // Если завтра воскресенье (0), переносим на понедельник
    if (deliveryDate.getDay() === 0) {
        deliveryDate.setDate(deliveryDate.getDate() + 1);
    }

    const defaultStr = deliveryDate.toISOString().split('T')[0];

    return {
        min: todayStr, // Запрет на всё, что раньше сегодня
        default: defaultStr // Завтра или понедельник
    };
}


function applyInvoiceFilters() {
    const start = document.getElementById('inv-date-start').value;
    const end = document.getElementById('inv-date-end').value;
    const manager = document.getElementById('filter-invoice-manager').value;
    const status = document.getElementById('filter-invoice-status').value;

    const params = new URLSearchParams();
    params.set('activeTab', 'tab-invoices');
    params.set('invoicePage', '0'); // Всегда сброс на 0 при новом фильтре

    if (start) params.set('invoiceStart', start);
    if (end) params.set('invoiceEnd', end);
    if (manager) params.set('invoiceManager', manager);
    if (status) params.set('invoiceStatus', status);

    window.location.href = window.location.pathname + '?' + params.toString();
}


function initDeliveryDateLogic() {
    const dateInput = document.getElementById('route-date-select');
    if (!dateInput) return;

    // 1. Вычисляем "Логистическое завтра"
    let deliveryDate = new Date();
    deliveryDate.setDate(deliveryDate.getDate() + 1); // +1 день

    // Если завтра воскресенье (0), переносим на понедельник
    if (deliveryDate.getDay() === 0) {
        deliveryDate.setDate(deliveryDate.getDate() + 1);
    }

    const toISODate = (date) => {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    };

    const finalDateStr = toISODate(deliveryDate);

    // 2. Устанавливаем значение и ограничение (минимум - сегодня)
    dateInput.value = finalDateStr;
    dateInput.min = toISODate(new Date());

    // 3. Защита от ручного выбора воскресенья
    dateInput.addEventListener('change', function() {
        const selected = new Date(this.value);
        if (selected.getDay() === 0) {
            showToast("Воскресенье — выходной. Пожалуйста, выберите рабочий день.", "info");
            this.value = finalDateStr;
        }
    });
}


function openPromoModal(productId) {
    const p = productsData.find(prod => prod.id == productId);
    if (!p) return;

    const today = new Date().toISOString().split('T')[0];
    let managerOptions = getManagerOptionsHTML();
    // const managerOptions = managerIdList.map(m => `<option value="${m}">${m}</option>`).join('');

    document.getElementById('modal-title').innerHTML = `📢 НАСТРОЙКА АКЦИИ: ${p.name}`;
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; background: #fff4e5; padding: 15px; border-radius: 10px; border: 1px solid #ff9800;">
            <div><small>ТОВАР:</small><br><b>${p.name}</b></div>
            <div><small>ОСТАТОК:</small><br><b>${p.stockQuantity} шт</b></div>
            <div><small>ТЕКУЩАЯ ЦЕНА:</small><br><b>${p.price} ֏</b></div>

            <div style="margin-top:10px;"><label>МЕНЕДЖЕР</label><select id="promo-manager" class="form-select">${managerOptions}</select></div>
            <div style="margin-top:10px;"><label>ОТ (Дата)</label><input type="date" id="promo-start" class="form-control" value="${today}"></div>
            <div style="margin-top:10px;"><label>ДО (Дата)</label><input type="date" id="promo-end" class="form-control" value="${today}"></div>

            <div style="margin-top:10px; grid-column: span 3;">
                <label>ПРОЦЕНТ АКЦИИ (%)</label>
                <input type="number" id="promo-percent" class="form-control" style="border: 2px solid #ff9800; font-weight: 900;" value="${p.promoPercent || 0}">
            </div>
        </div>
    `;

    document.getElementById('order-items-body').innerHTML = `<tr><td colspan="6" style="text-align:center;">Информационное окно создания акции</td></tr>`;
    document.getElementById('order-total-price').innerText = "";

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#ff9800" onclick="savePromo(${productId})">СОХРАНИТЬ</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">ЗАКРЫТЬ</button>
    `;

    openModal('modal-order-view');
}

async function savePromo(productId) {
    const data = {
        productId: productId,
        managerName: document.getElementById('promo-manager').value,
        startDate: document.getElementById('promo-start').value,
        endDate: document.getElementById('promo-end').value,
        percent: parseFloat(document.getElementById('promo-percent').value) || 0
    };

    const response = await fetch('/api/products/promo/create', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(data)
    });

    if (response.ok) {
        showToast("Акция успешно сохранена!", "success");
        location.reload();
    }
}

function updatePromoTimers() {
    const today = new Date();
    // Предположим, у нас есть данные об акциях
    document.querySelectorAll('[id^="timer-"]').forEach(el => {
        const productId = el.id.replace('timer-', '');
        const p = productsData.find(prod => prod.id == productId);

        // Здесь должна быть логика получения endDate из БД.
        // Пока для примера, если есть промо-процент:
        if (p && p.promoPercent > 0) {
            const endDate = new Date("2026-02-20"); // В реальности берем из p.promoEndDate
            const diffTime = endDate - today;
            const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

            if (diffDays > 0) {
                el.innerText = `${diffDays} дн.`;
                el.style.color = diffDays <= 1 ? "red" : "green";
                if(diffDays <= 1) el.classList.add('fw-bold');
            } else {
                el.innerText = "Истекло";
                el.style.color = "red";
            }
        }
    });
}


function openCreatePromoModal() {
    tempPromoItems = {};
    const today = new Date().toISOString().split('T')[0];
    // const managerOptions = managerIdList.map(m => `<option value="${m}">${m}</option>`).join('');

    document.getElementById('modal-title').innerText = "🔥 СОЗДАНИЕ НОВОЙ АКЦИИ";
    let managerOptions = getManagerOptionsHTML();
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 15px; background: #fff7ed; padding: 15px; border-radius: 10px; border: 1px solid #fb923c;">
            <div style="grid-column: span 2;">
                <label>ИМЯ АКЦИИ (ПРОМО):</label>
                <input type="text" id="promo-title" class="form-control" placeholder="Напр: Промо ZOVQ январь">
            </div>
            <div><label>МЕНЕДЖЕР:</label><select id="promo-manager" class="form-select">${managerOptions}</select></div>
            <div style="display:flex; gap:10px;">
                <div style="flex:1"><label>ОТ:</label><input type="date" id="promo-start" class="form-control" value="${today}"></div>
                <div style="flex:1"><label>ДО:</label><input type="date" id="promo-end" class="form-control" value="${today}"></div>
            </div>
        </div>
    `;

    renderPromoItemsTable(true); // true = режим редактирования

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="submitPromo(false)">СОХРАНИТЬ</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">ЗАКРЫТЬ</button>
    `;
    openModal('modal-order-view');
}


async function checkPromosBeforeSave(items) {
    const res = await fetch('/api/promos/check-active', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(Object.keys(items))
    });
    const activePromos = await res.json();

    if (activePromos.length > 0) {
        // Формируем красивые карточки с РАБОЧИМИ ползунками
        // Внутри функции checkPromosBeforeSave или там, где рисуете список:
        const promoListHtml = activePromos.map(p => `
    <label class="promo-card" style="display: flex; align-items: center; justify-content: space-between; width: 100%; box-sizing: border-box;">
        <div style="flex-grow: 1; text-align: left; pointer-events: none;">
            <div style="font-weight: 700; color: #1e293b; font-size: 13px;">${p.title || p.name}</div>
            <div style="font-size: 11px; color: #64748b;">Для менеджера: ${p.managerId}</div>
        </div>
        
        <!-- Чекбокс ДОЛЖЕН быть перед .custom-switch -->
        <input type="checkbox" 
               class="promo-checkbox" 
               data-promo-id="${p.id}" 
               checked 
               style="display: none;"> <!-- Скрываем, но он работает через label -->
        
        <!-- Визуальный ползунок -->
        <div class="custom-switch"></div>
    </label>
`).join('');


        showConfirmModal("Нашли акции!", `
            <div style="text-align:center;">
                ${promoListHtml}
                <p style="font-size:11px; color:#ef4444; margin-top:15px;">* При активации спец. цены заменят скидку магазина на эти товары.</p>
            </div>
        `, () => {
            // Собираем ТОЛЬКО те акции, где ползунок остался включенным
            const selectedPromos = [];
            document.querySelectorAll('.promo-apply-checkbox').forEach(cb => {
                if (cb.checked) {
                    const id = cb.getAttribute('data-promo-id');
                    const found = activePromos.find(ap => ap.id == id);
                    if (found) selectedPromos.push(found);
                }
            });
            saveOrderWithPromos(selectedPromos);
        });
    } else {
        performFinalOrderSave();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const activeTab = new URLSearchParams(window.location.search).get('activeTab');
    if (activeTab === 'tab-promos' || document.getElementById('tab-promos')?.classList.contains('active')) {
        loadPromosByPeriod();
    }
});

async function loadPromosByPeriod() {
    const startInput = document.getElementById('promo-filter-start');
    const endInput = document.getElementById('promo-filter-end');

    const start = startInput?.value;
    const end = endInput?.value;

    if (!start || !end) return;

    try {
        const tbody = document.getElementById('promos-table-body');
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:20px;">⌛ Загрузка данных...</td></tr>';

        // Мы просто запрашиваем данные. Сервер сам поймет, кто спрашивает (через сессию).
        const response = await fetch(`/api/admin/promos/filter?start=${start}&end=${end}`);
        if (!response.ok) throw new Error("Ошибка сервера");

        const allPromos = await response.json();

        // УДАЛЯЕМ ФИЛЬТРАЦИЮ В JS! Просто рисуем то, что прислал сервер.
        renderPromosList(allPromos);

        const periodLabel = document.getElementById('promo-period-label');
        if (periodLabel) {
            periodLabel.innerText = `${formatDate(start)} — ${formatDate(end)}`;
        }

        setTimeout(refreshPromoCounters, 100);

    } catch (e) {
        console.error("Ошибка загрузки акций:", e);
        showToast("Не удалось загрузить список акций", "error");
        document.getElementById('promos-table-body').innerHTML =
            '<tr><td colspan="7" style="text-align:center; color:red;">Ошибка соединения с сервером</td></tr>';
    }
}

function renderPromosList(promos) {
    const tbody = document.getElementById('promos-table-body');

    // Если сервер ничего не прислал (список пуст)
    if (!promos || promos.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:40px; color:#94a3b8;">Доступных акций за этот период не найдено</td></tr>';
        return;
    }

    // Рисуем всё, что пришло от сервера без дополнительных проверок
    tbody.innerHTML = promos.map(p => `
        <tr onclick="openPromoDetails(${p.id})" style="cursor:pointer;">
            <td><b style="color:#1e293b;">${p.title}</b></td>
            <td style="color:var(--accent); font-weight:600;">
                <span class="badge" style="background:#e0f2fe; color:#0369a1; padding: 4px 8px;">${p.managerId}</span>
            </td>
            <td>${formatDate(p.startDate)}</td>
            <td>${formatDate(p.endDate)}</td>
            <td>
                <span class="promo-days-left" data-end="${p.endDate}" style="font-weight:700;">---</span>
            </td>
            <td style="text-align:center;">
                <span class="badge bg-light text-dark" style="border:1px solid #ddd; padding: 5px 10px;">
                    ${Object.keys(p.items || {}).length} поз.
                </span>
            </td>
            <td>
                <span class="badge ${getStatusClass(p.status)}">${p.status}</span>
            </td>
        </tr>
    `).join('');

    refreshPromoCounters();
}

function getStatusClass(status) {
    switch(status) {
        case 'ACTIVE': return 'bg-success';
        case 'FINISHED': return 'bg-secondary';
        case 'PENDING': return 'bg-warning text-dark';
        default: return 'bg-light text-dark';
    }
}

async function deletePromoAction(id) {
    showConfirmModal("Удаление акции", "Вы уверены, что хотите полностью удалить эту акцию? Данные будут стерты.", async () => {
        try {
            // Выполняем запрос. Глобальный fetch сам покажет ошибку, если статус не 2xx.
            await fetch(`/api/admin/promos/${id}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value
                }
            });

            // Этот блок сработает ТОЛЬКО при успешном удалении (status 200)
            showToast("Акция успешно удалена", "success");

            // Закрываем модалку, если функция существует
            if (typeof closeModal === 'function') closeModal('modal-order-view');

            setTimeout(() => {
                if (typeof loadPromosByPeriod === 'function') {
                    loadPromosByPeriod();
                } else {
                    location.reload();
                }
            }, 500);

        } catch (e) {
            // Блок пустой: уведомление об ошибке уже вывел глобальный fetch.
            // Мы просто пресекаем появление "Критическая ошибка связи".
            console.warn("Удаление акции отклонено или не удалось:", e.message);
        }
    });
}


async function openPromoDetails(id) {
    // Получаем список акций для поиска нужной
    const response = await fetch(`/api/admin/promos/filter?start=2000-01-01&end=2100-01-01`);
    const allPromos = await response.json();
    const promo = allPromos.find(p => p.id == id);

    if (!promo) return showToast("Акция не найдена", "error");

    // Сохраняем в глобальную переменную для функции печати и редактирования
    currentPromoData = promo;
    tempPromoItems = { ...promo.items };

    // 1. Заголовок модального окна
    document.getElementById('modal-title').innerHTML = `📢 Акция: ${promo.title} ${promo.confirmed ? '<span class="badge bg-success" style="margin-left:10px;">ПОДТВЕРЖДЕНО</span>' : ''}`;

    // 2. Инфо-блок (сетка 3 колонки)
    const info = document.getElementById('order-info');
    info.innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; background: #f8fafc; padding: 15px; border-radius: 10px; border: 1px solid #e2e8f0; margin-top:15px;">
            <div><small style="color: #64748b; font-weight: 700;">МЕНЕДЖЕР:</small><br><b>${promo.managerId}</b></div>
            <div><small style="color: #64748b; font-weight: 700;">ПЕРИОД:</small><br><b>${formatDate(promo.startDate)} — ${formatDate(promo.endDate)}</b></div>
            <div><small style="color: #64748b; font-weight: 700;">СТАТУС:</small><br><span class="badge ${promo.status === 'ACTIVE' ? 'bg-success' : 'bg-secondary'}">${promo.status}</span></div>
        </div>
    `;

    // 3. Отрисовка таблицы товаров (в режиме просмотра)
    renderPromoItemsTable(false);

 // Внутри функции openPromoDetails
 const footer = document.getElementById('order-footer-actions');
 let buttonsHtml = `<button class="btn-primary" style="background:#475569" onclick="printPromoAct(${promo.id})">🖨 Печать</button>`;

 if (!promo.confirmed) {
     buttonsHtml += `
         <button class="btn-primary" style="background:#10b981" onclick="confirmPromoAction(${promo.id})">✅ ПОДТВЕРДИТЬ</button>
         <button class="btn-primary" onclick="enablePromoEdit(${promo.id})">✏️ Изменить</button>
         <button class="btn-primary" style="background:#ef4444" onclick="deletePromoAction(${promo.id})">🗑 Удалить</button>
     `;
 } else {
     buttonsHtml += `<div style="color:#15803d; font-weight:700; padding: 0 10px;">✅ ПОДТВЕРЖДЕНО</div>`;
 }

 buttonsHtml += `<button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
 footer.innerHTML = buttonsHtml;


    openModal('modal-order-view');
}

async function confirmPromoAction(id) {
    showConfirmModal("Подтвердить акцию?", "После подтверждения редактирование будет невозможно!", async () => {
        const res = await fetch(`/api/admin/promos/${id}/confirm`, { method: 'POST' });
        if (res.ok) {
            showToast("Акция подтверждена!", "success");
            location.reload();
        }
    });
}


async function checkAndApplyPromos(orderItems, onApplied) {
    const productIds = Object.keys(orderItems).map(Number);

    // ПОЛУЧАЕМ ВЫБРАННОГО В МОДАЛКЕ МЕНЕДЖЕРА (1011, 1012 и т.д.)
    const selectedManagerId = document.getElementById('new-op-manager')?.value ||
                              document.getElementById('promo-manager')?.value;

    const overlay = document.getElementById('promo-checker-overlay');
    const container = document.getElementById('promo-list-container');

    try {
        const response = await fetch('/api/admin/promos/check-active-for-items', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value || ""
            },
            // ОТПРАВЛЯЕМ И ТОВАРЫ, И ID ВЫБРАННОГО МЕНЕДЖЕРА
            body: JSON.stringify({
                productIds: productIds,
                managerId: selectedManagerId
            })
        });

        const activePromos = await response.json();

        if (!activePromos || activePromos.length === 0) {
            return onApplied([]);
        }

        // Рендеринг карточек (без изменений)
        // Внутри container.innerHTML = activePromos.map(...)
        container.innerHTML = activePromos.map(p => `
    <div style="margin-bottom: 8px;">
        <input type="checkbox" class="promo-checkbox" id="p-${p.id}" data-id="${p.id}" checked style="display:none;">
        <label class="promo-card" for="p-${p.id}" style="display: flex; align-items: center; justify-content: space-between; padding: 10px; background: #f8fafc; border-radius: 12px; cursor: pointer; border: 1px solid #e2e8f0;">
            <div style="flex-grow: 1; pointer-events: none;">
                <div style="font-weight: 700; font-size: 13px;">${p.title}</div>
                <div style="font-size: 11px; color: #64748b;">Для менеджера: ${p.managerId}</div>
            </div>
            <!-- УБРАЛИ background и position из inline-style -->
            <div class="custom-switch-ui" style="width: 40px; height: 20px; border-radius: 20px; position: relative; transition: 0.3s;">
                <div class="switch-circle" style="width: 16px; height: 16px; background: white; border-radius: 50%; position: absolute; top: 2px; transition: 0.3s;"></div>
            </div>
        </label>
    </div>
`).join('');


        document.getElementById('promo-apply-btn').onclick = () => {
            const selectedIds = Array.from(document.querySelectorAll('.promo-checkbox:checked')).map(el => el.dataset.id);
            onApplied(activePromos.filter(p => selectedIds.includes(p.id.toString())));
            overlay.style.display = 'none';
        };

        document.getElementById('promo-skip-btn').onclick = () => {
            overlay.style.display = 'none';
            onApplied([]);
        };

        overlay.style.display = 'flex';
    } catch (e) {
        onApplied([]);
    }
}


function refreshPromoCounters() {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    let activeCount = 0;

    document.querySelectorAll('.promo-days-left').forEach(el => {
        const endDateStr = el.getAttribute('data-end');
        if (!endDateStr) return;

        const endDate = new Date(endDateStr);
        const diffTime = endDate - today;
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        if (diffDays > 0) {
            el.innerHTML = `<b>${diffDays} дн.</b>`;
            el.style.color = diffDays <= 3 ? "#ea580c" : "#1e293b";
            if (diffDays === 1) {
                el.innerHTML = `<span class="last-day-alert">ПОСЛЕДНИЙ ДЕНЬ</span>`;
            }
            activeCount++;
        } else if (diffDays === 0) {
            el.innerHTML = `<span class="last-day-alert">ЗАВЕРШАЕТСЯ</span>`;
            activeCount++;
        } else {
            el.innerText = "Завершено";
            el.style.color = "#94a3b8";
        }
    });

    const counterEl = document.getElementById('active-promos-count');
    if (counterEl) counterEl.innerText = activeCount;
}


async function submitPromo(isEdit = false, promoId = null) {
    const title = document.getElementById('promo-title').value.trim();
    const managerId = document.getElementById('promo-manager').value;
    const startDate = document.getElementById('promo-start').value;
    const endDate = document.getElementById('promo-end').value;

    if (!title || !startDate || !endDate) {
        return showToast("Заполните название и даты акции!", "error");
    }

    // Собираем товары и их проценты из таблицы
    const items = {};
    document.querySelectorAll('.promo-percent-input').forEach(input => {
        const pId = input.dataset.id;
        const percent = parseFloat(input.value);
        if (!isNaN(percent) && percent > 0) {
            items[pId] = percent;
        }
    });

    if (Object.keys(items).length === 0) {
        return showToast("Добавьте хотя бы один товар в акцию!", "error");
    }

    const data = {
        title: title,
        managerId: managerId,
        startDate: startDate,
        endDate: endDate,
        items: items
    };

    const url = isEdit ? `/api/admin/promos/${promoId}/edit` : '/api/admin/promos/create';
    const method = isEdit ? 'PUT' : 'POST';

    // Блокируем кнопку, чтобы избежать дублей при медленном интернете
    const saveBtn = event?.target;
    if (saveBtn && saveBtn.tagName === 'BUTTON') {
        saveBtn.disabled = true;
        saveBtn.innerHTML = "⏳ Сохранение...";
    }

    try {
        // 1. Вызываем fetch (наш глобальный перехватчик уже настроен)
        // Если прав нет (403), выполнение ПРЕРВЕТСЯ здесь и уйдет в catch
        await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        // 2. Этот код сработает ТОЛЬКО при успехе (status 200)
        showToast(isEdit ? "Акция обновлена" : "Акция успешно создана!", "success");

        if (typeof closeModal === 'function') closeModal('modal-order-view');

        setTimeout(() => {
            if (typeof loadPromosByPeriod === 'function') loadPromosByPeriod();
            else location.reload();
        }, 800);

    } catch (e) {
        // ВАЖНО: Блок catch оставляем ПУСТЫМ для уведомлений.
        // Тост "Доступ запрещен" или текст ошибки сервера уже вывел глобальный fetch.
        console.warn("Операция с акцией отклонена:", e.message);

        // Разблокируем кнопку для исправления данных
        if (saveBtn && saveBtn.tagName === 'BUTTON') {
            saveBtn.disabled = false;
            saveBtn.innerHTML = isEdit ? "ОБНОВИТЬ" : "СОХРАНИТЬ";
        }
    }
}


function enablePromoEdit(id) {
    if (!currentPromoData) return;

    const today = new Date().toISOString().split('T')[0];
    const managerOptions = managerIdList.map(m =>
        `<option value="${m}" ${m === currentPromoData.managerId ? 'selected' : ''}>${m}</option>`
    ).join('');

    document.getElementById('modal-title').innerText = "✏️ РЕДАКТИРОВАНИЕ АКЦИИ #" + id;

    // Перерисовываем инфо-блок в инпуты
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 15px; background: #f1f5f9; padding: 15px; border-radius: 10px; border: 1px solid #cbd5e1;">
            <div style="grid-column: span 2;">
                <label>ИМЯ АКЦИИ:</label>
                <input type="text" id="promo-title" class="form-control" value="${currentPromoData.title}">
            </div>
            <div><label>МЕНЕДЖЕР:</label><select id="promo-manager" class="form-select">${managerOptions}</select></div>
            <div style="display:flex; gap:10px;">
                <div style="flex:1"><label>ОТ:</label><input type="date" id="promo-start" class="form-control" value="${currentPromoData.startDate}"></div>
                <div style="flex:1"><label>ДО:</label><input type="date" id="promo-end" class="form-control" value="${currentPromoData.endDate}"></div>
            </div>
        </div>
    `;

    renderPromoItemsTable(true); // Включаем режим добавления товаров

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="submitPromo(true, ${id})">СОХРАНИТЬ ИЗМЕНЕНИЯ</button>
        <button class="btn-primary" style="background:#64748b" onclick="openPromoDetails(${id})">ОТМЕНА</button>
    `;
}

function renderPromoItemsTable(isEdit) {
    const body = document.getElementById('order-items-body');
    if (!body) return;

    let html = '';

    // Отрисовка добавленных товаров
    Object.entries(tempPromoItems).forEach(([pId, percent]) => {
        const p = productsData.find(prod => prod.id == pId);
        if (!p) return;

        html += `
            <tr id="promo-row-${pId}">
                <td style="padding-left: 15px;">
                    <div style="font-weight:600;">${p.name}</div>
                    ${isEdit ? `<small style="color:red; cursor:pointer;" onclick="deletePromoItem(${pId})">удалить</small>` : ''}
                </td>
                <td style="text-align:center;"><b>${p.stockQuantity || 0}</b> шт</td>
                <td style="text-align:center;">${(p.price || 0).toLocaleString()} ֏</td>
                <td style="text-align:center;">${p.expiryDate ? formatDate(p.expiryDate) : '---'}</td>
                <td>
                    <div style="display:flex; align-items:center; gap:5px;">
                        <input type="number"
                               value="${percent}"
                               class="form-control promo-percent-input"
                               style="width:70px; border:2px solid #f59e0b; font-weight:bold; text-align:center;"
                               data-id="${pId}"
                               onchange="updatePromoPercent(${pId}, this.value)"
                               ${!isEdit ? 'disabled' : ''}>
                        <span style="font-weight:bold; color:#f59e0b;">%</span>
                    </div>
                </td>
            </tr>`;
    });

    // Строка добавления нового товара (только в режиме редактирования/создания)
    if (isEdit) {
        const options = productsData
            .filter(p => !tempPromoItems[p.id]) // Не показываем уже добавленные
            .map(p => `<option value="${p.id}">${p.name} (Доступно: ${p.stockQuantity})</option>`)
            .join('');

        html += `
            <tr style="background: #fff7ed; border-top: 2px solid #fb923c;">
                <td colspan="4" style="padding: 10px;">
                    <select id="add-promo-p-id" class="form-select" style="font-size:13px;">
                        <option value="">-- Выберите товар для акции --</option>
                        ${options}
                    </select>
                </td>
                <td style="padding: 10px;">
                    <button class="btn-primary"
                            style="background:#f59e0b; width:100%;"
                            onclick="addPromoItemRow()">
                        Добавить
                    </button>
                </td>
            </tr>`;
    }

    body.innerHTML = html;
}

function updatePromoPercent(pId, value) {
    let val = parseFloat(value);
    if (isNaN(val) || val < 0) val = 0;
    if (val > 100) val = 100;
    tempPromoItems[pId] = val;
}

function addPromoItemRow() {
    const select = document.getElementById('add-promo-p-id');
    const pId = select.value;

    if (!pId) return showToast("Выберите товар", "error");

    // Добавляем в список с дефолтным процентом (например 10%)
    tempPromoItems[pId] = 10;

    // Перерисовываем таблицу
    renderPromoItemsTable(true);
    showToast("Товар добавлен в список акции", "success");
}

function deletePromoItem(pId) {
    delete tempPromoItems[pId];
    renderPromoItemsTable(true);
}

function printPromoAct(promoId) {
    const promo = currentPromoData;
    if (!promo || promo.id != promoId) return showToast("Ошибка данных для печати", "error");

    const printWindow = window.open('', '_blank', 'width=800,height=600');

    // Настройки формата для печати (1 знак после запятой)
    const f = { minimumFractionDigits: 1, maximumFractionDigits: 1 };

    let itemsHtml = '';
    let index = 1;

    Object.entries(promo.items).forEach(([pId, percent]) => {
        const p = productsData.find(prod => prod.id == pId);
        if (!p) return;

        // --- ИСПРАВЛЕННЫЙ РАСЧЕТ (0.1 точность) ---
        const discountModifier = 1 - (percent / 100);

        // Цена по акции с точностью до 0.1
        const promoPrice = parseFloat((p.price * discountModifier).toFixed(1));

        itemsHtml += `
            <tr>
                <td>${index++}</td>
                <td>${p.name}</td>
                <td style="text-align:center;">${p.stockQuantity}</td>
                <!-- ИСПРАВЛЕНО: Базовая цена с одним знаком -->
                <td style="text-align:center;">${p.price.toLocaleString(undefined, f)} ֏</td>
                <!-- ИСПРАВЛЕНО: Акционная цена с одним знаком -->
                <td style="text-align:center; font-weight:bold; color:#ea580c;">${promoPrice.toLocaleString(undefined, f)} ֏</td>
                <td style="text-align:center;">${percent}%</td>
            </tr>`;
    });

    const content = `
        <html>
        <head>
            <title>Акт по акции - ${promo.title}</title>
            <style>
                body { font-family: 'DejaVu Sans', sans-serif; padding: 20px; color: #333; }
                .header { text-align: center; border-bottom: 2px solid #000; padding-bottom: 10px; margin-bottom: 20px; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                th, td { border: 1px solid #000; padding: 8px; font-size: 13px; }
                th { background: #f2f2f2; }
                .footer { margin-top: 40px; display: flex; justify-content: space-between; }
                .stamp { width: 150px; height: 80px; border: 1px dashed #ccc; text-align: center; line-height: 80px; color: #ccc; }
            </style>
        </head>
        <body>
            <div class="header">
                <h2>АКТ О ПРОВЕДЕНИИ МАРКЕТИНГОВОЙ АКЦИИ</h2>
                <p><b>Название:</b> ${promo.title} | <b>ID:</b> ${promo.id}</p>
                <p><b>Период:</b> ${formatDate(promo.startDate)} — ${formatDate(promo.endDate)}</p>
                <p><b>Ответственный менеджер:</b> ${promo.managerId}</p>
            </div>

            <table>
                <thead>
                    <tr>
                        <th>№</th>
                        <th>Наименование товара</th>
                        <th>Остаток</th>
                        <th>Базовая цена</th>
                        <th>Цена по акции</th>
                        <th>Скидка</th>
                    </tr>
                </thead>
                <tbody>
                    ${itemsHtml}
                </tbody>
            </table>

            <div class="footer">
                <div>
                    <p>Директор: _________________</p>
                    <p>Менеджер: <b>${promo.managerId}</b></p>
                </div>
                <div class="stamp">М.П.</div>
            </div>

            <script>window.onload = function() { window.print(); window.close(); }</script>
        </body>
        </html>
    `;

    printWindow.document.write(content);
    printWindow.document.close();
}

function saveOrderWithPromos(selectedPromos, baseData, submitFunction) {
    const promoMap = {};

    // Наполняем карту: ID товара -> Процент из акции
    selectedPromos.forEach(promo => {
        Object.entries(promo.items).forEach(([pId, percent]) => {
            // Если этот товар есть в нашем текущем заказе
            if (baseData.items[pId]) {
                promoMap[pId] = percent;
            }
        });
    });

    // Добавляем акционные данные в объект для сервера
    baseData.appliedPromoItems = promoMap;

    // Вызываем финальную отправку (которую мы уже прописали в saveFullChanges)
    submitFunction(baseData);
}

function updateRowBasePrice(selectEl, rowId) {
    const selectedOption = selectEl.options[selectEl.selectedIndex];
    const price = selectedOption.dataset.price || 0;
    const row = document.getElementById(`row-${rowId}`);

    // Обновляем базовую цену в дата-атрибуте для расчетов
    row.dataset.basePrice = price;

    // Запускаем ваш общий пересчет
    recalculateAllPricesByPercent();
}

function addItemRowToOrder() {
    const body = document.getElementById('manual-order-items-body');
    if (!body) return console.error("Таблица создания заказа не найдена");

    const rowId = Date.now();
    const row = document.createElement('tr');
    row.id = `row-${rowId}`;
    row.dataset.basePrice = "0"; // Здесь будет храниться цена без скидки

    const options = (window.productsData || []).map(p =>
        `<option value="${p.id}" data-price="${p.price}">${p.name}</option>`
    ).join('');

    row.innerHTML = `
        <td>
            <select class="form-select product-select" style="font-size: 12px;"
                    onchange="const opt=this.options[this.selectedIndex]; this.closest('tr').dataset.basePrice=opt.dataset.price || 0; recalculateAllPricesByPercent();">
                <option value="">-- Выбрать --</option>
                ${options}
            </select>
        </td>
        <td>
            <input type="number" class="form-control qty-input-active" value="1" min="1"
                   oninput="recalculateAllPricesByPercent()">
        </td>
        <td class="item-price-cell">0 ֏</td>
        <td class="item-subtotal-cell">0 ֏</td>
        <td style="text-align: center;">
            <button type="button" class="btn-primary" style="background:#fee2e2; color:#ef4444; border:none; padding: 2px 8px;"
                    onclick="this.closest('tr').remove(); recalculateAllPricesByPercent();">✕</button>
        </td>
    `;
    body.appendChild(row);
}


function updateRowPrice(selectEl, rowId) {
    const selected = selectEl.options[selectEl.selectedIndex];
    const price = selected.dataset.price || 0;
    const row = document.getElementById(`row-${rowId}`);
    row.dataset.basePrice = price;
    recalculateAllPricesByPercent();
}

function recalculateAllPricesByPercent() {
    const percentInput = document.getElementById('new-op-percent') || document.getElementById('order-discount-percent');
    let percent = percentInput ? parseFloat(percentInput.value) : 0;
    if (isNaN(percent)) percent = 0;

    // Настройки формата: всегда 1 знак после запятой для вывода текста
    const f = { minimumFractionDigits: 1, maximumFractionDigits: 1 };

    // Определяем активную таблицу (создание или редактирование)
    const modalCreate = document.getElementById('modal-order-create');
    const isCreateModal = modalCreate && modalCreate.style.display === 'block';

    const bodyId = isCreateModal ? 'manual-order-items-body' : 'order-items-body';
    const totalId = isCreateModal ? 'manual-order-total-price' : 'order-total-price';

    let totalOrderSum = 0;
    const rows = document.querySelectorAll(`#${bodyId} tr`);

    rows.forEach(row => {
        const basePrice = parseFloat(row.dataset.basePrice) || 0;
        const qtyInput = row.querySelector('.qty-input-active');
        const qty = qtyInput ? parseInt(qtyInput.value) || 0 : 0;

        // --- ПРАВИЛЬНЫЙ РАСЧЕТ (HALF_UP) ---
        // 1. Цена со скидкой за 1 единицу
        const modifiedPrice = roundHalfUp(basePrice * (1 - percent / 100));

        // 2. Сумма строки (Цена * Кол-во)
        const rowSum = roundHalfUp(modifiedPrice * qty);

        const priceCell = row.querySelector('.item-price-cell');
        const subtotalCell = row.querySelector('.item-subtotal-cell') || row.querySelector(`[id^="total-row-"]`);

        // Вывод в интерфейс с разделителями тысяч и одним знаком
        if (priceCell) {
            priceCell.innerText = modifiedPrice.toLocaleString(undefined, f) + " ֏";
        }
        if (subtotalCell) {
            subtotalCell.innerText = rowSum.toLocaleString(undefined, f) + " ֏";
        }

        totalOrderSum += rowSum;
    });

    // Округляем финальную сумму заказа
    totalOrderSum = roundHalfUp(totalOrderSum);

    const totalEl = document.getElementById(totalId);
    if (totalEl) {
        totalEl.innerHTML = `<span style="font-size: 14px; color: #64748b; font-weight: normal;">Итого:</span> ${totalOrderSum.toLocaleString(undefined, f)} ֏`;
    }

    // Синхронизируем с глобальной переменной
    window.currentOrderTotal = totalOrderSum;
}

function handleLogout() {
    showConfirmModal('Подтвердите выход', 'Вы уверены, что хотите покинуть систему?', () => {
        // Очищаем локальное состояние в браузере
        localStorage.clear();
        sessionStorage.clear();

        // Отправляем форму
        document.getElementById('logout-form').submit();
    });
}

async function handleCreateInvoice(orderId) {
    try {
        const response = await fetch(`/admin/invoices/create-from-order/${orderId}`, {
            method: 'POST',
            headers: window.apiHeaders
        });

        // 1. Читаем ответ сервера
        const result = await response.json().catch(() => ({}));

        // 2. КРИТИЧЕСКАЯ ПРОВЕРКА: Если в ответе есть ошибка (её поймал наш перехватчик)
        // Мы просто выходим из функции и НЕ показываем зеленый тост.
        if (result.error) {
            console.log("Действие отменено перехватчиком ошибок.");
            return;
        }

        // 3. Только если ошибок НЕТ, показываем успех
        if (response.ok) {
            showToast("Счет успешно выставлен", "success");
            setTimeout(() => location.reload(), 1000);
        }

    } catch (e) {
        console.error("Ошибка при создании счета:", e);
        // Ошибка сети будет обработана перехватчиком автоматически
    }
}



// 1. Обработка клика по вкладкам
document.querySelectorAll('.tab-link, [data-tab]').forEach(tab => {
    tab.addEventListener('click', function() {
        // Учитываем разные варианты атрибутов (href или data-tab)
        const targetId = this.getAttribute('href')?.replace('#', '') || this.getAttribute('data-tab');

        if (targetId === 'tab-promos') {
            // Небольшая задержка, чтобы браузер успел отрисовать вкладку
            setTimeout(() => {
                if (typeof loadPromosByPeriod === 'function') {
                    loadPromosByPeriod();
                }
            }, 150);
        }
    });
});

// 2. Автозагрузка при старте страницы (если вкладка Акции открыта по умолчанию)
window.addEventListener('DOMContentLoaded', () => {
    // 1. Проверяем вкладку через localStorage (так как вы сохраняете её там при клике)
    const currentTab = localStorage.getItem('sellion_tab');

    // 2. Проверяем также физическое наличие класса active или хеша в URL
    const promoTab = document.getElementById('tab-promos');
    const isActive = (currentTab === 'tab-promos') ||
        promoTab?.classList.contains('active') ||
        window.location.hash === '#tab-promos';

    if (isActive) {
        // Увеличим задержку до 500мс, чтобы Thymeleaf (th:value) точно успел проставить даты
        setTimeout(() => {
            loadPromosByPeriod();
        }, 500);
    }
});



document.addEventListener("DOMContentLoaded", async () => {

    const originalFetch = window.fetch;
    window.fetch = async (...args) => {
        try {
            const response = await originalFetch(...args);

            if (!response.ok) {
                let errorMessage = "Произошла ошибка системы";

                let serverMessage = null;
                try {
                    const contentType = response.headers.get("content-type");
                    if (contentType && contentType.includes("application/json")) {
                        const data = await response.clone().json();
                        serverMessage = data.error || data.message;
                    }
                } catch (e) {
                    console.warn("Не удалось распарсить JSON ошибки");
                }

                if (serverMessage) {
                    errorMessage = serverMessage;
                } else {
                    switch (response.status) {
                        case 400: errorMessage = "Некорректный запрос. Проверьте данные"; break;
                        case 403: errorMessage = "Доступ запрещен: Недостаточно прав"; break;
                        case 404: errorMessage = "Запрошенный ресурс не найден"; break;
                        case 408: errorMessage = "Время ожидания истекло"; break;
                        case 500: errorMessage = "Критическая ошибка сервера"; break;
                        case 503: errorMessage = "Сервис временно недоступен"; break;
                    }
                }

                if (typeof showToast === 'function') {
                    showToast(errorMessage, "error");
                }

                // Отклоняем промис, чтобы код в основном скрипте остановился
                return Promise.reject(new Error(errorMessage));
            }
            return response;
        } catch (error) {
            const isKnownError = error.message && (
                error.message.includes("Доступ") ||
                error.message.includes("удален") ||
                error.message.includes("найден") ||
                error.message.includes("ошибка сервера")
            );

            if (!isKnownError) {
                if (typeof showToast === 'function') showToast("Ошибка сети или сервера", "error");
            }
            return Promise.reject(error);
        }
    };



    const setDefaultInvoiceDates = () => {
        const startInput = document.getElementById('inv-date-start');
        const endInput = document.getElementById('inv-date-end');
        if (startInput && startInput.value) return;

        const now = new Date();
        const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
        const today = now.toISOString().split('T')[0];

        if (startInput) startInput.value = firstDay;
        if (endInput) endInput.value = today;
    };


    const token = document.querySelector('input[name="_csrf"]')?.value;
    window.apiHeaders = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    };
    if (token) window.apiHeaders['X-CSRF-TOKEN'] = token;

    if (typeof connectWebSocket === 'function') connectWebSocket();

    const initData = async () => {
        try {
            setDefaultInvoiceDates();
            const promises = [];
            if (typeof loadManagerIds === 'function') promises.push(loadManagerIds());
            if (typeof loadApiKeys === 'function') promises.push(loadApiKeys());
            await Promise.all(promises);
            if (typeof initDeliveryDateLogic === 'function') initDeliveryDateLogic();
        } catch (e) {
            console.error("Ошибка загрузки начальных данных:", e);
        }
    };
    initData();


    const isFirstLoadInSession = !sessionStorage.getItem('sellion_session_active');
    let lastTab = localStorage.getItem('sellion_tab') || 'tab-orders';

    if (isFirstLoadInSession) {
        lastTab = 'tab-orders';
        localStorage.setItem('sellion_tab', 'tab-orders');
        sessionStorage.setItem('sellion_session_active', 'true');
    }

    if (typeof showTab === 'function') showTab(lastTab);

    const runFormatting = () => {
        document.querySelectorAll('.js-date-format').forEach(el => {
            const val = el.innerText.trim();
            if (val && val !== '---' && val !== '') {
                if (typeof formatDate === 'function') el.innerText = formatDate(val);
            }
        });

        document.querySelectorAll('.js-status-translate').forEach(el => {
            if (!el || el.children.length > 0) return;
            const rawStatus = el.innerText.trim();
            if (rawStatus && typeof translateReturnStatus === 'function') {
                const statusInfo = translateReturnStatus(rawStatus);
                if (statusInfo) {
                    el.innerHTML = `<span class="badge ${statusInfo.class || 'bg-secondary'}">${statusInfo.text}</span>`;
                }
            }
        });

        if (typeof refreshReportCounters === 'function') refreshReportCounters();
        if (typeof refreshPromoCounters === 'function') refreshPromoCounters();
    };

    runFormatting();

    document.body.addEventListener('click', function (e) {
        const navLink = e.target.closest('.nav-link');
        if (navLink) {
            const tabId = navLink.id.replace('btn-', 'tab-');
            localStorage.setItem('sellion_tab', tabId);

            if (typeof showTab === 'function') showTab(tabId);

            if (tabId === 'tab-promos') {
                setTimeout(() => {
                    loadPromosByPeriod();
                }, 50);
            }

            requestAnimationFrame(() => {
                setTimeout(runFormatting, 100);
            });
        }


        const categoryHeader = e.target.closest('.js-category-toggle');
        if (categoryHeader) {
            const targetClass = categoryHeader.getAttribute('data-target');
            const rows = document.querySelectorAll(`.${targetClass}`);
            const icon = categoryHeader.querySelector('.toggle-icon');
            const firstRow = rows[0];
            const isCurrentlyHidden = firstRow ? (firstRow.style.display === 'none') : false;

            rows.forEach(row => {
                row.style.display = isCurrentlyHidden ? 'table-row' : 'none';
            });

            if (icon) {
                icon.style.transform = isCurrentlyHidden ? "rotate(0deg)" : "rotate(-90deg)";
                icon.innerText = isCurrentlyHidden ? "▼" : "▶";
            }
        }
    });

});

