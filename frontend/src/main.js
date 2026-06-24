import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import './legacy/common-globals';
import './assets/styles/styles.css';
import './assets/styles/auth.css';
import './assets/styles/dashboard.css';
import './assets/styles/draft.css';
import './assets/styles/edit.css';
import './assets/styles/templates.css';
import './assets/styles/users.css';
import './assets/styles/audit.css';
import './assets/styles/approval.css';
import './assets/styles/archive.css';
import './assets/styles/fulfillment.css';
import './assets/styles/ledger.css';
import './assets/styles/risk.css';
import './assets/styles/seal.css';
import './assets/styles/signature.css';
import './assets/vendor/wang-editor/style.css';

createApp(App)
  .use(createPinia())
  .use(router)
  .mount('#app');

