if (!initAppShell('dashboard', '工作台', 'Dashboard')) {
    throw new Error('auth required');
}

$('#welcomeRole').textContent = ROLE_LABELS[state.roleCode] || state.roleCode;
