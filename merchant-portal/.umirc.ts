import { defineConfig } from '@umijs/max';

// Merchant portal: antd 5 themed with ACME tokens (src/theme.ts) under our own layout;
// no ProLayout, no antd plugin. The dev server proxies /api to the gateway from compose.
export default defineConfig({
  layout: false,
  routes: [
    { path: '/', redirect: '/payments' },
    { path: '/payments', component: 'Payments' },
    { path: '/payments/new', component: 'NewPayment' },
    { path: '/payments/:id', component: 'PaymentDetail' },
    { path: '/trial-balance', component: 'TrialBalance' },
  ],
  title: 'PayLab merchant portal',
  proxy: {
    '/api': {
      target: process.env.PAYLAB_GATEWAY_URL || 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  npmClient: 'npm',
});
