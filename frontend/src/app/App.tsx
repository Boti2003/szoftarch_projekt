import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { theme } from './theme';
import { Navbar } from './Navbar';
import { AuthProvider } from '../features/auth/AuthContext';
import { ProtectedRoute } from '../features/auth/ProtectedRoute';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { ProfilePage } from '../features/profile/ProfilePage';
import { LeaderboardPage } from '../features/leaderboard/LeaderboardPage';
import { HealthDashboard } from '../features/health/HealthDashboard';
import { AppLayout } from '../components/AppLayout';
import { LanguagesPage } from '../features/learning/LanguagesPage';
import { LessonListPage } from '../features/learning/LessonListPage';
import { LessonDetailPage } from '../features/learning/LessonDetailPage';
import { ExercisePlayerPage } from '../features/learning/ExercisePlayerPage';
import { ScenarioChatPage } from '../features/learning/ScenarioChatPage';
import { DailyChallengePage } from '../features/daily/DailyChallengePage';

const queryClient = new QueryClient();

// INTEGRÁCIÓS MERGE-DÖNTÉS (integration branch, learning + identity):
// Az Identity (A) auth-keretét (AuthProvider + Navbar + ProtectedRoute + login/
// register/profile/leaderboard) és a Learning (B) feature-oldalait (learning +
// daily) egyesítjük. A learning/daily oldalak JWT-t igényelnek (a backend minden
// /api/v1/** végpontja védett), ezért a ProtectedRoute ALÁ kerülnek. A health,
// login, register publikus. Az AppLayout a learning oldalak közös kerete; a Navbar
// az Identity globális navigációja — egyelőre mindkettő megmarad (lásd
// INTEGRACIO 9. szakasz: a közös layout/Navbar összevonása D-vel tisztázandó).
export function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrowserRouter>
            <Navbar />
            <Routes>
              {/* Publikus oldalak */}
              <Route path="/" element={<HealthDashboard />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />

              {/* Védett útvonalak – JWT szükséges (Identity auth) */}
              <Route element={<ProtectedRoute />}>
                {/* Identity (A) */}
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/leaderboard" element={<LeaderboardPage />} />

                {/* Learning (B) – a közös AppLayout keretben */}
                <Route
                  path="/languages"
                  element={
                    <AppLayout>
                      <LanguagesPage />
                    </AppLayout>
                  }
                />
                <Route
                  path="/lessons"
                  element={
                    <AppLayout>
                      <LessonListPage />
                    </AppLayout>
                  }
                />
                <Route
                  path="/lessons/:id"
                  element={
                    <AppLayout>
                      <LessonDetailPage />
                    </AppLayout>
                  }
                />
                <Route
                  path="/lessons/:id/exercises/:exerciseId"
                  element={
                    <AppLayout>
                      <ExercisePlayerPage />
                    </AppLayout>
                  }
                />
                <Route
                  path="/scenario/:exerciseId"
                  element={
                    <AppLayout>
                      <ScenarioChatPage />
                    </AppLayout>
                  }
                />
                <Route
                  path="/lessons/:lessonId/scenario/:exerciseId"
                  element={
                    <AppLayout>
                      <ScenarioChatPage />
                    </AppLayout>
                  }
                />
                <Route
                  path="/daily"
                  element={
                    <AppLayout>
                      <DailyChallengePage />
                    </AppLayout>
                  }
                />
              </Route>

              {/* Fallback */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
