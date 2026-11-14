const API_BASE = window.location.port === '80' || window.location.port === ''
    ? '/api'
    : 'http://127.0.0.1:8080/api';
    
// 1. 页面加载时检查本地是否已有登录令牌
document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('mall_token');
    if (token) {
        showMainApp();
    } else {
        showLoginView();
    }
});

function showLoginView() {
    document.getElementById('loginView').classList.remove('hidden');
    document.getElementById('mainApp').classList.add('hidden');
}

function showMainApp() {
    document.getElementById('loginView').classList.add('hidden');
    document.getElementById('mainApp').classList.remove('hidden');
    document.getElementById('adminNickname').innerText = '👤 ' + (localStorage.getItem('mall_nickname') || '管理员');
    initDashboard();
}

// 2. 登录与退出逻辑
async function handleLogin() {
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value.trim();
    if (!username || !password) return alert('请输入账号和密码');

    const res = await fetch(API_BASE + '/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    const json = await res.json();
    if (json.code === 200) {
        localStorage.setItem('mall_token', json.data.token);
        localStorage.setItem('mall_nickname', json.data.nickname);
        showMainApp();
    } else {
        alert(json.msg);
    }
}

async function handleLogout() {
    await authFetch(API_BASE + '/logout', { method: 'POST' });
    localStorage.removeItem('mall_token');
    localStorage.removeItem('mall_nickname');
    showLoginView();
}

// 3. 带 Token 认证的全局请求封装（替代原有的 fetch）
async function authFetch(url, options = {}) {
    const token = localStorage.getItem('mall_token') || '';
    options.headers = Object.assign({}, options.headers, {
        'Authorization': token
    });
    const res = await fetch(url, options);
    const cloned = res.clone();
    try {
        const data = await cloned.json();
        if (data.code === 401) {
            localStorage.removeItem('mall_token');
            alert('登录已失效，请重新登录！');
            showLoginView();
            throw new Error('Unauthorized');
        }
    } catch (e) {
        if (e.message === 'Unauthorized') throw e;
    }
    return res;
}

let currentPage = 1;
const pageSize = 10;
let categoriesCache = [];

document.addEventListener('DOMContentLoaded', () => {
    initDashboard();
});

async function initDashboard() {
    await loadCategories();
    await loadLogs();
    await loadProducts(1);
}

// 选项卡视图切换
function switchTab(tab) {
    document.getElementById('tabBtnProducts').classList.toggle('active', tab === 'products');
    document.getElementById('tabBtnCategories').classList.toggle('active', tab === 'categories');
    document.getElementById('viewProducts').classList.toggle('hidden', tab !== 'products');
    document.getElementById('viewCategories').classList.toggle('hidden', tab !== 'categories');
}

// --- 1. 爬虫控制与日志 ---
async function triggerCrawl() {
    const pages = document.getElementById('crawlPages').value;
    const btn = document.getElementById('btnCrawl');
    btn.disabled = true;
    btn.innerText = '⏳ 正在合法采集中...';

    try {
        const res = await authFetch(`${API_BASE}/crawler/start?pages=${pages}`, { method: 'POST' });
        const json = await res.json();
        if (json.code === 200) {
            alert(`采集完成！新增入库 ${json.data.successCount} 条商品，耗时 ${json.data.costTimeMs} ms`);
            initDashboard();
        } else {
            alert('采集异常：' + json.msg);
        }
    } catch (e) {
        alert('网络请求失败，请确认 Spring Boot 后端服务已启动');
    } finally {
        btn.disabled = false;
        btn.innerText = '🚀 启动合规爬虫';
    }
}

async function loadLogs() {
    const res = await authFetch(`${API_BASE}/crawler/logs`);
    const json = await res.json();
    const ul = document.getElementById('logList');
    if (json.data && json.data.length > 0) {
        ul.innerHTML = json.data.map(l =>
            `<li>[${l.createTime.replace('T', ' ')}] 目标: ${l.targetUrl} | 新增: <b>${l.successCount}</b>条 | 耗时: ${l.costTimeMs}ms | 状态: ${l.status}</li>`
        ).join('');
    } else {
        ul.innerHTML = '<li>暂无采集记录，请点击右上角启动爬虫。</li>';
    }
}

// --- 2. 分类管理 (CRUD) ---
async function loadCategories() {
    const res = await authFetch(`${API_BASE}/categories`);
    const json = await res.json();
    categoriesCache = json.data || [];
    document.getElementById('statCategories').innerText = categoriesCache.length;

    // 同步更新筛选下拉框与商品编辑下拉框
    const filterSel = document.getElementById('filterCategory');
    const editSel = document.getElementById('editCategory');
    filterSel.innerHTML = '<option value="">全部分类</option>' +
        categoriesCache.map(c => `<option value="${c.id}">${c.categoryName} (${c.productCount})</option>`).join('');
    editSel.innerHTML = categoriesCache.map(c => `<option value="${c.id}">${c.categoryName}</option>`).join('');

    // 渲染分类管理表格
    const tbody = document.getElementById('categoryTableBody');
    if (categoriesCache.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8;">暂无分类数据</td></tr>';
        return;
    }
    tbody.innerHTML = categoriesCache.map(c => `
        <tr>
            <td>${c.id}</td>
            <td><b>${c.categoryName}</b></td>
            <td>${c.productCount} 件</td>
            <td>${c.createTime ? c.createTime.replace('T', ' ') : '-'}</td>
            <td>
                <button class="btn btn-outline btn-sm" onclick='openCategoryModal(${JSON.stringify(c)})'>重命名</button>
                <button class="btn btn-danger btn-sm" onclick="deleteCategory(${c.id})">删除</button>
            </td>
        </tr>
    `).join('');
}

function openCategoryModal(category = null) {
    document.getElementById('categoryModal').classList.remove('hidden');
    if (category) {
        document.getElementById('categoryModalTitle').innerText = '修改分类名称';
        document.getElementById('editCatId').value = category.id;
        document.getElementById('editCatName').value = category.categoryName;
    } else {
        document.getElementById('categoryModalTitle').innerText = '新增商品分类';
        document.getElementById('editCatId').value = '';
        document.getElementById('editCatName').value = '';
    }
}

async function saveCategory() {
    const id = document.getElementById('editCatId').value;
    const categoryName = document.getElementById('editCatName').value.trim();
    if (!categoryName) return alert('请输入分类名称');

    const res = await authFetch(`${API_BASE}/categories`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: id ? Number(id) : null, categoryName })
    });
    const json = await res.json();
    if (json.code !== 200) return alert(json.msg);

    closeModal('categoryModal');
    await loadCategories();
    await loadProducts(currentPage);
}

async function deleteCategory(id) {
    if (!confirm('确认删除该分类吗？')) return;
    const res = await authFetch(`${API_BASE}/categories/${id}`, { method: 'DELETE' });
    const json = await res.json();
    if (json.code !== 200) return alert(json.msg);
    await loadCategories();
}

// --- 3. 商品管理 (CRUD) ---
async function loadProducts(page) {
    currentPage = page;
    const keyword = document.getElementById('searchKeyword').value.trim();
    const categoryId = document.getElementById('filterCategory').value;

    let url = `${API_BASE}/products?page=${page}&size=${pageSize}`;
    if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
    if (categoryId) url += `&categoryId=${categoryId}`;

    const res = await authFetch(url);
    const json = await res.json();
    const { list, total } = json.data;

    document.getElementById('statTotal').innerText = total;
    renderProductTable(list);
    renderPagination(total);
}

function renderProductTable(list) {
    const tbody = document.getElementById('productTableBody');
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:#94a3b8;">暂无商品数据</td></tr>';
        return;
    }
    tbody.innerHTML = list.map(p => `
        <tr>
            <td>${p.id}</td>
            <td><img class="thumb" src="${p.imageUrl || 'https://placeholder.com'}" alt="封面"></td>
            <td><a href="${p.sourceUrl}" target="_blank" style="color:#1e293b;text-decoration:none;font-weight:500;">${p.title}</a></td>
            <td>${p.categoryName || '未分类'}</td>
            <td>£${Number(p.price).toFixed(2)}</td>
            <td>${p.stock}</td>
            <td>${'⭐'.repeat(p.rating)}</td>
            <td><span class="badge ${p.status === 1 ? 'badge-up' : 'badge-down'}">${p.status === 1 ? '已上架' : '已下架'}</span></td>
            <td>
                <button class="btn btn-outline btn-sm" onclick='openProductModal(${JSON.stringify(p)})'>编辑</button>
                <button class="btn btn-secondary btn-sm" onclick="toggleStatus(${p.id})">${p.status === 1 ? '下架' : '上架'}</button>
                <button class="btn btn-danger btn-sm" onclick="deleteProduct(${p.id})">删除</button>
            </td>
        </tr>
    `).join('');
}

function renderPagination(total) {
    const totalPages = Math.ceil(total / pageSize) || 1;
    const div = document.getElementById('pagination');
    div.innerHTML = `
        <span>共 ${total} 条记录 | 第 ${currentPage} / ${totalPages} 页</span>
        <button class="btn btn-outline btn-sm" ${currentPage <= 1 ? 'disabled' : ''} onclick="loadProducts(${currentPage - 1})">上一页</button>
        <button class="btn btn-outline btn-sm" ${currentPage >= totalPages ? 'disabled' : ''} onclick="loadProducts(${currentPage + 1})">下一页</button>
    `;
}

function resetSearch() {
    document.getElementById('searchKeyword').value = '';
    document.getElementById('filterCategory').value = '';
    loadProducts(1);
}

function openProductModal(product = null) {
    if (categoriesCache.length === 0) {
        return alert('请先启动爬虫或前往“商品分类管理”创建一个分类！');
    }
    document.getElementById('productModal').classList.remove('hidden');
    if (product) {
        document.getElementById('productModalTitle').innerText = '编辑商品信息';
        document.getElementById('editProdId').value = product.id;
        document.getElementById('editTitle').value = product.title;
        document.getElementById('editCategory').value = product.categoryId;
        document.getElementById('editPrice').value = product.price;
        document.getElementById('editStock').value = product.stock;
        document.getElementById('editRating').value = product.rating;
    } else {
        document.getElementById('productModalTitle').innerText = '手动录入新商品';
        document.getElementById('editProdId').value = '';
        document.getElementById('editTitle').value = '';
        document.getElementById('editPrice').value = '19.99';
        document.getElementById('editStock').value = '50';
        document.getElementById('editRating').value = '5';
    }
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.add('hidden');
}

async function saveProduct() {
    const id = document.getElementById('editProdId').value;
    const payload = {
        id: id ? Number(id) : null,
        title: document.getElementById('editTitle').value.trim(),
        categoryId: Number(document.getElementById('editCategory').value),
        price: Number(document.getElementById('editPrice').value),
        stock: Number(document.getElementById('editStock').value),
        rating: Number(document.getElementById('editRating').value)
    };
    if (!payload.title) return alert('请填写商品标题');

    const res = await authFetch(`${API_BASE}/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    const json = await res.json();
    if (json.code !== 200) return alert(json.msg);

    closeModal('productModal');
    await loadCategories();
    await loadProducts(currentPage);
}

async function toggleStatus(id) {
    await authFetch(`${API_BASE}/products/${id}/status`, { method: 'PUT' });
    loadProducts(currentPage);
}
async function deleteProduct(id) {
    if (!confirm('确认永久删除该商品吗？')) return;
    await authFetch(`${API_BASE}/products/${id}`, { method: 'DELETE' });
    await loadCategories();
    await loadProducts(currentPage);
}
// --- 4. 修改管理员密码 ---
function openPwdModal() {
    document.getElementById('oldPwdInput').value = '';
    document.getElementById('newPwdInput').value = '';
    document.getElementById('confirmPwdInput').value = '';
    document.getElementById('pwdModal').classList.remove('hidden');
}

async function submitChangePwd() {
    const oldPassword = document.getElementById('oldPwdInput').value.trim();
    const newPassword = document.getElementById('newPwdInput').value.trim();
    const confirmPassword = document.getElementById('confirmPwdInput').value.trim();

    if (!oldPassword || !newPassword) return alert('请完整填写原密码和新密码');
    if (newPassword !== confirmPassword) return alert('两次输入的新密码不一致');

    const res = await authFetch(API_BASE + '/admin/password', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ oldPassword, newPassword })
    });
    const json = await res.json();
    if (json.code !== 200) return alert(json.msg);

    alert(json.data);
    closeModal('pwdModal');
    localStorage.removeItem('mall_token');
    localStorage.removeItem('mall_nickname');
    showLoginView();
}
