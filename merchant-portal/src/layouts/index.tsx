import { MERCHANT_ID } from '@/services/api';
import { theme } from '@/theme';
import { Link, NavLink, Outlet } from '@umijs/max';
import { App, ConfigProvider } from 'antd';

const NAV = [
  { to: '/payments', label: 'Payments' },
  { to: '/trial-balance', label: 'Trial balance' },
];

/** ACME app bar: wordmark, up to five destinations, then the account slot pushed right. */
export default function Layout() {
  return (
    <ConfigProvider theme={theme}>
      <App>
        <header className="pl-topbar">
          <Link to="/payments" className="pl-wordmark">
            <span className="pl-wordmark__mark" aria-hidden="true">
              P
            </span>
            PayLab
          </Link>
          <nav className="pl-topbar__nav" aria-label="Primary">
            {NAV.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className="pl-topbar__link"
                // /payments/new and /payments/:id still count as Payments.
                end={false}
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="pl-topbar__account">
            <span className="pl-mono">{MERCHANT_ID}</span>
          </div>
        </header>
        <Outlet />
      </App>
    </ConfigProvider>
  );
}
