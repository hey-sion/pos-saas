// 손님 QR 셀프주문 화면 스크립트.
// 공개 경로(세션/CSRF 없음)라 POS와 달리 CSRF 헤더를 붙이지 않는다.
// storeId는 페이지의 data-store-id에서 읽어 공개 메뉴/주문 API 호출에 쓴다.

const storeId = document.querySelector(".page").dataset.storeId;

const state = {
    menus: [],
    cart: new Map(), // menuId -> { menuId, name, price, quantity }
    submitting: false
};

// 결제 완료 후 상단 배너에서 주문번호 30분간 확인 가능
const ORDER_BANNER_TTL_MS = 30 * 60 * 1000;
const LAST_ORDER_KEY = "pos:lastOrder";
const PENDING_TOAST_KEY = "pos:pendingToast";
const PENDING_TOAST_TTL_MS = 2 * 60 * 1000;
const TOAST_DURATION_MS = 4000;

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("submitOrderButton").addEventListener("click", submitOrder);
    renderOrderBanner();
    loadMenus();
    renderCart();
    handlePaymentReturn();
});

window.addEventListener("pageshow", () => showPendingToast());
window.addEventListener("focus", () => showPendingToast());
document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        showPendingToast();
    }
});

async function loadMenus() {
    try {
        const response = await fetch(`/api/v1/customer/stores/${storeId}/menus`);
        if (!response.ok) {
            throw new Error("Failed to load menus");
        }
        state.menus = await response.json();
    } catch {
        state.menus = [];
    }
    renderMenus();
}

function renderMenus() {
    const list = document.getElementById("menuList");
    const empty = document.getElementById("emptyMenuMessage");

    if (state.menus.length === 0) {
        list.replaceChildren();
        empty.hidden = false;
        return;
    }

    empty.hidden = true;
    list.replaceChildren(...state.menus.map(createMenuItem));
}

function createMenuItem(menu) {
    const article = document.createElement("article");
    article.className = "menu-item";

    const info = document.createElement("div");
    info.className = "menu-info";
    const name = document.createElement("h3");
    name.className = "menu-name";
    name.textContent = menu.name;
    const price = document.createElement("p");
    price.className = "menu-price";
    price.textContent = formatPrice(menu.price);
    info.append(name, price);

    const addButton = document.createElement("button");
    addButton.className = "add-button";
    addButton.type = "button";
    addButton.textContent = "+";
    addButton.setAttribute("aria-label", `${menu.name} 담기`);
    addButton.addEventListener("click", () => addToCart(menu));

    article.append(info, addButton);
    return article;
}

function addToCart(menu) {
    const existing = state.cart.get(menu.id);
    if (existing) {
        existing.quantity += 1;
    } else {
        state.cart.set(menu.id, {menuId: menu.id, name: menu.name, price: menu.price, quantity: 1});
    }
    renderCart();
}

function removeFromCart(menuId) {
    state.cart.delete(menuId);
    renderCart();
}

function renderCart() {
    const items = [...state.cart.values()];
    const count = items.reduce((sum, item) => sum + item.quantity, 0);
    const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0);

    document.getElementById("cartCount").textContent = count;
    document.getElementById("cartTotal").textContent = formatPrice(total);
    document.getElementById("cartPreview").replaceChildren(...items.map(createCartChip));
    document.getElementById("submitOrderButton").disabled = items.length === 0 || state.submitting;
}

function createCartChip(item) {
    const chip = document.createElement("button");
    chip.className = "cart-chip";
    chip.type = "button";
    chip.setAttribute("aria-label", `${item.name} 빼기`);

    chip.append(document.createTextNode(`${item.name} `));
    const qty = document.createElement("span");
    qty.className = "qty";
    qty.textContent = item.quantity;
    const remove = document.createElement("span");
    remove.className = "chip-remove";
    remove.setAttribute("aria-hidden", "true");
    remove.textContent = "✕";
    chip.append(qty, remove);

    chip.addEventListener("click", () => removeFromCart(item.menuId));
    return chip;
}

// 주문 생성 + 결제를 한 단위로 묶은 진입점. 카카오페이 단일이라 수단 선택 모달 없이 바로 결제까지 진행한다.
// phase 분리: PG 호출 전(생성)과 PG SDK 실패는 "진행 불가"로 단정, verify HTTP 실패만 모호 처리(이중청구 오인 방지).
async function submitOrder() {
    if (state.cart.size === 0 || state.submitting) {
        return;
    }

    state.submitting = true;
    renderCart();

    try {
        let order, created;
        try {
            order = await createOrder();
            created = await createPayment(order.id);
        } catch {
            showToast("결제를 진행할 수 없어요. 직원에게 문의해주세요");
            return;
        }

        // 모바일(카카오페이 REDIRECTION)은 여기서 카카오로 이탈했다가 redirectUrl로 복귀 → 복귀 시 handlePaymentReturn에서 verify.
        // PC(IFRAME)는 인라인 복귀라 아래 verify가 이어서 실행된다.
        const redirectUrl = `${location.origin}/order/${storeId}?verifyPaymentId=${created.payment.id}&orderNumber=${order.orderNumber}`;
        const pgOk = await requestPortOne(created.pg, redirectUrl);

        try {
            const verified = await verifyPayment(created.payment.id);
            showVerifyResult(verified, order.orderNumber, pgOk);
        } catch {
            showToast(pgOk
                ? "결제 확인에 실패했어요. 직원에게 문의해주세요"
                : "결제를 진행할 수 없어요. 직원에게 문의해주세요");
        }
    } finally {
        state.submitting = false;
        renderCart();
    }
}

// 모바일 리다이렉트 복귀와 PC 인라인 결제가 공유하는 결과 처리.
// pgOk=false면 PG 자체가 안 떴다는 신호 — PENDING이어도 결제 진행 불가로 단정.
function showVerifyResult(verified, orderNumber, pgOk = true) {
    if (verified.status === "COMPLETED") {
        if (orderNumber) {
            saveLastOrder(orderNumber);
            renderOrderBanner();
        }
        showToast(
            orderNumber ? `결제가 완료됐어요\n주문번호 #${orderNumber}번` : "결제가 완료됐어요",
            {replayOnResume: true}
        );
        resetCart();
    } else if (verified.status === "FAILED") {
        showToast(verified.failReason ? `결제 실패: ${verified.failReason}` : "결제가 취소됐어요");
    } else {
        showToast(pgOk
            ? "결제 확인 중이에요. 잠시 후 다시 시도해주세요"
            : "결제를 진행할 수 없어요. 직원에게 문의해주세요");
    }
}

async function createOrder() {
    const payload = {
        items: [...state.cart.values()].map(item => ({menuId: item.menuId, quantity: item.quantity}))
    };

    const response = await fetch(`/api/v1/customer/stores/${storeId}/orders`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    });

    if (response.status !== 201) {
        throw new Error("Failed to create order");
    }

    return response.json();
}

// 손님 셀프결제는 카카오페이로 고정 — 결제 수단/금액/storeId를 클라가 보내지 않고 서버가 order에서 파생한다.
async function createPayment(orderId) {
    const response = await fetch(`/api/v1/customer/orders/${orderId}/payments`, {method: "POST"});

    if (response.status !== 201) {
        throw new Error("Failed to create payment");
    }

    return response.json();
}

// PG 호출 성공 여부를 반환 — 결제창이 못 떴거나 SDK가 code 달린 결과 돌려주면 false.
// 모바일은 redirect로 떠나가서 resolve가 안 되니 호출자가 결과를 보지 않음(handlePaymentReturn 흐름).
async function requestPortOne(pg, redirectUrl) {
    if (typeof PortOne === "undefined" || !PortOne.requestPayment) {
        return false;
    }

    // 결제창 호출 시점의 body 자식만 hide(PortOne wrapper는 호출 후 추가되어 영향 없음).
    // 부모 페이지 paint cost를 줄여 카카오페이 결제창 응답성 개선.
    const childrenBefore = Array.from(document.body.children);
    childrenBefore.forEach(el => el.style.visibility = "hidden");

    try {
        const result = await PortOne.requestPayment({
            storeId: pg.storeId,
            channelKey: pg.channelKey,
            paymentId: pg.paymentId,
            orderName: pg.orderName,
            totalAmount: pg.totalAmount,
            currency: pg.currency,
            payMethod: pg.payMethod,
            easyPay: pg.easyPay,
            // 카카오페이: PC=IFRAME(인라인 복귀), 모바일=REDIRECTION(카카오 앱/페이지로 이동 후 redirectUrl로 복귀). 그 외 모드는 에러.
            windowType: {pc: "IFRAME", mobile: "REDIRECTION"},
            redirectUrl
        });
        // PortOne v2: 에러 시 result.code 포함된 객체로 resolve.
        return !(result && result.code);
    } catch {
        // SDK 예외는 단정하지 않고 후속 verify(백엔드 단건 조회)로 권위있는 상태를 확인한다.
        // false 반환은 PENDING/verify 실패 시 메시지 분기용 힌트일 뿐, verify의 COMPLETED/FAILED 판정은 그대로 우선.
        return false;
    } finally {
        restorePageVisibility();
    }
}

async function verifyPayment(paymentId) {
    const response = await fetch(`/api/v1/customer/payments/${paymentId}/verify`, {method: "POST"});

    if (!response.ok) {
        throw new Error("Failed to verify payment");
    }

    return response.json();
}

// 모바일 카카오페이는 REDIRECTION이라 결제 후 redirectUrl(?verifyPaymentId=…)로 복귀한다.
// 그 시점에 우리 결제 ID로 서버 verify(PortOne 재조회 = 권위있는 상태)를 호출해 결과를 보여준다.
async function handlePaymentReturn() {
    const params = new URLSearchParams(location.search);
    const verifyPaymentId = params.get("verifyPaymentId");
    if (!verifyPaymentId) {
        return;
    }

    const orderNumber = params.get("orderNumber");
    history.replaceState(null, "", location.pathname); // 새로고침 시 재검증 방지

    try {
        const verified = await verifyPayment(verifyPaymentId);
        showVerifyResult(verified, orderNumber);
    } catch {
        showToast("결제 확인에 실패했어요. 직원에게 문의해주세요");
    }
}

function restorePageVisibility() {
    Array.from(document.body.children).forEach(el => el.style.visibility = "");
}

// 최근 주문을 localStorage에 저장 — 페이지 새로고침/재진입 시에도 자기 주문번호 확인 가능.
// 매장 분리: storeId까지 같이 저장해서 다른 매장 QR로 진입 시 보이지 않게.
function saveLastOrder(orderNumber) {
    try {
        localStorage.setItem(LAST_ORDER_KEY, JSON.stringify({
            storeId,
            orderNumber: Number(orderNumber),
            ts: Date.now()
        }));
    } catch {
        // localStorage 접근 불가(시크릿 모드/용량초과 등) — 배너 미동작 허용.
    }
}

function savePendingToast(message) {
    try {
        sessionStorage.setItem(PENDING_TOAST_KEY, JSON.stringify({
            storeId,
            message,
            ts: Date.now()
        }));
    } catch {
        // sessionStorage 접근 불가 시 현재 화면 토스트만 사용
    }
}

function readPendingToast() {
    try {
        const raw = sessionStorage.getItem(PENDING_TOAST_KEY);
        if (!raw) {
            return null;
        }

        const parsed = JSON.parse(raw);
        if (!parsed || parsed.storeId !== storeId || !parsed.message) {
            sessionStorage.removeItem(PENDING_TOAST_KEY);
            return null;
        }
        if (Date.now() - parsed.ts > PENDING_TOAST_TTL_MS) {
            sessionStorage.removeItem(PENDING_TOAST_KEY);
            return null;
        }
        return parsed;
    } catch {
        return null;
    }
}

function clearPendingToast() {
    try {
        sessionStorage.removeItem(PENDING_TOAST_KEY);
    } catch {
        // sessionStorage 접근 불가 허용
    }
}

function readLastOrder() {
    try {
        const raw = localStorage.getItem(LAST_ORDER_KEY);
        if (!raw) {
            return null;
        }
        const parsed = JSON.parse(raw);
        if (!parsed || parsed.storeId !== storeId) {
            return null;
        }
        if (Date.now() - parsed.ts > ORDER_BANNER_TTL_MS) {
            localStorage.removeItem(LAST_ORDER_KEY);
            return null;
        }
        return parsed;
    } catch {
        return null;
    }
}

function renderOrderBanner() {
    const section = document.getElementById("orderBannerSection");
    const numberEl = document.getElementById("orderBannerNumber");
    const last = readLastOrder();
    if (!last) {
        section.hidden = true;
        return;
    }
    numberEl.textContent = String(last.orderNumber);
    section.hidden = false;
}

function resetCart() {
    state.cart.clear();
    renderCart();
}

function formatPrice(amount) {
    return `${Number(amount).toLocaleString("ko-KR")}원`;
}

function showPendingToast() {
    restorePageVisibility();

    if (document.visibilityState !== "visible") {
        return;
    }

    const pending = readPendingToast();
    if (!pending) {
        return;
    }

    clearPendingToast();
    displayToast(pending.message);
}

function showToast(message, options = {}) {
    if (options.replayOnResume || document.visibilityState !== "visible") {
        savePendingToast(message);
    }

    if (document.visibilityState !== "visible") {
        return;
    }

    displayToast(message);

    if (options.replayOnResume) {
        setTimeout(() => {
            if (document.visibilityState === "visible" && document.hasFocus()) {
                clearPendingToast();
            }
        }, TOAST_DURATION_MS + 100);
    }
}

function displayToast(message) {
    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.classList.remove("show");
    void toast.offsetWidth;
    toast.classList.add("show");
    setTimeout(() => toast.classList.remove("show"), TOAST_DURATION_MS);
}
