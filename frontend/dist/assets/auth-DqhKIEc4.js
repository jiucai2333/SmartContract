const n=`function bindAuthForm(formId, buttonId, handler) {
    const form = document.getElementById(formId);\r
    const button = document.getElementById(buttonId);\r
    if (!form || !button) {\r
        return;\r
    }\r
    form.setAttribute('action', 'javascript:void(0)');\r
    form.addEventListener('submit', (event) => event.preventDefault());\r
\r
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
                showFieldError(field, \`至少 \${min} 个字符\`);
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
\r
    button.addEventListener('click', run);\r
    form.addEventListener('keydown', (event) => {\r
        if (event.key === 'Enter') {\r
            event.preventDefault();\r
            run();\r
        }\r
    });\r
}\r
\r
function initLoginPage() {\r
    if (typeof redirectIfAuthed === 'function') {\r
        redirectIfAuthed();\r
    }\r
    bindAuthForm('loginForm', 'loginBtn', async (form) => {
        try {\r
            await login(form.get('username'), form.get('password'));\r
            if (typeof toast === 'function') {\r
                toast('登录成功');\r
            }\r
            location.href = '/html/dashboard.html';\r
        } catch (error) {\r
            if (typeof toast === 'function') {\r
                toast(error.message || '登录失败');\r
            } else {\r
                alert(error.message || '登录失败');\r
            }\r
        }\r
    });\r
}\r
\r
function initRegisterPage() {\r
    if (typeof redirectIfAuthed === 'function') {\r
        redirectIfAuthed();\r
    }\r
    bindAuthForm('registerForm', 'registerBtn', async (form) => {
        const password = form.get('password');
        if (password !== form.get('confirmPassword')) {
            const message = '两次输入的密码不一致';
            showFieldError(document.querySelector('[name="confirmPassword"]'), message);
            if (typeof toast === 'function') {
                toast(message);
            } else {
                alert(message);\r
            }\r
            return;\r
        }\r
        try {\r
            await register(form.get('username'), password);\r
            if (typeof toast === 'function') {\r
                toast('注册成功，已自动登录');\r
            }\r
            location.href = '/html/dashboard.html';\r
        } catch (error) {\r
            if (typeof toast === 'function') {\r
                toast(error.message || '注册失败');\r
            } else {\r
                alert(error.message || '注册失败');\r
            }\r
        }\r
    });\r
}\r
`;export{n as default};
