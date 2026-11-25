import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { theme } from './theme';
import { HealthDashboard } from '../features/health/HealthDashboard';
import { AppLayout } from '../components/AppLayout';
import { LanguagesPage } from '../features/learning/LanguagesPage';

const queryClient = new QueryClient();

export function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AppLayout>
            <Routes>
              {/* Skeleton: a health dashboard bizonyítja a végponttól végpontig működést.
                  A feature oldalakat (auth, learning, social, ai) a gazdák adják hozzá. */}
              <Route path="/" element={<HealthDashboard />} />
              <Route path="/languages" element={<LanguagesPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AppLayout>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
