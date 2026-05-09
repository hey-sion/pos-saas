const STORE_ID = 1;

const formatPrice = (price) => new Intl.NumberFormat("ko-KR").format(price) + "원";

function loadBusinessDate() {
    const today = new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).format(new Date());

    document.getElementById("businessDate").textContent = `영업일: ${today}`;
}

function todayIsoDate() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function initializeSalesDate() {
    document.getElementById("salesDate").value = todayIsoDate();
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

function renderEmptyManagementState() {
    document.getElementById("dailyTotal").textContent = formatPrice(0);
    document.getElementById("dailyCount").textContent = "매출 인정 0건 / 전체 주문 0건";
}

document.addEventListener("DOMContentLoaded", () => {
    loadBusinessDate();
    initializeSalesDate();
    loadStore();
    renderEmptyManagementState();
});