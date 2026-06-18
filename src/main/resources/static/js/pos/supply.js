const STATUS_LABELS = {
    REQUESTED: "주문확인중",
    CONFIRMED: "입금대기중",
    DEPOSITED: "발송대기중",
    SHIPPED: "발송완료"
};

// 발주 상태 → 시안 칩 색상 클래스
const CHIP_CLASS = {
    REQUESTED: "confirm",
    CONFIRMED: "pay",
    DEPOSITED: "ship",
    SHIPPED: "done"
};

const VISIBLE_ORDERS = 5;

// 품목별 수량 정책(최소/기본/최대). itemCode로 매핑한다.
const ITEM_UI = {
    DOUGH_MIX: {min: 0, defaultQty: 1, max: 9999},
    PLASTIC_BAG: {min: 0, defaultQty: 1, max: 999}
};
const DEFAULT_UI = {min: 0, defaultQty: 1, max: 9999};

// 품목별 아이콘(시안과 동일). 미등록 코드는 기본 아이콘.
const ITEM_ICONS = {
    DOUGH_MIX: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M7 8V5h10v3M6 8h12l-1 12a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1z" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    PLASTIC_BAG: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M6 8h12l-1 11a1.5 1.5 0 0 1-1.5 1.4H8.5A1.5 1.5 0 0 1 7 19zM9 8a3 3 0 0 1 6 0" stroke-linecap="round" stroke-linejoin="round"/></svg>'
};
const DEFAULT_ICON = ITEM_ICONS.DOUGH_MIX;

const CALENDAR_ICON = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3.5" y="5" width="17" height="15" rx="2.5"/><path d="M3.5 9.5h17M8 3.5v3M16 3.5v3" stroke-linecap="round"/></svg>';

// 희망 배송일 기본값: 오늘로부터 며칠 뒤로 채워둔다(반죽 등 리드타임 고려).
const DEFAULT_DELIVERY_LEAD_DAYS = 5;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

let supplyItems = [];
const quantities = {};
let orders = [];
let ordersExpanded = false;
let desiredDeliveryDate = defaultDeliveryDate();

const formatNumber = (value) => new Intl.NumberFormat("ko-KR").format(value);
const formatWon = (value) => `₩${formatNumber(value)}`;

// Date → "YYYY-MM-DD"(<input type="date">/서버 전송용 ISO 표기)
function toISODate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function todayISODate() {
    return toISODate(new Date());
}

function defaultDeliveryDate() {
    const date = new Date();
    date.setDate(date.getDate() + DEFAULT_DELIVERY_LEAD_DAYS);
    return toISODate(date);
}

// "YYYY-MM-DD" → "YYYY. MM. DD (요일)"(확인 모달 표시용)
function formatDeliveryDate(isoDate) {
    const date = new Date(`${isoDate}T00:00:00`);
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${date.getFullYear()}. ${month}. ${day} (${WEEKDAYS[date.getDay()]})`;
}

function getCookie(name) {
    return document.cookie
        .split("; ")
        .find((row) => row.startsWith(`${name}=`))
        ?.split("=")[1];
}

function jsonHeaders() {
    const csrfToken = getCookie("XSRF-TOKEN");
    const headers = {"Content-Type": "application/json"};

    if (csrfToken) {
        headers["X-XSRF-TOKEN"] = decodeURIComponent(csrfToken);
    }

    return headers;
}

function makeDisplay(perPack, packUnit, unit, quantity) {
    if (perPack > 1) {
        return `${formatNumber(quantity * perPack)}${packUnit} (${formatNumber(quantity)}${unit})`;
    }
    return `${formatNumber(quantity)}${unit}`;
}

function amountOf(item) {
    return quantities[item.code] * item.unitPrice;
}

function formatWhen(isoString) {
    const date = new Date(isoString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");

    return `${year}.${month}.${day} ${hour}:${minute}`;
}

async function loadStore() {
    const storeName = document.getElementById("storeName");

    if (!storeName) {
        return;
    }

    try {
        const response = await fetch("/api/stores/me");

        if (!response.ok) {
            storeName.textContent = "매장명: -";
            return;
        }

        const store = await response.json();
        storeName.textContent = `매장명: ${store.name}`;
    } catch {
        storeName.textContent = "매장명: -";
    }
}

async function loadCatalog() {
    try {
        const response = await fetch("/api/v1/supply-items");

        if (!response.ok) {
            supplyItems = [];
            return;
        }

        const items = await response.json();
        supplyItems = items.map((catalog) => ({
            code: catalog.code,
            name: catalog.name,
            unit: catalog.unit,
            unitPrice: catalog.unitPrice,
            perPack: catalog.packSize ?? 1,
            packUnit: catalog.packUnit,
            ...(ITEM_UI[catalog.code] ?? DEFAULT_UI)
        }));
    } catch {
        supplyItems = [];
    }
}

async function loadOrders() {
    try {
        const response = await fetch("/api/v1/supply-orders");

        if (!response.ok) {
            orders = [];
            return;
        }

        orders = (await response.json()).map(toOrderView);
    } catch {
        orders = [];
    }
}

function toOrderView(order) {
    return {
        createdAt: order.createdAt,
        status: order.status,
        totalAmount: order.totalAmount,
        items: order.items.map((item) => {
            const catalog = supplyItems.find((c) => c.code === item.itemCode);
            const perPack = catalog?.perPack ?? 1;
            return {
                itemName: item.itemName,
                display: makeDisplay(perPack, catalog?.packUnit, item.unit, item.quantity)
            };
        })
    };
}

function buildSpan(className, text, id) {
    const span = document.createElement("span");
    span.className = className;
    span.textContent = text;
    if (id) {
        span.id = id;
    }
    return span;
}

function renderForm() {
    const form = document.getElementById("supplyForm");

    const rows = supplyItems.map(createItemRow);
    // 마지막 품목행은 아래 희망 배송일 행과 경계선으로 구분되므로 border를 유지한다.

    const delivery = createDeliveryRow();

    const foot = document.createElement("div");
    foot.className = "supply-foot";

    const totalWrap = document.createElement("div");
    totalWrap.append(buildSpan("total-lbl", "발주 합계"), buildSpan("total-amt", "₩0", "formTotal"));

    const submit = document.createElement("button");
    submit.type = "button";
    submit.className = "order-btn";
    submit.id = "submitBtn";
    submit.textContent = "발주하기";
    submit.addEventListener("click", openConfirm);

    foot.append(totalWrap, submit);
    form.replaceChildren(...rows, delivery, foot);
}

function createDeliveryRow() {
    const row = document.createElement("div");
    row.className = "delivery";

    const info = document.createElement("div");
    info.className = "delivery-info";

    const icon = document.createElement("span");
    icon.className = "delivery-ic";
    icon.innerHTML = CALENDAR_ICON;

    const text = document.createElement("div");
    text.className = "delivery-text";

    const label = document.createElement("span");
    label.className = "delivery-label";
    label.textContent = "희망 배송일";

    const hint = document.createElement("span");
    hint.className = "delivery-hint";
    hint.textContent = "이 날짜까지 받아볼 수 있도록 본사에 요청해요";

    text.append(label, hint);
    info.append(icon, text);

    const input = document.createElement("input");
    input.className = "date-input";
    input.id = "deliveryDate";
    input.type = "date";
    input.min = todayISODate();
    input.value = desiredDeliveryDate;
    // 비우면 기본값(오늘+리드타임)으로 되돌려 항상 값이 채워지도록 한다.
    input.addEventListener("change", () => {
        desiredDeliveryDate = input.value || defaultDeliveryDate();
        input.value = desiredDeliveryDate;
    });

    row.append(info, input);
    return row;
}

function createItemRow(item) {
    quantities[item.code] = item.defaultQty;

    const row = document.createElement("div");
    row.className = "mat-row";

    const left = document.createElement("div");
    left.className = "mat-left";

    const mark = document.createElement("div");
    mark.className = "mat-mark";
    mark.innerHTML = ITEM_ICONS[item.code] ?? DEFAULT_ICON;

    const info = document.createElement("div");
    const name = document.createElement("div");
    name.className = "mat-name";
    name.textContent = item.name;

    const unit = document.createElement("div");
    unit.className = "mat-unit";
    const unitPrice = document.createElement("b");
    unitPrice.textContent = `${formatNumber(item.unitPrice)}원`;
    unit.append(unitPrice, ` / ${item.unit}`);

    info.append(name, unit);
    left.append(mark, info);

    const right = document.createElement("div");
    right.className = "mat-right";
    right.append(buildStepper(item), buildSpan("subtotal", "", `sub-${item.code}`));

    row.append(left, right);
    return row;
}

function buildStepper(item) {
    const minus = document.createElement("button");
    minus.type = "button";
    minus.className = "step-btn minus";
    minus.id = `minus-${item.code}`;
    minus.textContent = "−";
    minus.addEventListener("click", () => changeStep(item, -1));

    const input = document.createElement("input");
    input.className = "qty-input";
    input.id = `qty-${item.code}`;
    input.type = "text";
    input.inputMode = "numeric";
    input.placeholder = "0";
    input.addEventListener("input", () => onInput(item, input));
    input.addEventListener("blur", () => {
        if (input.value === "") {
            quantities[item.code] = 0;
            updateMinus(item);
            updateSub(item);
            updateTotal();
        }
    });

    const wrap = document.createElement("span");
    wrap.className = "qty-wrap";
    wrap.append(input, buildSpan("uu", item.unit));

    const plus = document.createElement("button");
    plus.type = "button";
    plus.className = "step-btn plus";
    plus.textContent = "+";
    plus.addEventListener("click", () => changeStep(item, 1));

    const stepper = document.createElement("div");
    stepper.className = "stepper";
    stepper.append(minus, wrap, plus);
    return stepper;
}

function updateMinus(item) {
    document.getElementById(`minus-${item.code}`).disabled = quantities[item.code] <= item.min;
}

function onInput(item, input) {
    const digits = input.value.replace(/[^0-9]/g, "");
    let quantity = digits === "" ? 0 : parseInt(digits, 10);

    if (quantity > item.max) {
        quantity = item.max;
    }

    quantities[item.code] = quantity;
    input.value = digits === "" ? "" : formatNumber(quantity);

    updateMinus(item);
    updateSub(item);
    updateTotal();
}

function changeStep(item, direction) {
    const next = quantities[item.code] + direction;
    quantities[item.code] = Math.min(item.max, Math.max(item.min, next));

    document.getElementById(`qty-${item.code}`).value = formatNumber(quantities[item.code]);

    updateMinus(item);
    updateSub(item);
    updateTotal();
}

function updateSub(item) {
    const sub = document.getElementById(`sub-${item.code}`);
    sub.textContent = formatWon(amountOf(item));
    sub.classList.toggle("zero", quantities[item.code] === 0);
}

function selectedItems() {
    return supplyItems
            .filter((item) => quantities[item.code] > 0)
            .map((item) => ({
                itemCode: item.code,
                itemName: item.name,
                display: makeDisplay(item.perPack, item.packUnit, item.unit, quantities[item.code]),
                quantity: quantities[item.code],
                amount: amountOf(item)
            }));
}

function totalAmount() {
    return selectedItems().reduce((sum, item) => sum + item.amount, 0);
}

function updateTotal() {
    const total = totalAmount();

    document.getElementById("formTotal").textContent = formatWon(total);
    document.getElementById("submitBtn").disabled = total === 0;
}

function openConfirm() {
    const items = selectedItems();

    if (items.length === 0) {
        return;
    }

    document.getElementById("modalList").replaceChildren(...items.map(createModalRow));
    document.getElementById("modalDelivery").textContent = formatDeliveryDate(desiredDeliveryDate);
    document.getElementById("modalTotal").textContent = formatWon(totalAmount());
    document.getElementById("confirmModal").classList.add("open");
}

function createModalRow(item) {
    const row = document.createElement("div");
    row.className = "m-row";

    const name = document.createElement("span");
    name.className = "nm";
    name.append(item.itemName, buildSpan("q", item.display));

    row.append(name, buildSpan("pr", formatWon(item.amount)));
    return row;
}

function closeConfirm() {
    document.getElementById("confirmModal").classList.remove("open");
}

async function confirmOrder() {
    const items = selectedItems();

    if (items.length === 0) {
        closeConfirm();
        return;
    }

    const confirmButton = document.getElementById("modalConfirm");
    confirmButton.disabled = true;

    try {
        const response = await fetch("/api/v1/supply-orders", {
            method: "POST",
            headers: jsonHeaders(),
            body: JSON.stringify({
                // TODO(백엔드): supply_order에 desiredDeliveryDate 저장 — 현재 서버는 미지원 필드로 무시한다.
                desiredDeliveryDate,
                items: items.map((item) => ({itemCode: item.itemCode, quantity: item.quantity}))
            })
        });

        if (!response.ok) {
            return;
        }

        resetForm();
        closeConfirm();
        await loadOrders();
        renderOrders();
    } finally {
        confirmButton.disabled = false;
    }
}

function resetForm() {
    supplyItems.forEach((item) => {
        quantities[item.code] = item.defaultQty;

        document.getElementById(`qty-${item.code}`).value = item.defaultQty === 0 ? "" : formatNumber(item.defaultQty);
        updateMinus(item);
        updateSub(item);
    });

    desiredDeliveryDate = defaultDeliveryDate();
    const dateInput = document.getElementById("deliveryDate");
    if (dateInput) {
        dateInput.value = desiredDeliveryDate;
    }

    updateTotal();
}

function renderOrders() {
    const list = document.getElementById("orderList");

    if (orders.length === 0) {
        const empty = document.createElement("div");
        empty.className = "order-empty";
        empty.textContent = "아직 발주 내역이 없습니다";
        list.replaceChildren(empty);
        return;
    }

    const visible = ordersExpanded ? orders : orders.slice(0, VISIBLE_ORDERS);
    const nodes = visible.map(createOrderRow);

    if (!ordersExpanded && orders.length > VISIBLE_ORDERS) {
        const more = document.createElement("button");
        more.type = "button";
        more.className = "more-btn";
        more.textContent = `더보기 (${orders.length - VISIBLE_ORDERS}건)`;
        more.addEventListener("click", () => {
            ordersExpanded = true;
            renderOrders();
        });
        nodes.push(more);
    }

    list.replaceChildren(...nodes);
}

function createOrderRow(order) {
    const row = document.createElement("div");
    row.className = "hist-row";

    const left = document.createElement("div");
    left.className = "hist-left";

    const top = document.createElement("div");
    top.className = "hist-top";
    top.append(buildSpan("hist-date", formatWhen(order.createdAt)));

    const items = document.createElement("div");
    items.className = "hist-items";
    items.textContent = order.items.map((item) => `${item.itemName} ${item.display}`).join(", ");

    left.append(top, items);

    const right = document.createElement("div");
    right.className = "hist-right";

    const amount = buildSpan("hist-amt", formatWon(order.totalAmount));

    const chip = document.createElement("span");
    chip.className = `chip ${CHIP_CLASS[order.status] ?? "confirm"}`;
    chip.append(buildSpan("dot", ""), STATUS_LABELS[order.status] ?? "-");

    right.append(amount, chip);
    row.append(left, right);
    return row;
}

function bindModal() {
    document.getElementById("modalCancel").addEventListener("click", closeConfirm);
    document.getElementById("modalConfirm").addEventListener("click", confirmOrder);
    document.getElementById("confirmModal").addEventListener("click", (event) => {
        if (event.target.id === "confirmModal") {
            closeConfirm();
        }
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    loadStore();
    bindModal();
    await loadCatalog();
    renderForm();
    resetForm();
    await loadOrders();
    renderOrders();
});