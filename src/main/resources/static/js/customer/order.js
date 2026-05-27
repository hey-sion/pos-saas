// 손님 QR 셀프주문 화면 스크립트.
// 공개 경로(세션/CSRF 없음)라 POS와 달리 CSRF 헤더를 붙이지 않는다.
// storeId는 페이지의 data-store-id에서 읽어 공개 메뉴/주문 API 호출에 쓴다.

const storeId = document.querySelector(".page").dataset.storeId;

const state = {
    menus: [],
    cart: new Map(), // menuId -> { menuId, name, price, quantity }
    submitting: false
};

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("submitOrderButton").addEventListener("click", submitOrder);
    loadMenus();
    renderCart();
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

// 주문 생성 + 결제를 한 단위로 묶은 진입점. 지금은 버튼이 직접 호출하지만,
// 추후 "주문 확인하기" 모달을 거치도록 바뀌어도 트리거만 교체하고 이 함수는 그대로 재사용한다.
async function submitOrder() {
    if (state.cart.size === 0 || state.submitting) {
        return;
    }

    state.submitting = true;
    renderCart();

    try {
        const order = await createOrder();
        // TODO(Phase 5): 여기서 포트원 통합결제 창을 띄워 결제까지 진행한다. 지금은 stub.
        showToast(`주문번호 ${order.orderNumber}번이 접수됐어요. 결제 기능은 준비 중입니다`);
        resetCart();
    } catch {
        showToast("주문 처리에 실패했습니다. 잠시 후 다시 시도해주세요");
    } finally {
        state.submitting = false;
        renderCart();
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

function resetCart() {
    state.cart.clear();
    renderCart();
}

function formatPrice(amount) {
    return `${Number(amount).toLocaleString("ko-KR")}원`;
}

function showToast(message) {
    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.classList.remove("show");
    void toast.offsetWidth;
    toast.classList.add("show");
    setTimeout(() => toast.classList.remove("show"), 2400);
}
