const STORE_ID = 1;

const PAYMENT_OPTIONS = {
    CASH: {
        method: "CASH",
        provider: null,
        label: "현금"
    },
    CARD: {
        method: "CARD",
        provider: null,
        label: "카드 단말기"
    },
    KAKAO_PAY: {
        method: "EASY_PAY",
        provider: "KAKAO_PAY",
        label: "카카오페이"
    }
};

const state = {
    menus: [],
    cart: [],
    waitingOrders: [],
    orderSubmitting: false,
    selectedWaitingOrder: null,
    selectedEasyPayOption: null
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
    updatePaymentButtons();

    if (!hasItems) {
        const empty = document.createElement("div");
        empty.className = "order-empty";
        empty.textContent = "메뉴를 선택해주세요";
        orderItems.replaceChildren(empty);
        return;
    }

    orderItems.replaceChildren(...state.cart.map(createCartItemRow));
}

function updatePaymentButtons() {
    const disabled = state.cart.length === 0 || state.orderSubmitting;

    document.getElementById("btnCash").disabled = disabled;
    document.getElementById("btnCard").disabled = disabled;
    document.getElementById("btnKakaoPay").disabled = disabled;
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

async function submitCompletedPayment(paymentOption) {
    if (state.cart.length === 0) {
        return;
    }

    state.orderSubmitting = true;
    updatePaymentButtons();

    try {
        await createOrder(paymentOption);
        clearCart();
        await refreshWaitingOrders();
        showToast("주문이 생성되었습니다");
    } catch {
        showToast("주문 생성에 실패했습니다");
    } finally {
        state.orderSubmitting = false;
        updatePaymentButtons();
    }
}

async function createOrder(paymentOption) {
    const payload = {
        storeId: STORE_ID,
        items: state.cart.map((item) => ({
            menuId: item.menuId,
            quantity: item.quantity
        })),
        method: paymentOption.method
    };

    if (paymentOption.provider) {
        payload.provider = paymentOption.provider;
    }

    const response = await fetch("/api/v1/orders", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
    });

    if (response.status !== 201) {
        throw new Error("Failed to create order");
    }

    return response.json();
}

function submitEasyPay(paymentOption) {
    if (state.cart.length === 0) {
        return;
    }

    openQrModal(paymentOption);
}

async function acceptEasyPayPayment() {
    if (!state.selectedEasyPayOption || state.cart.length === 0 || state.orderSubmitting) {
        return;
    }

    state.orderSubmitting = true;
    updatePaymentButtons();
    document.getElementById("qrAcceptButton").disabled = true;

    try {
        await createOrder(state.selectedEasyPayOption);
        closeQrModal(true);
        clearCart();
        await refreshWaitingOrders();
        showToast("결제 대기 주문이 생성되었습니다");
    } catch {
        showToast("결제 접수에 실패했습니다");
    } finally {
        state.orderSubmitting = false;
        updatePaymentButtons();
        document.getElementById("qrAcceptButton").disabled = false;
    }
}

function openQrModal(paymentOption) {
    state.selectedEasyPayOption = paymentOption;
    document.getElementById("qrTitle").textContent = `${paymentOption.label} 결제`;
    document.getElementById("qrAmount").textContent = formatPrice(getCartTotal());
    document.getElementById("qrHint").textContent = "고객이 QR을 스캔하면 결제 접수를 눌러주세요";
    document.getElementById("qrAcceptButton").disabled = false;
    document.getElementById("qrOverlay").classList.add("show");
    document.getElementById("qrOverlay").setAttribute("aria-hidden", "false");
}

function closeQrModal(force = false) {
    if (force !== true && state.orderSubmitting) {
        return;
    }

    state.selectedEasyPayOption = null;
    document.getElementById("qrOverlay").classList.remove("show");
    document.getElementById("qrOverlay").setAttribute("aria-hidden", "true");
}

async function refreshWaitingOrders() {
    try {
        const response = await fetch(`/api/v1/orders/waiting?storeId=${STORE_ID}`);

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
    slot.tabIndex = 0;
    slot.addEventListener("click", () => openOrderDetail(order));
    slot.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openOrderDetail(order);
        }
    });

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
    menu.textContent = createOrderItemSummary(order.items);

    const payType = document.createElement("span");
    payType.className = "slot-pay-type";
    payType.textContent = formatPaymentMethod(order.paymentMethod);

    mid.append(menu, payType);

    const price = document.createElement("div");
    price.className = "slot-price";
    price.textContent = formatPrice(order.totalAmount ?? 0);

    slot.append(top, mid, price);
    return slot;
}

function openOrderDetail(order) {
    state.selectedWaitingOrder = order;
    document.getElementById("orderDetailTitle").textContent = `#${order.orderNumber} 주문 확인`;
    document.getElementById("orderDetailStatus").textContent = order.status === "PAID" ? "결제 완료" : "결제 대기";
    document.getElementById("orderDetailPayment").textContent = formatPaymentMethod(order.paymentMethod);
    document.getElementById("orderDetailTotal").textContent = formatPrice(order.totalAmount ?? 0);
    document.getElementById("orderDetailItems").replaceChildren(...createOrderDetailItemRows(order.items));
    hideCancelConfirmation();
    document.getElementById("orderDetailOverlay").classList.add("show");
    document.getElementById("orderDetailOverlay").setAttribute("aria-hidden", "false");
}

function closeOrderDetail() {
    state.selectedWaitingOrder = null;
    document.getElementById("orderDetailOverlay").classList.remove("show");
    document.getElementById("orderDetailOverlay").setAttribute("aria-hidden", "true");
}

function showCancelConfirmation() {
    document.getElementById("orderCancelConfirm").classList.add("show");
}

function hideCancelConfirmation() {
    document.getElementById("orderCancelConfirm").classList.remove("show");
}

async function completeSelectedWaitingOrder() {
    if (!state.selectedWaitingOrder) {
        return;
    }

    try {
        await updateOrderStatus(state.selectedWaitingOrder.id, "DELIVERED");
        closeOrderDetail();
        await refreshWaitingOrders();
        showToast("전달완료 처리되었습니다");
    } catch {
        showToast("전달완료 처리에 실패했습니다");
    }
}

async function cancelSelectedWaitingOrder() {
    if (!state.selectedWaitingOrder) {
        return;
    }

    try {
        await updateOrderStatus(state.selectedWaitingOrder.id, "CANCELLED");
        closeOrderDetail();
        await refreshWaitingOrders();
        showToast("주문이 취소되었습니다");
    } catch {
        showToast("주문 취소에 실패했습니다");
    }
}

async function updateOrderStatus(orderId, status) {
    const response = await fetch(`/api/v1/orders/${orderId}/status`, {
        method: "PATCH",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({status})
    });

    if (response.status !== 204) {
        throw new Error("Failed to update order status");
    }
}

function createOrderDetailItemRows(items) {
    if (!items || items.length === 0) {
        const empty = document.createElement("div");
        empty.className = "order-empty";
        empty.textContent = "주문 항목이 없습니다";
        return [empty];
    }

    return items.map((item) => {
        const row = document.createElement("div");
        row.className = "order-detail-item";

        const name = document.createElement("span");
        name.className = "order-detail-item-name";
        name.textContent = item.menuName;

        const quantity = document.createElement("span");
        quantity.className = "order-detail-item-qty";
        quantity.textContent = `${item.quantity}개`;

        row.append(name, quantity);
        return row;
    });
}

function createOrderItemSummary(items) {
    if (!items || items.length === 0) {
        return "-";
    }

    const first = items[0];
    if (items.length === 1) {
        return `${first.menuName} ${first.quantity}개`;
    }
    return `${first.menuName} 외 ${items.length - 1}건`;
}

function formatPaymentMethod(method) {
    const labels = {
        CASH: "현금",
        CARD: "카드",
        EASY_PAY: "간편결제"
    };
    return labels[method] ?? "-";
}

function showToast(message) {
    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.classList.remove("show");
    void toast.offsetWidth;
    toast.classList.add("show");
    setTimeout(() => toast.classList.remove("show"), 2000);
}

function bindEvents() {
    document.querySelector(".clear-btn").addEventListener("click", clearCart);
    document.getElementById("btnCash").addEventListener("click", () => submitCompletedPayment(PAYMENT_OPTIONS.CASH));
    document.getElementById("btnCard").addEventListener("click", () => submitCompletedPayment(PAYMENT_OPTIONS.CARD));
    document.getElementById("btnKakaoPay").addEventListener("click", () => submitEasyPay(PAYMENT_OPTIONS.KAKAO_PAY));
    document.getElementById("qrAcceptButton").addEventListener("click", acceptEasyPayPayment);
    document.getElementById("qrCloseButton").addEventListener("click", closeQrModal);
    document.getElementById("orderDetailCloseButton").addEventListener("click", closeOrderDetail);
    document.getElementById("orderDeliverButton").addEventListener("click", completeSelectedWaitingOrder);
    document.getElementById("orderCancelButton").addEventListener("click", showCancelConfirmation);
    document.getElementById("orderCancelDismissButton").addEventListener("click", hideCancelConfirmation);
    document.getElementById("orderCancelConfirmButton").addEventListener("click", cancelSelectedWaitingOrder);
}

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    loadBusinessDate();
    loadStore();
    loadMenus();
    renderCart();
    refreshWaitingOrders();
});