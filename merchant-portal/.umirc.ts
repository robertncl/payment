import { defineConfig } from '@umijs/max';

// Merchant portal: antd 6 themed with ACME tokens (src/theme.ts) under our own layout;
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
  // umi aliases `react-router` and `react-router-dom` to their package directories, and a
  // webpack directory alias matches every subpath and bypasses the package `exports` map.
  // react-router 7 only exposes `react-router/dom` through `exports`, so react-router-dom 7's
  // `import ... from "react-router/dom"` fails to resolve. Make both aliases exact-match: the
  // bare imports still pin the single hoisted copy, and subpaths resolve through `exports`.
  chainWebpack(memo) {
    for (const pkg of ['react-router', 'react-router-dom']) {
      const target = memo.resolve.alias.get(pkg);
      if (target) memo.resolve.alias.delete(pkg).set(`${pkg}$`, target);
    }
    return memo;
  },
  npmClient: 'npm',
  // antd 6 chunks share minified helper names; umi requires IIFE minification to avoid conflicts.
  esbuildMinifyIIFE: true,
});
