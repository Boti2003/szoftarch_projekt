import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, test } from 'vitest';
import { LessonListPage } from '../LessonListPage';
import { LessonDetailPage } from '../LessonDetailPage';
import { ExercisePlayerPage } from '../ExercisePlayerPage';

// Walking-skeleton integration flow against the MSW handlers:
// lesson list -> open a lesson -> open an auto exercise -> answer -> feedback.
function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/lessons']}>
        <Routes>
          <Route path="/lessons" element={<LessonListPage />} />
          <Route path="/lessons/:id" element={<LessonDetailPage />} />
          <Route
            path="/lessons/:id/exercises/:exerciseId"
            element={<ExercisePlayerPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('leckelista -> lecke -> auto feladat -> visszajelzés', async () => {
  renderApp();

  // Lesson list loads; open the first (unlocked) lesson.
  fireEvent.click(await screen.findByText('1. Alapok'));

  // Lesson detail renders the theory heading and the exercise list.
  expect(await screen.findByText('Feladatok')).toBeInTheDocument();

  // Open the MULTIPLE_CHOICE exercise from the list.
  fireEvent.click(await screen.findByText('Melyik a helyes?'));

  // Answer and submit the auto exercise.
  const option = await screen.findByLabelText('Hello');
  fireEvent.click(option);
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  // Feedback from the attempt fixture renders.
  expect(await screen.findByText('Szép munka!')).toBeInTheDocument();
  expect(screen.getByText('Helyes')).toBeInTheDocument();
});
