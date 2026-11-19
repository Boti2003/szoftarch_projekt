import { createTheme } from '@mui/material';

// Közös MUI theme (D11). A gazdák ezt bővítik; a baseline egységes alapot ad.
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2e5d9f' },
    secondary: { main: '#e08a1e' },
  },
  typography: {
    fontFamily: 'system-ui, Roboto, "Helvetica Neue", Arial, sans-serif',
  },
});
