const SUPPLY_ITEMS = [
    {code: "DOUGH_MIX", name: "반죽믹스", unit: "포", perPack: 1, unitPrice: 35000, min: 0, defaultQty: 1, max: 9999, control: "input"},
    {code: "PLASTIC_BAG", name: "비닐봉투", unit: "묶음", perPack: 6000, packUnit: "매", unitPrice: 152000, min: 0, defaultQty: 1, max: 999, control: "stepper"}
];

const STATUS_LABELS = {
    REQUESTED: "주문확인중",
    CONFIRMED: "입금대기중",
    DEPOSITED: "발송대기중",
    SHIPPED: "발송완료"
};

const VISIBLE_ORDERS = 5;

const quantities = {};
let orders = [];
let ordersExpanded = false;

const formatNumber = (value) => new Intl.NumberFormat("ko-KR").format(value);
const formatPrice = (value) => `${formatNumber(value)}원`;

function amountOf(item) {
    return quantities[item.code] * item.unitPrice;
}

function itemDisplay(item) {
    const quantity = quantities[item.code];

    if (item.perPack === 1) {
        return `${formatNumber(quantity)}${item.unit}`;
    }
    return `${formatNumber(quantity * item.perPack)}${item.packUnit} (${formatNumber(quantity)}${item.unit})`;
}

function formatWhen(isoString) {
    const date = new Date(isoString);
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");

    return `${month}.${day} ${hour}:${minute}`;
}

async function loadStore() {
    const storeName = document.getElementById("storeName");

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

function renderForm() {
    const form = document.getElementById("supplyForm");

    const rows = SUPPLY_ITEMS.map(createItemRow);

    const actions = document.createElement("div");
    actions.className = "form-actions";

    const total = document.createElement("span");
    total.className = "form-total";
    total.append("합계", buildSpan("amount", "0원", "formTotal"));

    const submit = document.createElement("button");
    submit.type = "button";
    submit.className = "submit-btn";
    submit.id = "submitBtn";
    submit.textContent = "발주하기";
    submit.addEventListener("click", openConfirm);

    actions.append(total, submit);
    form.replaceChildren(...rows, actions);
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

function createItemRow(item) {
    quantities[item.code] = item.defaultQty;

    const row = document.createElement("div");
    row.className = "item-row";

    const name = document.createElement("span");
    name.className = "item-name";
    name.textContent = item.name;

    const field = document.createElement("div");
    field.className = "qty-field";

    const control = document.createElement("div");
    control.className = "qty-control";
    control.append(...(item.control === "stepper" ? buildStepper(item) : buildInput(item)));

    const sub = buildSpan("qty-sub", "", `sub-${item.code}`);

    field.append(control, sub);
    row.append(name, field);
    return row;
}

function buildInput(item) {
    const input = document.createElement("input");
    input.className = "qty-input";
    input.id = `qty-${item.code}`;
    input.type = "text";
    input.inputMode = "numeric";
    input.placeholder = "0";
    input.addEventListener("input", () => onInput(item, input));

    const suffix = buildSpan("qty-suffix", item.unit);
    return [input, suffix];
}

function buildStepper(item) {
    const minus = document.createElement("button");
    minus.type = "button";
    minus.className = "step-btn";
    minus.id = `minus-${item.code}`;
    minus.textContent = "−";
    minus.addEventListener("click", () => changeStep(item, -1));

    const value = buildSpan("step-value", String(item.defaultQty), `qty-${item.code}`);

    const plus = document.createElement("button");
    plus.type = "button";
    plus.className = "step-btn";
    plus.textContent = "+";
    plus.addEventListener("click", () => changeStep(item, 1));

    const box = document.createElement("div");
    box.className = "stepper-box";
    box.append(minus, value, plus);

    const suffix = buildSpan("qty-suffix", item.unit);
    return [box, suffix];
}

function onInput(item, input) {
    const digits = input.value.replace(/[^0-9]/g, "");
    let quantity = digits === "" ? 0 : parseInt(digits, 10);

    if (quantity > item.max) {
        quantity = item.max;
    }

    quantities[item.code] = quantity;
    input.value = quantity === 0 ? "" : formatNumber(quantity);

    updateSub(item);
    updateTotal();
}

function changeStep(item, direction) {
    const next = quantities[item.code] + direction;
    quantities[item.code] = Math.min(item.max, Math.max(item.min, next));

    document.getElementById(`qty-${item.code}`).textContent = formatNumber(quantities[item.code]);
    document.getElementById(`minus-${item.code}`).disabled = quantities[item.code] <= item.min;

    updateSub(item);
    updateTotal();
}

function updateSub(item) {
    const sub = document.getElementById(`sub-${item.code}`);
    const quantity = quantities[item.code];

    if (quantity === 0) {
        sub.replaceChildren();
        return;
    }

    const conversion = item.perPack > 1
            ? `${formatNumber(quantity * item.perPack)}${item.packUnit} · `
            : "";

    sub.replaceChildren(conversion, buildSpan("amount", formatPrice(amountOf(item))));
}

function selectedItems() {
    return SUPPLY_ITEMS
            .filter((item) => quantities[item.code] > 0)
            .map((item) => ({
                itemName: item.name,
                display: itemDisplay(item),
                detail: item.perPack > 1 ? `${formatNumber(quantities[item.code])}${item.unit}` : "",
                amount: amountOf(item)
            }));
}

function totalAmount() {
    return selectedItems().reduce((sum, item) => sum + item.amount, 0);
}

function updateTotal() {
    const total = totalAmount();

    document.getElementById("formTotal").textContent = formatPrice(total);
    document.getElementById("submitBtn").disabled = total === 0;
}

function openConfirm() {
    const items = selectedItems();

    if (items.length === 0) {
        return;
    }

    document.getElementById("modalList").replaceChildren(...items.map(createModalRow));
    document.getElementById("modalTotal").textContent = formatPrice(totalAmount());
    document.getElementById("confirmModal").classList.add("open");
}

function createModalRow(item) {
    const row = document.createElement("div");
    row.className = "modal-row";

    const left = document.createElement("span");
    left.append(buildSpan("name", item.itemName));
    left.append(buildSpan("detail", item.display));

    row.append(left, buildSpan("price", formatPrice(item.amount)));
    return row;
}

function closeConfirm() {
    document.getElementById("confirmModal").classList.remove("open");
}

function confirmOrder() {
    const items = selectedItems();

    if (items.length === 0) {
        closeConfirm();
        return;
    }

    orders.unshift({
        createdAt: new Date().toISOString(),
        status: "REQUESTED",
        items,
        totalAmount: items.reduce((sum, item) => sum + item.amount, 0)
    });

    resetForm();
    closeConfirm();
    renderOrders();
}

function resetForm() {
    SUPPLY_ITEMS.forEach((item) => {
        quantities[item.code] = item.defaultQty;

        if (item.control === "stepper") {
            document.getElementById(`qty-${item.code}`).textContent = formatNumber(item.defaultQty);
            document.getElementById(`minus-${item.code}`).disabled = quantities[item.code] <= item.min;
        } else {
            document.getElementById(`qty-${item.code}`).value = item.defaultQty === 0 ? "" : formatNumber(item.defaultQty);
        }

        updateSub(item);
    });

    updateTotal();
}

function renderOrders() {
    const list = document.getElementById("orderList");
    const sub = document.getElementById("orderSub");

    sub.textContent = orders.length === 0 ? "" : `전체 ${orders.length}건`;

    if (orders.length === 0) {
        const empty = document.createElement("div");
        empty.className = "order-empty";
        empty.textContent = "아직 발주 내역이 없습니다";
        list.replaceChildren(empty);
        return;
    }

    const visible = ordersExpanded ? orders : orders.slice(0, VISIBLE_ORDERS);
    const nodes = visible.map(createOrderCard);

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

function createOrderCard(order) {
    const card = document.createElement("div");
    card.className = "order-card";

    const head = document.createElement("div");
    head.className = "order-head";

    const when = document.createElement("span");
    when.className = "order-when";
    when.textContent = formatWhen(order.createdAt);

    const status = document.createElement("span");
    status.className = `order-status ${order.status.toLowerCase()}`;
    status.textContent = STATUS_LABELS[order.status] ?? "-";

    head.append(when, status);

    const body = document.createElement("div");
    body.className = "order-body";

    const items = document.createElement("div");
    items.className = "order-items";
    items.textContent = order.items.map((item) => `${item.itemName} ${item.display}`).join(", ");

    const amount = document.createElement("span");
    amount.className = "order-amount";
    amount.textContent = formatPrice(order.totalAmount);

    body.append(items, amount);
    card.append(head, body);
    return card;
}

function loadDummyOrders() {
    orders = [
        {
            createdAt: new Date(Date.now() - 3600000).toISOString(),
            status: "DEPOSITED",
            items: [{itemName: "반죽믹스", display: "10포"}],
            totalAmount: 350000
        },
        {
            createdAt: new Date(Date.now() - 86400000).toISOString(),
            status: "SHIPPED",
            items: [
                {itemName: "반죽믹스", display: "20포"},
                {itemName: "비닐봉투", display: "6,000매 (1묶음)"}
            ],
            totalAmount: 852000
        }
    ];
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

document.addEventListener("DOMContentLoaded", () => {
    loadStore();
    renderForm();
    bindModal();
    resetForm();
    loadDummyOrders();
    renderOrders();
});