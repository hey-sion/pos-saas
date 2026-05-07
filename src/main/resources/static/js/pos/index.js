const STORE_ID = 1;

const state = {
    menus: [],
    cart: [],
    waitingOrders: []
};

const formatPrice = (price) => new Intl.NumberFormat("ko-KR").format(price) + "원";

function loadBusinessDate() {
    const today = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).format(new Date());

    document.getElementById("businessDate").textContent = `영업일: ${today}`;
}

async function loadStore() {
    const storeName = document.getElementById("storeName");

    try {
        const response = await fetch(`/api/stores/${STORE_ID}`);

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

async function loadMenus() {
    const menuGrid = document.getElementById("menuGrid");
    const emptyMessage = document.getElementById("emptyMessage");

    try {
        const response = await fetch(`/api/menus?storeId=${STORE_ID}`);

        if (!response.ok) {
            throw new Error("Failed to load menus");
        }

        state.menus = await response.json();
        menuGrid.replaceChildren(...state.menus.map(createMenuButton));
        emptyMessage.hidden = state.menus.length > 0;
    } catch {
        state.menus = [];
        menuGrid.replaceChildren();
        emptyMessage.hidden = false;
    }
}

function createMenuButton(menu) {
    const button = document.createElement("button");
    button.className = "menu-btn";
    button.type = "button";
    button.addEventListener("click", () => addMenuToCart(menu));

    const name = document.createElement("span");
    name.className = "name";
    name.textContent = menu.name;

    const price = document.createElement("span");
    price.className = "price";
    price.textContent = formatPrice(menu.price);

    button.append(name, price);
    return button;
}

function addMenuToCart(menu) {
    const item = state.cart.find((cartItem) => cartItem.menuId === menu.id);

    if (item) {
        item.quantity += 1;
    } else {
        state.cart.push({
            menuId: menu.id,
            name: menu.name,
            price: menu.price,
            quantity: 1
        });
    }

    renderCart();
}

function changeQuantity(menuId, delta) {
    const item = state.cart.find((cartItem) => cartItem.menuId === menuId);

    if (!item) {
        return;
    }

    item.quantity += delta;

    if (item.quantity <= 0) {
        state.cart = state.cart.filter((cartItem) => cartItem.menuId !== menuId);
    }

    renderCart();
}

function clearCart() {
    state.cart = [];
    renderCart();
}

function getCartTotal() {
    return state.cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
}

function renderCart() {
    const orderItems = document.getElementById("orderItems");
    const orderTotal = document.getElementById("orderTotal");
    const hasItems = state.cart.length > 0;

    orderTotal.textContent = formatPrice(getCartTotal());
    document.getElementById("btnCash").disabled = !hasItems;
    document.getElementById("btnCard").disabled = !hasItems;
    document.getElementById("btnQr").disabled = !hasItems;

    if (!hasItems) {
        const empty = document.createElement("div");
        empty.className = "order-empty";
        empty.textContent = "메뉴를 선택해주세요";
        orderItems.replaceChildren(empty);
        return;
    }

    orderItems.replaceChildren(...state.cart.map(createCartItemRow));
}

function createCartItemRow(item) {
    const row = document.createElement("div");
    row.className = "order-item";

    const left = document.createElement("div");
    left.className = "order-item-left";

    const name = document.createElement("span");
    name.textContent = item.name;

    const quantity = document.createElement("div");
    quantity.className = "order-item-qty";

    const decrease = document.createElement("button");
    decrease.className = "qty-btn";
    decrease.type = "button";
    decrease.textContent = "-";
    decrease.addEventListener("click", () => changeQuantity(item.menuId, -1));

    const value = document.createElement("span");
    value.className = "qty-val";
    value.textContent = item.quantity;

    const increase = document.createElement("button");
    increase.className = "qty-btn";
    increase.type = "button";
    increase.textContent = "+";
    increase.addEventListener("click", () => changeQuantity(item.menuId, 1));

    quantity.append(decrease, value, increase);
    left.append(name, quantity);

    const price = document.createElement("span");
    price.className = "order-item-price";
    price.textContent = formatPrice(item.price * item.quantity);

    row.append(left, price);
    return row;
}

async function submitOfflinePayment(method) {
    if (state.cart.length === 0) {
        return;
    }

    showToast("주문 생성 API 연결 대기");

    // TODO: POST /api/orders 연동 후 서버 응답 기준으로 cart 리셋 및 대기열 갱신
    // await createOrder({method, channel: "OFFLINE"});
    // clearCart();
    // await refreshWaitingOrders();
}

async function submitEasyPay() {
    if (state.cart.length === 0) {
        return;
    }

    openQrModal(getCartTotal());

    // TODO: POST /api/orders/simple-pay 연동 후 QR URL을 표시하고 대기열을 갱신
    // const qr = await createEasyPayOrder();
    // updateQrModal(qr);
    // clearCart();
    // await refreshWaitingOrders();
}

function openQrModal(amount) {
    document.getElementById("qrAmount").textContent = formatPrice(amount);
    document.getElementById("qrHint").textContent = "QR 생성 대기";
    document.getElementById("qrOverlay").classList.add("show");
    document.getElementById("qrOverlay").setAttribute("aria-hidden", "false");
}

function closeQrModal() {
    document.getElementById("qrOverlay").classList.remove("show");
    document.getElementById("qrOverlay").setAttribute("aria-hidden", "true");
}

async function refreshWaitingOrders() {
    try {
        const response = await fetch(`/api/orders/waiting?storeId=${STORE_ID}`);

        if (!response.ok) {
            throw new Error("Failed to load waiting orders");
        }

        state.waitingOrders = await response.json();
    } catch {
        state.waitingOrders = [];
    }

    renderWaitingOrders();
}

function renderWaitingOrders() {
    const slots = document.getElementById("slots");
    const emptySlots = document.getElementById("emptySlots");

    emptySlots.style.display = state.waitingOrders.length > 0 ? "none" : "block";
    slots.replaceChildren(...state.waitingOrders.map(createWaitingOrderSlot));
}

function createWaitingOrderSlot(order) {
    const slot = document.createElement("div");
    const isPaid = order.status === "PAID";
    slot.className = `slot ${isPaid ? "paid" : "pending"}`;

    const top = document.createElement("div");
    top.className = "slot-top";

    const number = document.createElement("span");
    number.className = "slot-num";
    number.textContent = `#${order.orderNumber}`;

    const badge = document.createElement("span");
    badge.className = "slot-badge";
    badge.textContent = isPaid ? "결제 완료" : "결제 대기";

    top.append(number, badge);

    const mid = document.createElement("div");
    mid.className = "slot-mid";

    const menu = document.createElement("span");
    menu.className = "slot-menu";
    menu.textContent = order.summary ?? "-";

    const payType = document.createElement("span");
    payType.className = "slot-pay-type";
    payType.textContent = order.paymentMethod ?? "-";

    mid.append(menu, payType);

    const price = document.createElement("div");
    price.className = "slot-price";
    price.textContent = formatPrice(order.totalAmount ?? 0);

    slot.append(top, mid, price);
    return slot;
}

function showToast(message) {
    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.classList.remove("show");
    void toast.offsetWidth;
    toast.classList.add("show");
    setTimeout(() => toast.classList.remove("show"), 2000);
}

function switchTab(tab) {
    document.querySelectorAll(".tab").forEach((button) => {
        button.classList.toggle("active", button.dataset.tab === tab);
    });

    document.getElementById("posPane").classList.toggle("hidden", tab !== "menu");
    document.getElementById("queuePane").classList.toggle("hidden", tab !== "menu");
    document.getElementById("mgmtPane").classList.toggle("active", tab === "mgmt");
}

function bindEvents() {
    document.querySelectorAll(".tab").forEach((button) => {
        button.addEventListener("click", () => switchTab(button.dataset.tab));
    });

    document.querySelector(".clear-btn").addEventListener("click", clearCart);
    document.getElementById("btnCash").addEventListener("click", () => submitOfflinePayment("CASH"));
    document.getElementById("btnCard").addEventListener("click", () => submitOfflinePayment("CARD"));
    document.getElementById("btnQr").addEventListener("click", submitEasyPay);
    document.getElementById("qrCloseButton").addEventListener("click", closeQrModal);
}

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    loadBusinessDate();
    loadStore();
    loadMenus();
    renderCart();
    refreshWaitingOrders();
});
