import type { ThemeConfig } from 'antd';

// ACME design system v5 mapped onto antd 5. Values come from the system's tokens.json
// (mirrored as CSS variables in src/global.css). Every colour has one job:
//   ink (Graphite 900) fills the one primary action per view,
//   Blue marks interaction (links, focus, selection),
//   Green / Amber / Red / Blue report status,
//   Red on a control means destructive, nothing else.
// Light theme only: v5 has no dark theme.

const FONT_SANS = '"Inter", "Helvetica Neue", "Segoe UI", system-ui, sans-serif';
const FONT_MONO = '"JetBrains Mono", ui-monospace, "Cascadia Code", Consolas, monospace';

const gray = {
  0: '#FFFFFF',
  50: '#F8F9FA',
  100: '#F1F3F5',
  200: '#E2E5E9',
  300: '#C8CDD3',
  400: '#838A94',
  500: '#676E78',
  600: '#525961',
  700: '#3C424A',
  800: '#272C32',
  900: '#15181D',
} as const;
const blue = { 50: '#EEF4FF', 200: '#BCD0FB', 600: '#1F57C9', 700: '#1A47A6', 800: '#163A85' } as const;
const green = { 50: '#EBF7F0', 200: '#B5E2C6', 700: '#11643F' } as const;
const amber = { 50: '#FEF6E6', 200: '#F6D8A0', 700: '#834808' } as const;
const red = { 50: '#FFF0EE', 200: '#FFBDB6', 600: '#C91C15', 700: '#A91515' } as const;

const SHADOW_SM = '0 1px 2px #1018280D';
const SHADOW_MD = '0 4px 12px -2px #10182814';
const SHADOW_LG = '0 16px 40px -8px #1018282E';
const FOCUS_RING = '0 0 0 2px ' + blue[600] + '40';

export const theme: ThemeConfig = {
  token: {
    // Primary = ink. Hover lightens one step, press settles.
    colorPrimary: gray[900],
    colorPrimaryHover: gray[700],
    colorPrimaryActive: gray[800],
    colorTextLightSolid: gray[0],
    colorLink: blue[700],
    colorLinkHover: blue[800],
    colorLinkActive: blue[800],
    colorSuccess: green[700],
    colorSuccessBg: green[50],
    colorSuccessBorder: green[200],
    colorWarning: amber[700],
    colorWarningBg: amber[50],
    colorWarningBorder: amber[200],
    colorError: red[700],
    colorErrorBg: red[50],
    colorErrorBorder: red[200],
    colorInfo: blue[700],
    colorInfoBg: blue[50],
    colorInfoBorder: blue[200],
    colorBgLayout: gray[0],
    colorBgContainer: gray[0],
    colorBgElevated: gray[0],
    colorBgMask: '#0B0D1073',
    colorFillAlter: gray[50],
    colorFillTertiary: gray[100],
    colorBgContainerDisabled: gray[50],
    colorTextDisabled: gray[500],
    colorBorder: gray[400],
    colorBorderSecondary: gray[200],
    colorSplit: gray[200],
    colorText: gray[900],
    colorTextHeading: gray[900],
    colorTextSecondary: gray[600],
    colorTextTertiary: gray[500],
    colorTextDescription: gray[600],
    colorTextPlaceholder: gray[500],
    controlOutline: blue[600] + '40',
    controlItemBgActive: blue[50],
    controlItemBgActiveHover: blue[50],
    fontFamily: FONT_SANS,
    fontFamilyCode: FONT_MONO,
    fontSize: 14,
    borderRadius: 6,
    borderRadiusLG: 10,
    borderRadiusSM: 4,
    controlHeight: 40,
    controlHeightSM: 32,
    controlHeightLG: 48,
    boxShadow: SHADOW_LG,
    boxShadowSecondary: SHADOW_MD,
    boxShadowTertiary: SHADOW_SM,
    motionDurationFast: '0.1s',
    motionDurationMid: '0.15s',
    motionDurationSlow: '0.25s',
    motionEaseOut: 'cubic-bezier(0.2, 0.8, 0.4, 1)',
    motionEaseInOut: 'cubic-bezier(0.45, 0, 0.25, 1)',
    wireframe: false,
  },
  components: {
    Button: {
      fontWeight: 500,
      primaryShadow: SHADOW_SM,
      defaultShadow: SHADOW_SM,
      dangerShadow: SHADOW_SM,
      defaultBorderColor: gray[300],
      defaultHoverBg: gray[50],
      defaultHoverColor: gray[900],
      defaultHoverBorderColor: gray[400],
      defaultActiveBg: gray[100],
      defaultActiveColor: gray[900],
      defaultActiveBorderColor: gray[400],
      textHoverBg: gray[100],
      // Destructive buttons use danger-emphasis (red-600), one step deeper on hover.
      colorError: red[600],
      colorErrorHover: red[700],
      colorErrorActive: red[700],
    },
    Table: {
      headerBg: gray[50],
      headerColor: gray[600],
      rowHoverBg: gray[50],
      borderColor: gray[200],
      headerSplitColor: 'transparent',
      cellFontSize: 14,
      cellPaddingBlock: 12,
      cellPaddingInline: 12,
      cellPaddingBlockSM: 8,
      cellPaddingInlineSM: 12,
    },
    Tabs: {
      inkBarColor: gray[900],
      itemColor: gray[600],
      itemHoverColor: gray[900],
      itemSelectedColor: gray[900],
      itemActiveColor: gray[900],
      titleFontSize: 14,
      horizontalItemGutter: 16,
      horizontalItemPadding: '8px 0',
    },
    Card: {
      headerFontSize: 16,
      headerBg: 'transparent',
      bodyPadding: 20,
    },
    Modal: {
      borderRadiusLG: 12,
      contentBg: gray[0],
      headerBg: gray[0],
      titleFontSize: 18,
    },
    Alert: {
      withDescriptionPadding: '16px',
      withDescriptionIconSize: 16,
    },
    Input: {
      hoverBorderColor: gray[500],
      activeBorderColor: blue[600],
      activeShadow: FOCUS_RING,
    },
    Select: {
      hoverBorderColor: gray[500],
      activeBorderColor: blue[600],
      activeOutlineColor: blue[600] + '40',
      optionSelectedBg: blue[50],
      optionSelectedColor: gray[900],
      optionSelectedFontWeight: 500,
    },
  },
};
