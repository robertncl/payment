import type { BadgeVariant } from '@/services/format';

// ACME Badge: status glyphs on a 24px grid, 2px stroke, so status never relies on hue alone.
// Neutral has none.
const PATHS: Partial<Record<BadgeVariant, string[]>> = {
  success: ['M20 6 9 17l-5-5'],
  warning: ['M12 3 2 20h20L12 3z', 'M12 10v4', 'M12 17h.01'],
  danger: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M15 9l-6 6', 'M9 9l6 6'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 16v-4', 'M12 8h.01'],
};

export function StatusIcon({ variant, size = 12 }: { variant: BadgeVariant; size?: number }) {
  const d = PATHS[variant];
  if (!d) return null;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {d.map((p) => (
        <path key={p} d={p} />
      ))}
    </svg>
  );
}

/** Read-only status pill: one or two words, sentence case, never a button. */
export default function StatusBadge({
  variant = 'neutral',
  children,
}: {
  variant?: BadgeVariant;
  children: React.ReactNode;
}) {
  return (
    <span className={'pl-badge' + (variant === 'neutral' ? '' : ' pl-badge--' + variant)}>
      <StatusIcon variant={variant} />
      {children}
    </span>
  );
}
