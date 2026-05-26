const STORE_ID = 1;

const formatPrice = (price) => new Intl.NumberFormat("ko-KR").format(price) + "원";

function todayIsoDate() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function initializeSalesDate() {
    const salesDate = document.getElementById("salesDate");

    salesDate.value = todayIsoDate();
    salesDate.addEventListener("change", loadDailySummary);
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

async function loadDailySummary() {
    const date = document.getElementById("salesDate").value;

    if (!date) {
        renderDailySummary({salesAmount: 0, salesOrderCount: 0, totalOrderCount: 0, orders: []});
        return;
    }

    try {
        const response = await fetch(`/api/v1/orders/daily-summary?storeId=${STORE_ID}&date=${date}`);

        if (!response.ok) {
            throw new Error("Failed to load daily summary");
        }

        renderDailySummary(await response.json());
    } catch {
        renderDailySummary({salesAmount: 0, salesOrderCount: 0, totalOrderCount: 0, orders: []});
    }
}

function renderDailySummary(summary) {
    document.getElementById("dailyTotal").textContent = formatPrice(summary.salesAmount ?? 0);
    document.getElementById("dailyCount").textContent =
            `매출 인정 ${summary.salesOrderCount ?? 0}건 / 전체 주문 ${summary.totalOrderCount ?? 0}건`;
    renderOrders(summary.orders ?? []);
}

function renderOrders(orders) {
    const orderHistory = document.getElementById("orderHistory");

    if (orders.length === 0) {
        const empty = document.createElement("div");
        empty.className = "history-empty";
        empty.textContent = "주문 내역이 없습니다";
        orderHistory.replaceChildren(empty);
        return;
    }

    orderHistory.replaceChildren(...orders.map(createOrderHistoryRow));
}

function createOrderHistoryRow(order) {
    const row = document.createElement("div");
    row.className = "history-row";

    const left = document.createElement("div");
    left.className = "history-left";

    const number = document.createElement("span");
    number.className = "history-num";
    number.textContent = `#${order.orderNumber}`;

    const menu = document.createElement("span");
    menu.className = "history-menu";
    menu.textContent = createOrderItemSummary(order.items);

    left.append(number, menu);

    const right = document.createElement("div");
    right.className = "history-right";

    const amount = document.createElement("span");
    amount.className = "history-amount";
    amount.textContent = formatPrice(order.totalAmount ?? 0);

    const payment = document.createElement("span");
    payment.className = "history-pay";
    payment.textContent = formatPaymentMethod(order.paymentMethod);

    const status = document.createElement("span");
    status.className = `history-status ${order.status?.toLowerCase() ?? ""}`;
    status.textContent = formatOrderStatus(order.status);

    right.append(amount, payment, status);
    row.append(left, right);
    return row;
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

function formatOrderStatus(status) {
    const labels = {
        RECEIVED: "주문 접수",
        DELIVERED: "전달 완료",
        CANCELLED: "주문 취소"
    };
    return labels[status] ?? "-";
}

document.addEventListener("DOMContentLoaded", () => {
    initializeSalesDate();
    loadStore();
    loadDailySummary();
});
