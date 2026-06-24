const legacyScripts = {
  approval: () => import('./pages/approval.js?raw'),
  archive: () => import('./pages/archive.js?raw'),
  audit: () => import('./pages/audit.js?raw'),
  auth: () => import('./pages/auth.js?raw'),
  blockchain: () => import('./pages/blockchain.js?raw'),
  dashboard: () => import('./pages/dashboard.js?raw'),
  draft: () => import('./pages/draft.js?raw'),
  edit: () => import('./pages/edit.js?raw'),
  fulfillment: () => import('./pages/fulfillment.js?raw'),
  ledger: () => import('./pages/ledger.js?raw'),
  risk: () => import('./pages/risk.js?raw'),
  seal: () => import('./pages/seal.js?raw'),
  signature: () => import('./pages/signature.js?raw'),
  templates: () => import('./pages/templates.js?raw'),
  users: () => import('./pages/users.js?raw')
};

let wangEditorPromise = null;

function loadScriptOnce(src) {
  const existing = document.querySelector(`script[data-src="${src}"]`);
  if (existing) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.dataset.src = src;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`无法加载脚本：${src}`));
    document.head.appendChild(script);
  });
}

async function ensurePageRuntime(name) {
  if (name === 'edit') {
    wangEditorPromise ||= loadScriptOnce('/vendor/wang-editor/index.js');
    await wangEditorPromise;
  }
}

export async function runLegacyPage(name, initializer) {
  const load = legacyScripts[name];
  if (!load) return;
  await ensurePageRuntime(name);
  const { default: code } = await load();
  const runner = new Function(`
    ${code}
    return {
      initLoginPage: typeof initLoginPage === 'function' ? initLoginPage : null,
      initRegisterPage: typeof initRegisterPage === 'function' ? initRegisterPage : null
    };
    //# sourceURL=legacy-${name}.js
  `);
  const result = runner.call(window);
  if (initializer && typeof result?.[initializer] === 'function') {
    result[initializer]();
  }
}
