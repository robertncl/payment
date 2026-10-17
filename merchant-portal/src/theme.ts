import { theme as antdTheme, type ThemeConfig } from 'antd';

// ACME design system tokens mapped onto antd 5. Hex values come from the system's tokens.json
// (Oat neutrals, one Clay accent, Brick for danger, Sky for info). Success and warning stay in
// Oat ink on purpose: their icon and label carry the meaning, not a hue.

export type Mode = 'light' | 'dark';

const FONT_SANS = '"Styrene B", "Inter", "Helvetica Neue", "Segoe UI", system-ui, sans-serif';
const FONT_MONO = '"JetBrains Mono", ui-monospace, "Cascadia Code", Consolas, monospace';

const palette = {
  light: {
    canvas: '#F3F0E7',
    surface: '#E9E4D5',
    raised: '#FCFBF7',
    border: '#C4BDA4',
    borderStrong: '#7E7764',
    text: '#1E1B13',
    muted: '#554F41',
    subtle: '#696352',
    link: '#80432F',
    linkHover: '#6B3624',
    selected: '#80432F',
    selectedSoft: '#FAF0EB',
    danger: '#97291B',
    dangerSoft: '#FBEEEC',
    info: '#305875',
    infoSoft: '#EDF1F4',
    status: '#554F41',
    focus: '#94513B',
  },
  dark: {
    canvas: '#100E08',
    surface: '#1E1B13',
    raised: '#2F2B21',
    border: '#554F41',
    borderStrong: '#7E7764',
    text: '#E9E4D5',
    muted: '#C4BDA4',
    subtle: '#A29B89',
    link: '#DDA588',
    linkHover: '#E9C3AF',
    selected: '#DDA588',
    selectedSoft: '#3A2A1F',
    danger: '#EF7361',
    dangerSoft: '#38241A',
    info: '#8DB0C9',
    infoSoft: '#2A2F2D',
    status: '#A29B89',
    focus: '#D18B6A',
  },
} as const;

export function themeFor(mode: Mode): ThemeConfig {
  const c = palette[mode];
  return {
    algorithm: mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      // The primary fill stays clay-600 with white text in both themes.
      colorPrimary: '#94513B',
      colorLink: c.link,
      colorLinkHover: c.linkHover,
      colorLinkActive: c.linkHover,
      colorError: c.danger,
      colorErrorBg: c.dangerSoft,
      colorInfo: c.info,
      colorInfoBg: c.infoSoft,
      colorSuccess: c.status,
      colorWarning: c.status,
      colorBgLayout: c.canvas,
      colorBgContainer: c.raised,
      colorBgElevated: c.raised,
      colorFillAlter: c.surface,
      colorBgContainerDisabled: c.surface,
      colorTextDisabled: c.subtle,
      colorBorder: c.borderStrong,
      colorBorderSecondary: c.border,
      colorSplit: c.border,
      colorText: c.text,
      colorTextHeading: c.text,
      colorTextSecondary: c.muted,
      colorTextTertiary: c.subtle,
      colorTextDescription: c.muted,
      colorTextPlaceholder: c.subtle,
      controlOutline: mode === 'dark' ? 'rgba(209, 139, 106, 0.35)' : 'rgba(148, 81, 59, 0.25)',
      fontFamily: FONT_SANS,
      fontFamilyCode: FONT_MONO,
      fontSize: 14,
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 4,
      controlHeight: 40,
      controlHeightSM: 32,
      controlHeightLG: 48,
      boxShadow: 'var(--acme-shadow-lg)',
      boxShadowSecondary: 'var(--acme-shadow-md)',
      boxShadowTertiary: 'var(--acme-shadow-sm)',
      motionDurationFast: '0.1s',
      motionDurationMid: '0.15s',
      motionDurationSlow: '0.25s',
      motionEaseOut: 'cubic-bezier(0.2, 0, 0, 1)',
      wireframe: false,
    },
    components: {
      Button: {
        fontWeight: 600,
        primaryShadow: 'none',
        defaultShadow: 'var(--acme-shadow-sm)',
        dangerShadow: 'none',
        defaultHoverBg: c.surface,
        defaultHoverColor: c.text,
        defaultHoverBorderColor: c.borderStrong,
        textHoverBg: c.surface,
      },
      Table: {
        headerBg: c.surface,
        headerColor: c.muted,
        rowHoverBg: c.surface,
        borderColor: c.border,
        headerSplitColor: 'transparent',
      },
      Tabs: {
        inkBarColor: '#94513B',
        itemColor: c.muted,
        itemHoverColor: c.text,
        itemSelectedColor: c.selected,
        itemActiveColor: c.selected,
        titleFontSize: 14,
      },
      Card: {
        headerFontSize: 20,
        headerBg: 'transparent',
      },
      Modal: {
        borderRadiusLG: 16,
        contentBg: c.raised,
        headerBg: c.raised,
      },
      Input: { activeShadow: '0 0 0 2px ' + c.focus + '40' },
      Select: { optionSelectedBg: c.selectedSoft, optionSelectedColor: c.selected },
    },
  };
}
