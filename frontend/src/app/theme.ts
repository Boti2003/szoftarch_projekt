import { createTheme } from '@mui/material';

// Közös MUI theme. Letisztult, igényes alap: nyugodt kék-borostyán paletta,
// lágy sarkok, visszafogott árnyékok. A feature-ök ezt öröklik.
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2e5d9f', light: '#5a84c4', dark: '#1e4270', contrastText: '#ffffff' },
    secondary: { main: '#e08a1e', light: '#f0a847', dark: '#b06a0e', contrastText: '#ffffff' },
    success: { main: '#2e7d4f' },
    error: { main: '#c0392b' },
    background: { default: '#f5f7fa', paper: '#ffffff' },
    text: { primary: '#1c2733', secondary: '#5a6775' },
  },
  shape: {
    borderRadius: 10,
  },
  typography: {
    fontFamily: 'system-ui, Roboto, "Helvetica Neue", Arial, sans-serif',
    h4: { fontWeight: 700, letterSpacing: '-0.01em' },
    h5: { fontWeight: 700, letterSpacing: '-0.01em' },
    h6: { fontWeight: 600 },
    button: { fontWeight: 600, textTransform: 'none' },
  },
  components: {
    MuiAppBar: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { borderBottom: '1px solid rgba(0,0,0,0.08)' },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 8 },
      },
    },
    MuiPaper: {
      styleOverrides: {
        outlined: { borderColor: 'rgba(0,0,0,0.1)' },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600 },
      },
    },
  },
});
