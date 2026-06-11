if (!initAppShell('dashboard', '工作台', 'Dashboard')) {
    throw new Error('auth required');
}

function fmt(n) { return n != null ? Number(n).toLocaleString('zh-CN') : '0'; }

async function loadDashboard() {
    const data = await api('/api/dashboard');
    $('#totalContracts').textContent = fmt(data.totalContracts);
    $('#approvingContracts').textContent = fmt(data.approvingContracts);
    $('#highRiskContracts').textContent = fmt(data.highRiskContracts);
    $('#dueSoonPlans').textContent = fmt(data.dueSoonPlans);
}

loadDashboard().catch(error => toast(error.message));
