if (typeof productsData === 'undefined') window.productsData = [];
if (typeof clientsData === 'undefined') window.clientsData = [];
if (typeof ordersData === 'undefined') window.ordersData = [];
if (typeof returnsData === 'undefined') window.returnsData = [];


let tempItems = {};
let managerIdList = [];

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


function getManagerOptionsHTML() {
    // Если список еще не загружен, добавляем хотя бы текущего пользователя или OFFICE
    if (!managerIdList || managerIdList.length === 0) {
        return `<option value="OFFICE">OFFICE (загрузка...)</option>`;
    }
    return managerIdList.map(m => `<option value="${m}">${m}</option>`).join('');
}


// Чистая функция для подготовки товаров (всегда возвращает {ID: Qty})
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
    showConfirmModal("Подтвердить возврат?", "Сумма будет вычтена из долга клиента.", async () => {
        const response = await fetch(`/api/admin/returns/${id}/confirm`, {method: 'POST'});
        if (response.ok) {
            showToast("Возврат подтвержден!", "success");
            location.reload();
        }
    });
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


// 2. Полная карточка клиента (все поля)
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

        <!-- РЯД 3 -->
        <div style="${rowStyle}">
            <div><small>Название банка:</small><br><b>${client.bankName || '---'}</b></div>
            <div><small>Расчетный счет:</small><br><b>${client.bankAccount || '---'}</b></div>
            <div><small>Телефон:</small><br><b>${client.phone || '---'}</b></div>
            <div></div> <!-- Пустое место -->
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

        <!-- Ряд 3: Название банка, Расчетный счет, Телефон -->
        <div style="${rowStyle}">
            <div><label>Название банка</label><input type="text" id="edit-client-bank-name" value="${client.bankName || ''}"></div>
            <div><label>Расчетный счет</label><input type="text" id="edit-client-bank" value="${client.bankAccount || ''}"></div>
            <div><label>Телефон</label><input type="text" id="edit-client-phone" value="${client.phone || ''}"></div>
            <div></div> <!-- Пусто для выравнивания -->
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


// Функция отправки нового товара на сервер
// async function submitCreateProduct() {
//     const data = {
//         name: document.getElementById('new-p-name').value,
//         price: parseFloat(document.getElementById('new-p-price').value) || 0,
//         stockQuantity: parseInt(document.getElementById('new-p-qty').value) || 0,
//         itemsPerBox: parseInt(document.getElementById('new-p-box').value) || 1,
//         barcode: document.getElementById('new-p-code').value,
//         category: document.getElementById('new-p-cat').value
//     };
//
//     if (!data.name) {
//         showToast("Введите название товара!");
//         return;
//     }
//
//     try {
//         const response = await fetch('/api/admin/products/create', {
//             method: 'POST',
//             headers: {'Content-Type': 'application/json'},
//             body: JSON.stringify(data)
//         });
//         if (response.ok) {
//             location.reload(); // Обновляем страницу после создания
//         } else {
//             showToast("Ошибка при сохранении товара");
//         }
//     } catch (e) {
//         console.error(e);
//         showToast("Ошибка сети");
//     }
// }



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
    // Сначала открываем окно
    openModal('modal-client');

    // Находим select
    const select = document.getElementById('new-client-manager-id');
    if (!select) {
        console.error("Критическая ошибка: Select #new-client-manager-id не найден!");
        return;
    }

    // Если список пуст, ждем загрузки
    if (!window.managerIdList || window.managerIdList.length === 0) {
        select.innerHTML = '<option value="">⏳ Загрузка...</option>';
        await loadManagerIds();
    }

    // Финальное заполнение
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



 // Глобальный массив для хранения списка из Enum

// Функция для загрузки списка менеджеров с сервера (асинхронно)
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
    await loadManagerIds();
    tempItems = {};
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
        <div class="modal-info-row" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-top:10px; background: #f8fafc; padding: 15px; border-radius: 10px;">
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
            <div><label>КОММЕНТАРИЙ:</label><input type="text" id="new-op-comment" class="form-control" placeholder="..."></div>
        </div>`;

    initSmartClientSearch('new-op-shop', 'clients-datalist'); // Активируем живой поиск
    renderItemsTable(tempItems, true);
    document.getElementById('order-total-price').innerText = "Итого: 0 ֏";
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveNewManualOperation('order')">Создать заказ</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Отмена</button>
    `;
    openModal('modal-order-view');
}





async function openCreateReturnModal() {
    await loadManagerIds();
    tempItems = {};
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

    initSmartClientSearch('new-op-shop', 'returns-clients-datalist'); // Активируем живой поиск
    renderItemsTable(tempItems, true);

    const totalEl = document.getElementById('order-total-price');
    if (totalEl) { totalEl.innerText = "Итого: 0 ֏"; }

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#ef4444" onclick="saveNewManualOperation('return')">Создать возврат</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Отмена</button>
    `;
    openModal('modal-order-view');
}



function initSmartClientSearch(inputId, datalistId) {
    const input = document.getElementById(inputId);
    const datalist = document.getElementById(datalistId);
    let validClients = []; // Здесь будем хранить имена для проверки

    const updateSearch = async () => {
        const query = input.value.trim();
        try {
            const response = await fetch(`/api/clients/search-fast?keyword=${encodeURIComponent(query)}`);
            const clients = await response.json();

            validClients = clients.map(c => c.name); // Сохраняем список имен
            datalist.innerHTML = clients.map(c => `<option value="${c.name}">`).join('');
        } catch (err) { console.error("Ошибка:", err); }
    };

    input.addEventListener('input', updateSearch);
    input.addEventListener('focus', updateSearch);

    // ПРОВЕРКА НА ОШИБКУ: когда оператор закончил ввод
    input.addEventListener('blur', () => {
        const val = input.value.trim();
        if (val === "") return;

        // Если введенного текста нет в списке валидных имен
        if (!validClients.includes(val)) {
            showToast("Ошибка: Выберите магазин из предложенного списка!", "error");
            input.value = ""; // Очищаем поле, так как значение неверное
            input.style.border = "2px solid red";
        } else {
            input.style.border = ""; // Всё ок
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
    const body = document.getElementById('order-items-body');
    const footer = document.getElementById('order-footer-actions');
    const title = document.getElementById('modal-title');
    const totalEl = document.getElementById('order-total-price');

    try {
        // Визуальная индикация загрузки
        body.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:20px;">⌛ Загрузка истории...</td></tr>';

        const response = await fetch(`/api/admin/audit/order/${orderId}`);

        if (!response.ok) {
            const errorText = await response.text();
            showToast(`Ошибка: ${response.status}`, "error");
            body.innerHTML = `<tr><td colspan="5" style="color:red; text-align:center; padding:20px;">Не удалось загрузить данные</td></tr>`;
            return;
        }

        const logs = await response.json();

        // Меняем заголовок модального окна
        title.innerHTML = `📜 ИСТОРИЯ ИЗМЕНЕНИЙ #${orderId}`;

        if (logs.length === 0) {
            body.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:40px; color:#94a3b8;">История изменений для этой операции пуста</td></tr>';
        } else {
            // Формируем строки таблицы истории
            body.innerHTML = logs.map(log => `
                <tr style="border-bottom: 1px solid #f1f5f9;">
                    <td style="white-space: nowrap; color: #64748b; font-size: 12px;">
                        ${formatDate(log.timestamp)}
                    </td>
                    <td>
                        <span class="badge" style="background:#e0f2fe; color:#0369a1;">${log.username}</span>
                    </td>
                    <td style="font-weight: 600; color: #1e293b;">
                        ${log.action}
                    </td>
                    <td colspan="2" style="font-size: 13px; color: #475569; font-style: italic;">
                        ${log.details || '---'}
                    </td>
                </tr>
            `).join('');
        }

        // Скрываем общую сумму, так как мы смотрим логи
        if (totalEl) totalEl.style.display = 'none';

        // Обновляем футер (только кнопка Назад)
        footer.innerHTML = `
            <button class="btn-primary" style="background:#64748b; width: 100%; padding: 10px;" onclick="openOrderDetails(${orderId})">
                🔙 ВЕРНУТЬСЯ К ДЕТАЛЯМ
            </button>
        `;

    } catch (e) {
        console.error("Audit load error:", e);
        showToast("Критическая ошибка сети", "error");
        body.innerHTML = '<tr><td colspan="5" style="text-align:center; color:red;">Нет связи с сервером</td></tr>';
    }
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

    // 1. ПОЛНАЯ ОЧИСТКА: Сбрасываем фрейм и удаляем старые обработчики,
    // чтобы они не копились и не срабатывали по несколько раз.
    frame.onload = null;
    frame.src = "about:blank";

    showToast("⏳ Подготовка документа...", "info");

    // 2. ЗАДЕРЖКА: Небольшой таймаут гарантирует, что браузер
    // успеет "забыть" предыдущую задачу печати.
    setTimeout(() => {
        frame.src = url;

        frame.onload = function() {
            // Пропускаем пустую загрузку
            if (frame.src.includes("about:blank")) return;

            // 3. ФИНАЛЬНЫЙ РЕНДЕРИНГ: Даем время на подгрузку стилей и картинок
            setTimeout(() => {
                try {
                    frame.contentWindow.focus();
                    frame.contentWindow.print();

                    // Очищаем событие после успешного вызова,
                    // чтобы оно не висело в памяти.
                    frame.onload = null;
                } catch (e) {
                    console.error("Ошибка печати:", e);
                    // Если заблокировано (например, popup-blocker), открываем в новом окне
                    // window.open(url, '_blank');
                }
            }, 500);
        };
    }, 100);
};

// Привязываем функции к глобальному объекту ОДИН РАЗ
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


// function toggleCategory(categoryClass) {
//     // Находим все строки, у которых есть этот класс
//     const rows = document.getElementsByClassName(categoryClass);
//     const header = document.querySelector(`[data-target="${categoryClass}"]`);
//     const icon = header.querySelector('.toggle-icon');
//
//     if (rows.length === 0) return;
//
//     // Определяем текущее состояние по первой строке
//     const isHidden = rows[0].style.display === "none";
//
//     for (let i = 0; i < rows.length; i++) {
//         rows[i].style.display = isHidden ? "table-row" : "none";
//     }
//
//     // Вращаем иконку
//     if (icon) {
//         icon.style.transform = isHidden ? "rotate(0deg)" : "rotate(-90deg)";
//     }
// }

// function toggleCategory(targetId) {
//     // Находим все строки товаров данной категории по классу
//     const rows = document.querySelectorAll('.' + targetId);
//     // Находим строку-заголовок, по которой кликнули
//     const header = document.querySelector(`[data-target="${targetId}"]`);
//     const icon = header.querySelector('.toggle-icon');
//
//     if (rows.length === 0) return;
//
//     // Проверяем текущее состояние (скрыто или нет) по первой строке
//     const isHidden = rows[0].style.display === "none";
//
//     rows.forEach(row => {
//         // Переключаем: если было скрыто — показываем (table-row), иначе скрываем
//         row.style.display = isHidden ? "table-row" : "none";
//     });
//
//     // Анимация стрелочки
//     if (icon) {
//         if (isHidden) {
//             icon.style.transform = "rotate(0deg)";
//             icon.innerText = "▼";
//         } else {
//             icon.style.transform = "rotate(-90deg)";
//             icon.innerText = "▶";
//         }
//     }
// }



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
            showToast("Склад скорректирован", "success");
            location.reload();
        } else {
            const error = await response.json();
            showToast(error.message || "Ошибка при сохранении", "error");
        }
    } catch (e) {
        showToast("Ошибка сети", "error");
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
    const start = document.getElementById('report-start').value;
    const end = document.getElementById('report-end').value;
    const email = document.getElementById('report-email').value;

    if (!start || !end || !email) {
        showToast("Выберите период и введите email!", "error");
        return;
    }

    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
    const csrfHeader = "X-CSRF-TOKEN";

    const types = [];
    if (document.getElementById('check-orders').checked) types.push('orders');
    if (document.getElementById('check-returns').checked) types.push('returns');

    const params = new URLSearchParams();
    params.append('start', start);
    params.append('end', end);
    params.append('email', email);
    types.forEach(type => params.append('types', type));

    showToast(`⏳ Отправка отчета на ${email}...`, "info");

    const url = '/api/reports/excel/send-to-accountant';

    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            [csrfHeader]: csrfToken
        },
        body: params
    })
        .then(async response => {
            // Проверяем, не пришел ли HTML вместо JSON (ошибка авторизации)
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("text/html")) {
                throw new Error("Ошибка доступа (403/401). Перезагрузите страницу.");
            }

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `Ошибка сервера ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            showToast(`${data.message || 'Отчет успешно отправлен!'}`, 'success');
        })
        .catch(error => {
            console.error('Email error:', error);
            showToast( error.message, 'error');
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
                showToast("Ошибка: " + (data.error || "Не удалось отправить"), "danger");
            }
        })
        .catch(err => {
            console.error('Error:', err);
            showToast("Ошибка соединения с сервером", "danger");
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
    const managerId = document.getElementById('route-manager-select').value;
    const date = document.getElementById('route-date-select').value;
    if (!date) return showToast("Выберите дату", "error");

    // Вызываем компактную печать заказов
    const url = `/admin/logistic/print-compact?managerId=${encodeURIComponent(managerId)}&date=${date}&type=order`;
    printAction(url);
}

function printCompactReturns() {
    const managerId = document.getElementById('route-manager-select').value;
    const date = document.getElementById('route-date-select').value;
    if (!date) return showToast("Выберите дату", "error");

    const url = `/admin/logistic/print-compact?managerId=${encodeURIComponent(managerId)}&date=${date}&type=return`;
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
                showToast("Ошибка сохранения", "danger");
            }
        });
}


function filterInvoices() {
    const manager = document.getElementById('filter-invoice-manager').value.toLowerCase();
    const status = document.getElementById('filter-invoice-status').value.toLowerCase();
    const rows = document.querySelectorAll('#invoices-table-body tr');

    rows.forEach(row => {
        // Предполагаем, что менеджер есть в данных строки (добавим это в HTML ниже)
        const rowManager = row.getAttribute('data-manager')?.toLowerCase() || "";
        const rowStatus = row.querySelector('.badge').innerText.toLowerCase();

        const matchManager = manager === "" || rowManager === manager;
        const matchStatus = status === "" || rowStatus === status;

        row.style.display = (matchManager && matchStatus) ? "" : "none";
    });
}



function printManagerDebts() {
    const managerId = document.getElementById('filter-invoice-manager').value;
    if (!managerId) {
        showToast("Сначала выберите менеджера из списка!", "info");
        return;
    }

    // Формируем URL согласно вашему Java-контроллеру
    const url = `/admin/invoices/print-debts?managerId=${encodeURIComponent(managerId)}`;

    // Используем вашу универсальную функцию тихой печати
    // Она загрузит страницу во фрейм 'printFrame' и вызовет window.print()
    printAction(url);
}


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


async function saveNewManualOperation(type) {
    const shopInput = document.getElementById('new-op-shop');
    const shopName = shopInput.value.trim();
    const dateVal = document.getElementById('new-op-date').value;

    // 1. Первичная проверка заполнения
    if (!shopName || !dateVal) {
        return showToast("Заполните магазин и дату!", "error");
    }

    // 2. ВАЛИДАЦИЯ МАГАЗИНА (для работы с 5000+ записей)
    // Проверяем, существует ли введенный магазин в БД перед созданием заказа
    try {
        const checkRes = await fetch(`/api/clients/search-fast?keyword=${encodeURIComponent(shopName)}`);
        const clients = await checkRes.json();
        // Ищем точное совпадение (без учета регистра)
        const exists = clients.some(c => c.name.toLowerCase() === shopName.toLowerCase());

        if (!exists) {
            shopInput.style.border = "2px solid #ef4444";
            return showToast(`Магазин "${shopName}" не найден! Выберите из списка.`, "danger");
        }
        shopInput.style.border = ""; // Сбрасываем рамку, если всё ок
    } catch (e) {
        console.error("Ошибка проверки клиента:", e);
    }

    // 3. Сбор товаров из таблицы
    document.querySelectorAll('.qty-input-active').forEach(input => {
        const pId = input.id.replace('input-qty-', '');
        const val = parseInt(input.value);
        if (val > 0) tempItems[pId] = val; else delete tempItems[pId];
    });

    if (Object.keys(tempItems).length === 0) return showToast("Состав пуст!", "error");

    // 4. Сбор остальных данных
    const managerId = document.getElementById('new-op-manager').value;
    const carNumber = document.getElementById('new-op-car')?.value || "";
    const comment = document.getElementById('new-op-comment')?.value || "";

    const data = {
        shopName,
        managerId,
        items: tempItems,
        carNumber,
        comment,
        createdAt: `${dateVal}T${getCurrentTimeFormat()}`,
        androidId: `MANUAL-${Date.now()}`
    };

    // 5. Определение URL и специфических полей
    let url = '';
    if (type === 'order') {
        url = '/api/admin/orders/create-manual';
        data.deliveryDate = dateVal;
        data.paymentMethod = document.getElementById('new-op-payment').value;
        data.needsSeparateInvoice = document.getElementById('new-op-separate')?.value === "true";
    } else {
        url = '/api/returns/sync';
        data.returnReason = document.getElementById('new-op-reason')?.value || "OTHER";
        data.returnDate = dateVal;
    }

    // 6. Отправка данных
    try {
        const payload = type === 'order' ? data : [data];
        const result = await secureFetch(url, {
            method: 'POST',
            body: payload
        });

        showToast("Операция успешно сохранена", "success");
        setTimeout(() => location.reload(), 800);
    } catch (e) {
        console.error("Save error:", e);
    }
}


// 1. Расчет итоговой суммы (Ваша функция, оставляем и используем)
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

// 2. Быстрое обновление количества без перезагрузки всей таблицы

function removeItemFromEdit(pId) {
    delete tempItems[pId];
    renderItemsTable(tempItems, true);
    showToast("Товар удален из списка", "info"); // Добавляем уведомление
}

// 3. Добавление нового товара в список


function addItemToEdit() {
    const select = document.getElementById('add-item-select');
    const qtyInput = document.getElementById('add-item-qty');
    const pId = select.value;

    if (!pId) return showToast("Сначала выберите товар", "error");

    const qty = parseInt(qtyInput.value) || 1;
    const product = productsData.find(p => p.id == pId);

    if (product) {
        const currentQty = tempItems[pId] || 0;
        tempItems[pId] = currentQty + qty;

        renderItemsTable(tempItems, true);

        // Сбрасываем выбор в пустое состояние
        select.value = "";
        qtyInput.value = 1;
        showStatus("Добавлено");
    }
}


// 5. Идеальный рендеринг таблицы
function renderItemsTable(itemsMap, isEdit) {
    const body = document.getElementById('order-items-body');
    if (!body) return;

    let html = '';
    Object.entries(itemsMap).forEach(([pId, qty]) => {
        const p = productsData.find(prod => prod.id == pId);
        if (!p) return;

        const total = p.price * qty;


        const qtyDisplay = isEdit ?
            `<div class="qty-edit-box" style="display: flex; align-items: center; gap: 3px;">
                <input type="number" id="input-qty-${pId}" class="qty-input-active" value="${qty}" onchange="applySingleQty('${pId}')">
                <button onclick="applySingleQty('${pId}')" title="Обновить" 
                        style="background: none; border: none; cursor: pointer; font-size: 16px; padding: 0;">✅</button>
            </div>` : `<b>${qty} шт.</b>`;

        html += `<tr>
            <td style="padding-left: 15px;">
                ${p.name} 
                <!-- Маленький красный X сразу после имени -->
                ${isEdit ? `<span onclick="removeItemFromEdit('${pId}')" 
                             style="margin-left: 5px; color: #ef4444; cursor: pointer; font-size: 12px; font-weight: bold; vertical-align: middle;">❌</span>` : ''}
            </td>
            <td>${qtyDisplay}</td>
            <td>${p.price.toLocaleString()} ֏</td>
            <td id="total-row-${pId}" style="font-weight:700;">${total.toLocaleString()} ֏</td>
            <td><small class="text-muted">${p.category || '---'}</small></td>
        </tr>`;
    });

    if (isEdit) {
        // Поле добавления: пустой выбор по умолчанию
        const options = `<option value="" disabled selected>Выберите товар...</option>` +
            productsData.map(p => `<option value="${p.id}">${p.name} (${p.price} ֏)</option>`).join('');

        html += `<tr class="add-row-sticky">
            <td><select id="add-item-select" class="form-select" style="font-size: 13px;">${options}</select></td>
            <td><input type="number" id="add-item-qty" value="1" class="form-control" style="width: 60px;"></td>
            <td colspan="3"><button class="btn-primary w-100" onclick="addItemToEdit()" style="padding: 6px;">+ Добавить</button></td>
        </tr>`;
    }

    body.innerHTML = html;
    calculateCurrentTempTotal();
}


function openWriteOffModal() {
    tempItems = {};
    const today = new Date().toISOString().split('T')[0];

    // ИСПРАВЛЕНО: Правильный синтаксис селектора атрибута
    const userElement = document.querySelector('.sidebar [sec\\:authentication]');
    // Экранируем двоеточие обратным слэшем, чтобы JS понял, что это часть имени атрибута

    const currentUser = userElement?.innerText || "ADMIN";

    document.getElementById('modal-title').innerText = "📉 НОВОЕ СПИСАНИЕ ТОВАРА";
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background: #fef2f2; padding: 15px; border-radius: 10px; border: 1px solid #fecdd3;">
            <div><label>КТО СПИСЫВАЕТ</label><input type="text" id="write-off-user" class="form-control" value="${currentUser}" readonly></div>
            <div><label>ДАТА СПИСАНИЯ</label><input type="date" id="write-off-date" class="form-control" value="${today}"></div>
            <div><label>ПРИЧИНА СПИСАНИЯ</label><input type="text" id="write-off-comment" class="form-control" placeholder="Брак / Срок годности"></div>
        </div>`;

    renderItemsTable(tempItems, true);
    // Скрываем Итого для списаний
    const totalEl = document.getElementById('order-total-price');
    if (totalEl) totalEl.style.display = 'none'; // Правильно скрываем элемент

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#ef4444" onclick="submitWriteOff()">✅ ПОДТВЕРДИТЬ СПИСАНИЕ</button>
        <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">ОТМЕНА</button>`;
    openModal('modal-order-view');
}


// В функции openOrderDetails добавьте проверку на списание

function openOrderDetails(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return showToast("Данные не найдены", "error");

    tempItems = syncTempItems(order.items);
    const isWriteOff = order.shopName === 'СПИСАНИЕ';

    // Заголовок модального окна
    document.getElementById('modal-title').innerHTML = isWriteOff
        ? `<span style="color: #ef4444;">📉 СПИСАНИЕ №${order.id}</span>`
        : `ЗАКАЗ №${order.id}`;

    const info = document.getElementById('order-info');

    if (isWriteOff) {
        // ИНФО ДЛЯ СПИСАНИЯ: Кто списал, Дата и Причина
        info.innerHTML = `
            <div style="background: #fef2f2; padding: 15px; border-radius: 10px; border-left: 5px solid #ef4444; margin-top: 15px;">
                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;">
                    <div><small style="color: #991b1b; font-weight: 700;">КТО СПИСАЛ:</small><br><b>${order.managerId || 'ADMIN'}</b></div>
                    <div><small style="color: #991b1b; font-weight: 700;">ДАТА СПИСАНИЯ:</small><br><b>${formatDate(order.createdAt)}</b></div>
                    <div><small style="color: #991b1b; font-weight: 700;">ПРИЧИНА:</small><br><b>${order.comment || 'Не указана'}</b></div>
                </div>
            </div>`;
    } else {
        // ИНФО ДЛЯ ЗАКАЗА: Сетка 4х2
        info.innerHTML = `
            <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; background: #f8fafc; padding: 15px; border-radius: 10px; margin-top: 15px; border: 1px solid #e2e8f0;">
                <div><small style="color: #64748b; font-weight: 700;">МАГАЗИН:</small><br><b style="color: #1e293b;">${order.shopName}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">МЕНЕДЖЕР:</small><br><b>${order.managerId}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">ДАТА:</small><br><b>${formatDate(order.createdAt)}</b></div>
                <div><small style="color: #64748b; font-weight: 700;">АВТО:</small><br><b>${order.carNumber || '---'}</b></div>
                
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">ДОСТАВКА:</small><br><b>${formatDate(order.deliveryDate).split(' ')[0]}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">ОПЛАТА:</small><br><b>${translatePayment(order.paymentMethod)}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">ФАКТУРА:</small><br><b>${order.needsSeparateInvoice ? 'Раздельная' : 'Общая'}</b></div>
                <div style="border-top: 1px solid #e2e8f0; padding-top: 8px;"><small style="color: #64748b; font-weight: 700;">КОММЕНТАРИЙ:</small><br><i style="font-size: 11px;">${order.comment || '---'}</i></div>
            </div>`;
    }

    // Рендерим состав товаров (без возможности редактирования в просмотре)
    renderItemsTable(tempItems, false);

    const footer = document.getElementById('order-footer-actions');
    let btnsHtml = '';

    // Кнопка ИСТОРИИ (Всегда первая и общая для всех типов)
    btnsHtml += `<button class="btn-primary" style="background:#6366f1" onclick="showOrderHistory(${order.id})">📜 История</button>`;

    if (isWriteOff) {
        // Для списаний — после подтверждения (создания) только История и Закрыть
        btnsHtml += `<button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;
    } else {
        // Логика для обычных заказов
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

    footer.innerHTML = btnsHtml;

    // При списании скрываем итоговую сумму
    const totalEl = document.getElementById('order-total-price');
    if (totalEl) totalEl.style.display = isWriteOff ? 'none' : 'block';

    openModal('modal-order-view');
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
        await secureFetch(`/api/admin/orders/${id}/full-edit`, {
            method: 'PUT',
            body: data
        });
        showToast("Заказ обновлен", "success");
        setTimeout(() => location.reload(), 500);
    } catch (e) {
        console.error(e);
    }
}


async function saveReturnChanges(id) {
    if (Object.keys(tempItems).length === 0) {
        return showToast("Состав возврата пуст", "danger");
    }

    // Находим оригинальный объект возврата, чтобы сохранить ID менеджера
    const originalReturn = returnsData.find(r => r.id == id);
    const managerId = originalReturn ? originalReturn.managerId : "OFFICE";

    const data = {
        shopName: document.getElementById('edit-ret-shop').value,
        managerId: managerId, // Берем из данных, так как поля в HTML больше нет
        returnDate: document.getElementById('edit-ret-date').value,
        returnReason: document.getElementById('edit-ret-reason').value,
        carNumber: document.getElementById('edit-ret-car').value,
        comment: document.getElementById('edit-ret-comment').value,
        items: tempItems
    };

    try {
        // Используем метод PUT для обновления существующей записи
        await secureFetch(`/api/admin/returns/${id}/edit`, {
            method: 'PUT',
            body: data
        });

        showToast("Возврат успешно обновлен", "success");

        // Небольшая задержка перед перезагрузкой для визуального подтверждения
        setTimeout(() => location.reload(), 500);
    } catch (e) {
        console.error("Save error:", e);
        // Ошибка уже будет показана внутри secureFetch через showToast
    }
}


function enableReturnEdit(id) {
    // 1. Поиск возврата по ID
    const ret = returnsData.find(r => r.id == id);
    if (!ret) return showToast("Ошибка: Возврат не найден", "error");

    // 2. Синхронизация состава товаров
    tempItems = syncTempItems(ret.items);

    // ПОЛУЧАЕМ ОГРАНИЧЕНИЯ ДАТ ДЛЯ 2026 ГОДА
    const dates = getSmartDeliveryDates();

    document.getElementById('modal-title').innerText = "✏️ Редактирование возврата #" + id;

    // 3. Подготовка списка магазинов
    let clientOptions = clientsData.map(c =>
        `<option value="${c.name}" ${c.name === ret.shopName ? 'selected' : ''}>${c.name}</option>`
    ).join('');

    const info = document.getElementById('order-info');

    // 4. Отрисовка обновленной сетки
    info.innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background: #fff1f2; padding: 15px; border-radius: 10px; border: 1px solid #fecdd3;">
            <div style="grid-column: span 2;">
                <label style="font-size:11px; font-weight:800; color:#9f1239;">МАГАЗИН</label>
                <select id="edit-ret-shop" class="form-select" style="font-weight:700;">${clientOptions}</select>
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
                <label style="font-size:11px; font-weight:800; color:#9f1239;">ДОСТАВКА (ДАТА ВОЗВРАТА)</label>
                <!-- Добавлена блокировка заднего числа через min и проверка onchange -->
                <input type="date" id="edit-ret-date" class="form-control" 
                       min="${dates.min}" 
                       value="${convertDateToISO(ret.returnDate || ret.createdAt)}"
                       onchange="if(this.value < '${dates.min}') { alert('Нельзя выбрать прошедшую дату!'); this.value='${dates.min}'; }">
            </div>
            <div style="margin-top:10px;">
                <label style="font-size:11px; font-weight:800; color:#9f1239;">КОММЕНТАРИЙ</label>
                <input type="text" id="edit-ret-comment" class="form-control" value="${ret.comment || ''}" placeholder="Заметка...">
            </div>
        </div>`;

    // 5. Рендерим состав товаров
    renderItemsTable(tempItems, true);

    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981; padding: 10px 25px;" onclick="saveReturnChanges(${id})">💾 Сохранить</button>
        <button class="btn-primary" style="background:#64748b; padding: 10px 25px;" onclick="openReturnDetails(${id})">Отмена</button>
    `;
}


function openReturnDetails(id) {
    const ret = (returnsData || []).find(r => r.id == id);
    if (!ret) return showToast("Возврат не найден", "error");

    tempItems = syncTempItems(ret.items);
    const isConfirmed = ret.status === 'CONFIRMED';

    document.getElementById('modal-title').innerHTML = `
        Детали операции 
        <span class="badge ${isConfirmed ? 'bg-success' : 'bg-warning'}" style="margin-left:10px;">
            ${isConfirmed ? 'Проведено' : 'Черновик'}
        </span>
        <span class="badge" style="margin-left:5px; background-color: #64748b;">ВОЗВРАТ №${ret.id}</span>
    `;

    // Сетка: Верх (Магазин, Менеджер, Авто), Низ (Причина, Доставка, Коммент)
    document.getElementById('order-info').innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background-color: #fff1f2; padding: 15px; border-radius: 10px; margin-top: 15px; border: 1px solid #fecdd3;">
            <div><small style="color: #9f1239; font-weight: 700;">МАГАЗИН:</small><br><b>${ret.shopName}</b></div>
            <div><small style="color: #9f1239; font-weight: 700;">МЕНЕДЖЕР:</small><br><b>${ret.managerId || '---'}</b></div>
            <div><small style="color: #9f1239; font-weight: 700;">НОМЕР АВТО:</small><br><b>${ret.carNumber || '---'}</b></div>
            
            <div style="border-top: 1px solid #fecdd3; padding-top: 10px;"><small style="color: #9f1239; font-weight: 700;">ПРИЧИНА:</small><br><b style="color:#ef4444;">${translateReason(ret.returnReason)}</b></div>
            <div style="border-top: 1px solid #fecdd3; padding-top: 10px;"><small style="color: #9f1239; font-weight: 700;">ДОСТАВКА:</small><br><b>${formatDate(ret.returnDate || ret.createdAt).split(' ')[0]}</b></div>
            <div style="border-top: 1px solid #fecdd3; padding-top: 10px;"><small style="color: #9f1239; font-weight: 700;">КОММЕНТАРИЙ:</small><br><i>${ret.comment || '---'}</i></div>
        </div>
    `;

    renderItemsTable(tempItems, false);

    const footer = document.getElementById('order-footer-actions');
    const commonBtns = `<button class="btn-primary" style="background-color:#475569" onclick="printReturn(${ret.id})">🖨 Печать</button>
                        <button class="btn-primary" style="background-color:#64748b" onclick="closeModal('modal-order-view')">Закрыть</button>`;

    footer.innerHTML = !isConfirmed ? `
        <button class="btn-primary" style="background-color:#10b981" onclick="confirmReturn(${ret.id})">✅ Провести</button>
        <button class="btn-primary" onclick="enableReturnEdit(${ret.id})">✏️ Изменить</button>
        <button class="btn-primary" style="background-color:#ef4444" onclick="deleteReturnOrder(${ret.id})">❌ Удалить</button>
        ${commonBtns}` : `<div style="flex: 1; color: #166534; font-weight: bold;">✓ Операция проведена</div>${commonBtns}`;

    openModal('modal-order-view');
}


function enableOrderEdit(id) {
    const order = ordersData.find(o => o.id == id);
    if (!order) return showToast("Ошибка: Заказ не найден", "error");

    tempItems = syncTempItems(order.items);
    const dates = getSmartDeliveryDates(); // Получаем текущие ограничения 2026 года

    document.getElementById('modal-title').innerText = "📝 Редактирование заказа #" + id;

    const info = document.getElementById('order-info');
    info.innerHTML = `
        <div class="modal-info-grid" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; background: #f1f5f9; padding: 15px; border-radius: 10px;">
            <div><label>МАГАЗИН</label>
                <select id="edit-shop" class="form-select">
                    ${clientsData.map(c => `<option value="${c.name}" ${c.name === order.shopName ? 'selected' : ''}>${c.name}</option>`).join('')}
                </select>
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
            <div style="margin-top:10px;"><label>КОММЕНТАРИЙ</label><input type="text" id="edit-comment" class="form-control" value="${order.comment || ''}"></div>
        </div>`;

    renderItemsTable(tempItems, true);
    document.getElementById('order-footer-actions').innerHTML = `
        <button class="btn-primary" style="background:#10b981" onclick="saveFullChanges(${id})">💾 Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openOrderDetails(${id})">Отмена</button>`;
}


async function saveClientChanges(id) {
    // 1. Собираем все данные из полей ввода
    const data = {
        name: document.getElementById('edit-client-name').value,
        category: document.getElementById('edit-client-category').value,
        ownerName: document.getElementById('edit-client-owner').value,
        inn: document.getElementById('edit-client-inn').value,
        phone: document.getElementById('edit-client-phone').value,
        address: document.getElementById('edit-client-address').value,
        debt: parseFloat(document.getElementById('edit-client-debt').value) || 0,
        bankName: document.getElementById('edit-client-bank-name').value, // Название банка
        bankAccount: document.getElementById('edit-client-bank').value, // IBAN
        managerId: document.getElementById('edit-client-manager').value,
        routeDay: document.getElementById('edit-client-route-day').value
    };

    try {
        // 2. Отправляем на сервер (убедитесь, что в Java Controller добавлены новые поля)
        await secureFetch(`/api/admin/clients/${id}/edit`, {
            method: 'PUT',
            body: data
        });

        // 3. Синхронизируем локальный массив данных
        const idx = clientsData.findIndex(c => c.id == id);
        if (idx !== -1) {
            // Обновляем все поля в локальной переменной
            clientsData[idx] = {...clientsData[idx], ...data};

            // 4. Обновляем ячейки в основной таблице (Web-интерфейс)
            const row = document.querySelector(`tr[onclick*="openClientDetails(${id})"]`);
            if (row) {
                row.cells[0].innerText = data.name;
                row.cells[1].innerText = data.address;
                row.cells[2].innerText = data.category || '---';
                row.cells[3].innerText = data.debt.toLocaleString() + ' ֏';

                // Исправлено: применяем класс цвета к ячейке с долгом
                row.cells[3].className = data.debt > 0 ? 'price-down' : '';
            }
        }

        showToast("Данные клиента обновлены", "success");
        openClientDetails(id); // Возвращаемся к просмотру деталей (они подтянутся из обновленного clientsData)
    } catch (e) {
        console.error("Ошибка сохранения клиента:", e);
        showToast("Не удалось сохранить изменения", "error");
    }
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
        <button class="btn-primary" style="background:#10b981" onclick="saveProductChanges(${p.id})">💾 Сохранить</button>
        <button class="btn-primary" style="background:#64748b" onclick="openProductDetails(${p.id})">Отмена</button>
    `;
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



// async function openProductDetails(id) {
//     window.currentProductId = id;
//     const p = productsData.find(prod => prod.id == id);
//     if (!p) return;
//
//     document.getElementById('modal-product-title').innerHTML = `📦 ${p.name}`;
//     const info = document.getElementById('product-info');
//
//     // Наполняем данными в 2 ряда, используя стили из tab-orders
//     info.innerHTML = `
//         <div class="container-fluid p-0">
//             <!-- РЯД 1: ЦЕНА, КАТЕГОРИЯ, СКЛАД, КОД АТГ -->
//             <div class="row g-2 mb-3">
//                 <div class="col-md-3">
//                     <small class="text-muted d-block mb-1">ЦЕНА:</small>
//                     <b class="price-up" style="font-size: 16px;">${p.price.toLocaleString()} ֏</b>
//                 </div>
//                 <div class="col-md-3">
//                     <small class="text-muted d-block mb-1">КАТЕГОРИЯ:</small>
//                     <b class="text-dark">${p.category || '---'}</b>
//                 </div>
//                 <div class="col-md-3">
//                     <small class="text-muted d-block mb-1">СКЛАД:</small>
//                     <b class="text-success">Основной</b> <!-- Вернул как заглушку -->
//                 </div>
//                 <div class="col-md-3">
//                     <small class="text-muted d-block mb-1">КОД АТГ (SKU):</small>
//                     <b class="text-dark" style="font-family: monospace;">${p.hsnCode || '---'}</b>
//                 </div>
//             </div>
//
//             <!-- РЯД 2: Остаток, Штрих-код, Упаковка/Ед.изм, Срок -->
//             <div class="row g-2 mb-3">
//                 <div class="col-md-2">
//                     <small class="text-muted d-block mb-1">Остаток:</small>
//                     <span class="badge ${p.stockQuantity > 10 ? 'bg-light text-dark' : 'bg-danger text-white'}" style="padding: 6px;">${p.stockQuantity} шт.</span>
//                 </div>
//                 <div class="col-md-3">
//                     <small class="text-muted d-block mb-1">ШТРИХ-КОД:</small>
//                     <span style="font-size: 12px; font-family: monospace;">${p.barcode || '---'}</span>
//                 </div>
//                 <div class="col-md-4">
//                     <small class="text-muted d-block mb-1">Упак. / Ед. изм.</small>
//                     <div class="d-flex align-items-center">
//                         <span class="fw-bold pe-1 border-end w-50 text-end">${p.itemsPerBox || 0}</span>
//                         <span class="ps-1 text-primary fw-bold w-50 text-start">${p.unit || 'шт'}</span>
//                     </div>
//                 </div>
//                 <div class="col-md-3">
//                     <small class="text-muted d-block mb-1">СРОК:</small>
//                     <span class="fw-bold">${p.expiryDate ? formatDate(p.expiryDate) : '---'}</span>
//                 </div>
//             </div>
//
//             <!-- История (стиль сохранен) -->
//             <div id="product-history-box" style="margin-top:20px;">
//                 <label class="label-small text-muted mb-2">📜 ИСТОРИЯ ДВИЖЕНИЯ (2026)</label>
//                 <div class="table-scroll-mini" style="max-height: 150px; overflow-y: auto;">
//                     <table class="table table-sm">
//                         <tbody id="product-history-body"><tr><td>Загрузка...</td></tr></tbody>
//                     </table>
//                 </div>
//             </div>
//         </div>
//     `;
//
//     // Загрузка истории (без изменений)
//     try {
//         const history = await secureFetch(`/api/products/${encodeURIComponent(p.name)}/history`);
//         const tbody = document.getElementById('product-history-body');
//         if (tbody) {
//             tbody.innerHTML = history.map(h => `
//                 <tr>
//                     <td class="ps-2">${formatDate(h.timestamp)}</td>
//                     <td><span class="badge ${h.type === 'WRITE_OFF' ? 'bg-danger' : 'bg-info'}" style="font-size:10px;">${h.type}</span></td>
//                     <td class="text-end pe-2" style="color:${h.quantityChange > 0 ? '#10b981' : '#ef4444'}">
//                         <b>${h.quantityChange > 0 ? '+' : ''}${h.quantityChange}</b>
//                     </td>
//                 </tr>`).join('') || '<tr><td colspan="3" class="text-center p-3">Движений нет</td></tr>';
//         }
//     } catch (e) { console.warn("История недоступна"); }
//
//     // Кнопки управления в футере: Итого слева, кнопки справа (Как на скриншоте заказа)
//     document.getElementById('product-footer-actions').innerHTML = `
//         <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
//             <!-- Итоговая сумма слева -->
//             <b class="price-up" style="font-size: 1.2rem;">Итого: ${p.price.toLocaleString()} ֏</b>
//
//             <!-- Кнопки справа -->
//             <div style="display: flex; gap: 10px;">
//                 <button class="btn-primary" style="background:#f59e0b" onclick="doInventory()">⚖️ Инвентарь</button>
//                 <button class="btn-primary" onclick="enableProductEdit()">✏️ Изменить</button>
//                 <button class="btn-primary" style="background:#ef4444;" onclick="deleteProduct(${p.id})">🗑 Удалить</button>
//                 <button class="btn-primary" style="background:#64748b" onclick="closeModal('modal-product-view')">Закрыть</button>
//             </div>
//         </div>
//     `;
//
//     openModal('modal-product-view');
// }



// Универсальный переключатель режима Редактировать/Просмотр
function toggleProductEdit(isEdit) {
    const view = document.getElementById('product-view-mode');
    const edit = document.getElementById('product-edit-mode');
    if (view && edit) {
        view.style.display = isEdit ? 'none' : 'block';
        edit.style.display = isEdit ? 'block' : 'none';
    }
}






// async function saveProductChanges(id) {
//     // 1. Собираем данные из новых компактных полей
//     const data = {
//         name: document.getElementById('edit-product-name').value,
//         price: parseFloat(document.getElementById('edit-product-price').value) || 0,
//         stockQuantity: parseInt(document.getElementById('edit-product-qty').value) || 0,
//         barcode: document.getElementById('edit-product-barcode').value,
//         itemsPerBox: parseInt(document.getElementById('edit-product-perbox').value) || 0,
//         category: document.getElementById('edit-product-category').value,
//         hsnCode: document.getElementById('edit-product-hsn').value,
//         unit: document.getElementById('edit-product-unit').value,
//         expiryDate: document.getElementById('edit-product-expiry').value
//     };
//
//     try {
//         // 2. Отправка на сервер через PUT
//         await secureFetch(`/api/admin/products/${id}/edit`, {
//             method: 'PUT',
//             body: data
//         });
//
//         // 3. Обновляем локальный массив данных
//         const idx = productsData.findIndex(p => p.id == id);
//         if (idx !== -1) {
//             productsData[idx] = {...productsData[idx], ...data};
//
//             // 4. Умное обновление строки в таблице (без перезагрузки)
//             // Ищем строку по ID товара в атрибуте onclick
//             const row = document.querySelector(`tr[onclick*="openProductDetails(${id})"]`);
//             if (row) {
//                 // Название
//                 if (row.cells[0].querySelector('div')) row.cells[0].querySelector('div').innerText = data.name;
//                 // Цена
//                 row.cells[1].innerText = data.price.toLocaleString() + ' ֏';
//                 // Остаток (с сохранением стиля badge)
//                 const qtyBadge = row.cells[2].querySelector('span');
//                 if (qtyBadge) {
//                     qtyBadge.innerText = data.stockQuantity + ' шт.';
//                     // Динамическая смена цвета если остаток мал
//                     qtyBadge.className = data.stockQuantity > 10 ? 'badge bg-light text-dark' : 'badge bg-danger text-white';
//                 }
//                 // Упаковка
//                 row.cells[3].innerText = `${data.itemsPerBox} шт/уп`;
//                 // Штрих-код
//                 row.cells[4].innerText = data.barcode || '---';
//                 // Срок годности (форматируем перед вставкой)
//                 if (row.cells[5] && typeof formatDate === 'function') {
//                     row.cells[5].innerText = data.expiryDate ? formatDate(data.expiryDate) : '---';
//                 }
//             }
//         }
//
//         if (typeof showToast === 'function') showToast("Товар обновлен", "success");
//
//         // 5. Возвращаемся в режим просмотра деталей
//         openProductDetails(id);
//
//     } catch (e) {
//         console.error("Ошибка сохранения:", e);
//         if (typeof showToast === 'function') showToast("Ошибка при сохранении", "danger");
//     }
// }




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


// --- НОВАЯ ФУНКЦИЯ ДЛЯ ЛОГИСТИКИ ---
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
            alert("⚠️ Воскресенье — выходной. Пожалуйста, выберите рабочий день.");
            this.value = finalDateStr;
        }
    });
}





document.addEventListener("DOMContentLoaded", async () => {
    console.log("🚀 Sellion ERP 2026: Инициализация системы...");

    // --- 1. CSRF ЗАЩИТА ---
    const token = document.querySelector('input[name="_csrf"]')?.value;
    window.apiHeaders = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    };
    if (token) window.apiHeaders['X-CSRF-TOKEN'] = token;

    // --- 2. СИСТЕМНЫЕ СЛУЖБЫ ---
    if (typeof connectWebSocket === 'function') connectWebSocket();

    // --- 3. ЗАГРУЗКА ДАННЫХ (Параллельно) ---
    const initData = async () => {
        try {
            const promises = [];
            if (typeof loadManagerIds === 'function') promises.push(loadManagerIds());
            if (typeof loadApiKeys === 'function') promises.push(loadApiKeys());
            await Promise.all(promises);

              initDeliveryDateLogic();
        } catch (e) {
            console.error("Ошибка загрузки начальных данных:", e);
        }
    };
    initData();

    // --- 4. НАВИГАЦИЯ ---
    const lastTab = localStorage.getItem('sellion_tab') || 'tab-main';
    if (typeof showTab === 'function') showTab(lastTab);

    // --- 5. ФОРМАТИРОВАНИЕ И СЧЕТЧИКИ ---
    const runFormatting = () => {
        // Форматируем даты
        document.querySelectorAll('.js-date-format').forEach(el => {
            const val = el.innerText.trim();
            if (val && val !== '---' && val !== '') {
                if (typeof formatDate === 'function') {
                    el.innerText = formatDate(val);
                }
            }
        });

        // Переводим статусы
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

        // Обновление счетчиков
        if (typeof refreshReportCounters === 'function') {
            refreshReportCounters();
        }
    };

    runFormatting();

    // --- 6. ГЛОБАЛЬНЫЙ ДЕЛЕГАТ СОБЫТИЙ ---
    document.body.addEventListener('click', function (e) {
        // Обновление данных при переходе по вкладкам
        if (e.target.closest('.nav-link')) {
            requestAnimationFrame(() => setTimeout(runFormatting, 100));
        }

        // --- ЛОГИКА АККОРДЕОНА (СКЛАД) ---
        const categoryHeader = e.target.closest('.js-category-toggle');
        if (categoryHeader) {
            const targetClass = categoryHeader.getAttribute('data-target');
            const rows = document.querySelectorAll(`.${targetClass}`);
            const icon = categoryHeader.querySelector('.toggle-icon');

            // Определяем текущее состояние.
            // ВАЖНО: Если style.display пустой, значит строка видна (table-row)
            const firstRow = rows[0];
            const isCurrentlyHidden = firstRow ? (firstRow.style.display === 'none') : false;

            rows.forEach(row => {
                // Если было скрыто — показываем, если было видно — скрываем
                row.style.display = isCurrentlyHidden ? 'table-row' : 'none';
            });

            // Анимация иконки
            if (icon) {
                icon.style.transform = isCurrentlyHidden ? "rotate(0deg)" : "rotate(-90deg)";
                icon.innerText = isCurrentlyHidden ? "▼" : "▶";
            }
        }
    });


    console.log("Sellion ERP 2026: Система полностью готова к работе.");
});

