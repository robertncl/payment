import { MERCHANT_ID } from '@/services/api';
import { themeFor, type Mode } from '@/theme';
import { Link, NavLink, Outlet } from '@umijs/max';
import { App, ConfigProvider } from 'antd';
import { useEffect, useState } from 'react';

const DARK_QUERY = '(prefers-color-scheme: dark)';

/** Follows the OS theme and mirrors it onto <html data-theme> for the CSS-variable tokens. */
function useColorMode(): Mode {
  const [mode, setMode] = useState<Mode>(() =>
    window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light',
  );
  useEffect(() => {
    const mq = window.matchMedia(DARK_QUERY);
    const onChange = (e: MediaQueryListEvent) => setMode(e.matches ? 'dark' : 'light');
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = mode;
  }, [mode]);
  return mode;
}

const NAV = [
  { to: '/payments', label: 'Payments' },
  { to: '/trial-balance', label: 'Trial balance' },
];

export default function Layout() {
  const mode = useColorMode();
  return (
    <ConfigProvider theme={themeFor(mode)}>
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
