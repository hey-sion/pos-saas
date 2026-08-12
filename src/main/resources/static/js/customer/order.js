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

// PC(IFRAME)는 결제창이 닫히는 즉시 verify 해서 PG 상태 반영 전 PENDING 을 받는다.
// 모바일(REDIRECTION)은 복귀 왕복 동안 시간이 벌려 대체로 한 번에 확정된다.
const VERIFY_DEADLINE_MS = 3000;
const VERIFY_RETRY_DELAY_MS = 1000;

const SUBMIT_LABEL_DEFAULT = "주문하고 결제하기";
const SUBMIT_LABEL_VERIFYING = "결제 확인 중이에요";   // 실제로 확인이 도는 동안 (최대 VERIFY_DEADLINE_MS)
const SUBMIT_LABEL_NEEDS_CHECK = "결제 확인 필요";     // 닫히지 않은 주문 때문에 잠김

// 결제가 닫히지 않은 주문의 두 갈래. 화면과 잠금은 같고, 자동 재확인 대상만 UNSETTLED 로 한정한다.
const ORDER_STATE_UNSETTLED = "UNSETTLED";      // 결과를 못 받음 — 웹훅이 확정하면 풀린다
const ORDER_STATE_NEEDS_STAFF = "NEEDS_STAFF";  // 서버가 확정 불가로 판정 — 다시 물어도 안 바뀐다

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("submitOrderButton").addEventListener("click", submitOrder);
    renderOrderBanner();
    loadMenus();
    renderCart();
    resumePaymentResult();
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

    // 닫히지 않은 주문이 남아 있으면 결제를 막는다 — 이미 결제됐을 수 있어 다시 누르면 이중청구가 된다.
    const openOrder = unsettledOrder();
    const submitButton = document.getElementById("submitOrderButton");
    submitButton.disabled = items.length === 0 || state.submitting || openOrder !== null;
    submitButton.textContent = submitButtonLabel(openOrder);
}

function submitButtonLabel(openOrder) {
    if (state.submitting) {
        return SUBMIT_LABEL_VERIFYING;
    }
    return openOrder ? SUBMIT_LABEL_NEEDS_CHECK : SUBMIT_LABEL_DEFAULT;
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

        // 모바일(카카오페이 REDIRECTION)은 여기서 카카오로 이탈했다가 redirectUrl로 복귀 → 복귀 시 resumePaymentResult에서 verify.
        // PC(IFRAME)는 인라인 복귀라 아래 verify가 이어서 실행된다.
        // 복귀 경로는 현재 slug 기반 페이지 경로(location.pathname = /order/{slug})를 그대로 재사용한다.
        const redirectUrl = `${location.origin}${location.pathname}?verifyPaymentId=${created.payment.id}&orderNumber=${order.orderNumber}`;
        const pgOk = await requestPortOne(created.pg, redirectUrl);

        const verified = await verifyPaymentUntilSettled(created.payment.id);
        showVerifyResult(verified, {orderNumber: order.orderNumber, paymentId: created.payment.id, pgOk});
    } finally {
        state.submitting = false;
        renderCart();
    }
}

// 모바일 리다이렉트 복귀와 PC 인라인 결제가 공유하는 결과 처리.
// verified=null 은 마감시한까지 확정을 못 받은 미확정 — 결제가 됐을 수도 있는 상태다.
// pgOk=false면 PG 자체가 안 떴다는 신호 — 돈이 안 나갔으므로 미확정으로 묶지 않는다.
function showVerifyResult(verified, {orderNumber, paymentId, pgOk = true}) {
    if (verified?.status === "COMPLETED") {
        if (orderNumber) {
            saveLastOrder(orderNumber);
            renderOrderBanner();
        }
        showToast(
            orderNumber ? `결제가 완료됐어요\n주문번호 #${orderNumber}번` : "결제가 완료됐어요",
            {replayOnResume: true}
        );
        resetCart();
        return;
    }

    if (verified?.status === "FAILED") {
        clearUnsettledOrder(paymentId);
        renderOrderBanner();
        renderCart();
        showToast(verified.failReason ? `결제 실패: ${verified.failReason}` : "결제가 취소됐어요");
        return;
    }

    // AMOUNT_MISMATCH 는 PG 에 결제가 남아 있다는 서버 판정이라 pgOk 힌트보다 우선한다.
    if (verified?.status === "AMOUNT_MISMATCH") {
        keepOrderOpen(orderNumber, paymentId, ORDER_STATE_NEEDS_STAFF);
        showToast(staffCheckMessage(orderNumber), {replayOnResume: true});
        return;
    }

    if (!pgOk) {
        showToast("결제를 진행할 수 없어요. 직원에게 문의해주세요");
        return;
    }

    keepOrderOpen(orderNumber, paymentId, ORDER_STATE_UNSETTLED);
    showToast(staffCheckMessage(orderNumber), {replayOnResume: true});
}

// 주문번호는 결제 전에 이미 발급돼 있다. 손님과 직원을 잇는 유일한 열쇠라 화면에 남기고, 그동안 재결제를 막는다.
function keepOrderOpen(orderNumber, paymentId, state) {
    if (!orderNumber || !paymentId) {
        return;
    }

    saveLastOrder(orderNumber, {paymentId, state});
    renderOrderBanner();
    renderCart();
}

// 두 상태 모두 손님이 할 일은 같다 — 주문번호를 직원에게 알리는 것. 화면에서 저절로 풀리지도 않는다.
function staffCheckMessage(orderNumber) {
    return orderNumber
            ? `결제 확인이 필요해요\n주문번호 #${orderNumber}번을 직원에게 알려주세요`
            : "결제 확인이 필요해요. 직원에게 문의해주세요";
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
// 모바일은 redirect로 떠나가서 resolve가 안 되니 호출자가 결과를 보지 않음(resumePaymentResult 흐름).
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

// PENDING 도 HTTP 실패도 손님 입장에선 똑같이 "아직 모름" 이라 같은 재시도 대상으로 둔다.
// verify 는 PortOne 재조회 + 조건부 UPDATE 라 여러 번 불러도 한 번만 반영되고,
// 이미 확정된 결제는 PG 재조회 없이 DB 만 읽고 응답한다.
// 마감시한까지 확정을 못 받으면 null — 호출부가 미확정으로 처리한다.
async function verifyPaymentUntilSettled(paymentId) {
    const deadline = Date.now() + VERIFY_DEADLINE_MS;

    while (true) {
        try {
            const verified = await verifyPayment(paymentId);
            if (verified.status !== "PENDING") {
                return verified;
            }
        } catch {
            // 마감시한 안이면 다시 묻는다.
        }

        const remaining = deadline - Date.now();
        if (remaining <= 0) {
            return null;
        }
        await delay(Math.min(VERIFY_RETRY_DELAY_MS, remaining));
    }
}

function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// 페이지 진입 시 결제 결과를 화면에 반영하는 진입점.
// ① 모바일 카카오페이는 REDIRECTION이라 결제 후 redirectUrl(?verifyPaymentId=…)로 복귀한다.
// ② 복귀가 아니어도 미확정으로 남은 주문이 있으면 다시 묻는다 — 그 사이 웹훅이 서버에서 확정했을 수 있다.
async function resumePaymentResult() {
    const params = new URLSearchParams(location.search);
    const returnedPaymentId = params.get("verifyPaymentId");

    if (returnedPaymentId) {
        const orderNumber = params.get("orderNumber");
        history.replaceState(null, "", location.pathname); // 새로고침 시 재검증 방지

        const verified = await verifyPaymentUntilSettled(returnedPaymentId);
        showVerifyResult(verified, {orderNumber, paymentId: returnedPaymentId});
        return;
    }

    await recheckUnsettledOrder();
}

// 손님이 기다리는 상황이 아니라 한 번만 조용히 묻는다.
// 아직도 미확정이면 화면을 그대로 둔다 — 같은 안내를 재진입마다 반복하지 않는다.
// NEEDS_STAFF 는 서버가 이미 확정 불가로 판정한 상태라 다시 물어도 바뀌지 않으므로 건너뛴다.
async function recheckUnsettledOrder() {
    const unsettled = unsettledOrder();
    if (unsettled?.state !== ORDER_STATE_UNSETTLED) {
        return;
    }

    try {
        const verified = await verifyPayment(unsettled.paymentId);
        if (verified.status !== "PENDING") {
            showVerifyResult(verified, {orderNumber: unsettled.orderNumber, paymentId: unsettled.paymentId});
        }
    } catch {
        // 여전히 미확정 — 배너와 결제 잠금을 유지한다.
    }
}

function restorePageVisibility() {
    Array.from(document.body.children).forEach(el => el.style.visibility = "");
}

// 최근 주문을 localStorage에 저장 — 페이지 새로고침/재진입 시에도 자기 주문번호 확인 가능.
// 매장 분리: storeId까지 같이 저장해서 다른 매장 QR로 진입 시 보이지 않게.
function saveLastOrder(orderNumber, {paymentId = null, state = null} = {}) {
    try {
        localStorage.setItem(LAST_ORDER_KEY, JSON.stringify({
            storeId,
            orderNumber: Number(orderNumber),
            paymentId,
            state,
            ts: Date.now()
        }));
    } catch {
        // localStorage 접근 불가(시크릿 모드/용량초과 등) — 배너 미동작 허용.
    }
}

// 해당 결제의 미확정 기록만 지운다. 그 전에 성공한 주문의 배너까지 날리지 않기 위해 paymentId 로 대조한다.
function clearUnsettledOrder(paymentId) {
    const last = readLastOrder();
    if (!last?.state || last.paymentId !== paymentId) {
        return;
    }

    try {
        localStorage.removeItem(LAST_ORDER_KEY);
    } catch {
        // localStorage 접근 불가 허용
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

function unsettledOrder() {
    const last = readLastOrder();
    return last?.state ? last : null;
}

function renderOrderBanner() {
    const section = document.getElementById("orderBannerSection");
    const numberEl = document.getElementById("orderBannerNumber");
    const labelEl = document.getElementById("orderBannerLabel");
    const last = readLastOrder();
    if (!last) {
        section.hidden = true;
        return;
    }
    numberEl.textContent = String(last.orderNumber);
    labelEl.textContent = last.state ? "결제 확인 필요" : "내 주문번호";
    section.classList.toggle("attention", Boolean(last.state));
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
