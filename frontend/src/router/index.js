import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Dashboard from '../views/Dashboard.vue';
import Draft from '../views/Draft.vue';
import Edit from '../views/Edit.vue';
import Templates from '../views/Templates.vue';
import Risk from '../views/Risk.vue';
import Approval from '../views/Approval.vue';
import Ledger from '../views/Ledger.vue';
import Seal from '../views/Seal.vue';
import Archive from '../views/Archive.vue';
import Blockchain from '../views/Blockchain.vue';
import Signature from '../views/Signature.vue';
import Fulfillment from '../views/Fulfillment.vue';
import Users from '../views/Users.vue';
import Audit from '../views/Audit.vue';

const legacyRedirects = [
  'dashboard',
  'draft',
  'edit',
  'templates',
  'risk',
  'approval',
  'ledger',
  'seal',
  'archive',
  'blockchain',
  'signature',
  'fulfillment',
  'users',
  'audit',
  'login',
  'register'
].flatMap((name) => [
  { path: `/${name}.html`, redirect: `/${name}` },
  { path: `/html/${name}.html`, redirect: `/${name}` }
]);

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/register', component: Register, meta: { public: true } },
    { path: '/dashboard', component: Dashboard },
    { path: '/draft', component: Draft },
    { path: '/edit', component: Edit },
    { path: '/draft/edit', redirect: '/edit' },
    { path: '/templates', component: Templates },
    { path: '/risk', component: Risk },
    { path: '/approval', component: Approval },
    { path: '/ledger', component: Ledger },
    { path: '/seal', component: Seal },
    { path: '/archive', component: Archive },
    { path: '/blockchain', component: Blockchain },
    { path: '/signature', component: Signature },
    { path: '/fulfillment', component: Fulfillment },
    { path: '/users', component: Users },
    { path: '/audit', component: Audit },
    ...legacyRedirects,
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  auth.restore();
  if (!to.meta.public && !auth.isAuthed) return '/login';
  if (to.meta.public && auth.isAuthed && (to.path === '/login' || to.path === '/register')) return '/dashboard';
  return true;
});

export default router;
