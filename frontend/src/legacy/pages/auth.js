function bindAuthForm(formId, buttonId, handler) {
    const form = document.getElementById(formId);
    const button = document.getElementById(buttonId);
    if (!form || !button) {
        return;
    }
    form.setAttribute('action', 'javascript:void(0)');
    form.addEventListener('submit', (event) => event.preventDefault());

    const validate = () => {
        let ok = true;
        Array.from(form.elements).forEach((field) => {
            if (!field.matches?.('input, select, textarea')) return;
            clearFieldError(field);
            const value = String(field.value || '').trim();
            if (field.required && !value) {
                showFieldError(field, '此项必填');
                ok = false;
                return;
            }
            const min = Number(field.getAttribute('minlength'));
            if (value && Number.isFinite(min) && value.length < min) {
                showFieldError(field, `至少 ${min} 个字符`);
                ok = false;
            }
        });
        return ok;
    };

    const run = async () => {
        if (button.disabled || !validate()) return;
        setButtonBusy(button, true, buttonId === 'loginBtn' ? '登录中...' : '注册中...');
        try {
            await handler(new FormData(form));
        } finally {
            setButtonBusy(button, false);
        }
    };

    button.addEventListener('click', run);
    form.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            run();
        }
    });
}

function initLoginPage() {
    if (typeof redirectIfAuthed === 'function') {
        redirectIfAuthed();
    }
    bindAuthForm('loginForm', 'loginBtn', async (form) => {
        try {
            await login(form.get('username'), form.get('password'));
            if (typeof toast === 'function') {
                toast('登录成功');
            }
            location.href = '/html/dashboard.html';
        } catch (error) {
            if (typeof toast === 'function') {
                toast(error.message || '登录失败');
            } else {
                alert(error.message || '登录失败');
            }
        }
    });
}

function initRegisterPage() {
    if (typeof redirectIfAuthed === 'function') {
        redirectIfAuthed();
    }
    bindAuthForm('registerForm', 'registerBtn', async (form) => {
        const password = form.get('password');
        if (password !== form.get('confirmPassword')) {
            const message = '两次输入的密码不一致';
            showFieldError(document.querySelector('[name="confirmPassword"]'), message);
            if (typeof toast === 'function') {
                toast(message);
            } else {
                alert(message);
            }
            return;
        }
        try {
            await register(form.get('username'), password);
            if (typeof toast === 'function') {
                toast('注册成功，已自动登录');
            }
            location.href = '/html/dashboard.html';
        } catch (error) {
            if (typeof toast === 'function') {
                toast(error.message || '注册失败');
            } else {
                alert(error.message || '注册失败');
            }
        }
    });
}
