import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { theme } from './theme';
import { HealthDashboard } from '../features/health/HealthDashboard';
import { AppLayout } from '../components/AppLayout';
import { LanguagesPage } from '../features/learning/LanguagesPage';
import { LessonListPage } from '../features/learning/LessonListPage';
import { LessonDetailPage } from '../features/learning/LessonDetailPage';
import { ExercisePlayerPage } from '../features/learning/ExercisePlayerPage';
import { ScenarioChatPage } from '../features/learning/ScenarioChatPage';
import { DailyChallengePage } from '../features/daily/DailyChallengePage';

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
              <Route path="/lessons" element={<LessonListPage />} />
              <Route path="/lessons/:id" element={<LessonDetailPage />} />
              <Route
                path="/lessons/:id/exercises/:exerciseId"
                element={<ExercisePlayerPage />}
              />
              <Route path="/scenario/:exerciseId" element={<ScenarioChatPage />} />
              <Route path="/daily" element={<DailyChallengePage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AppLayout>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
