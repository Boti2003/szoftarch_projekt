import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { LessonListPage } from './LessonListPage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LessonListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('betölti a leckéket és megjeleníti a státusz címkéket', async () => {
  renderPage();

  expect(await screen.findByText('1. Alapok')).toBeInTheDocument();
  expect(await screen.findByText('Folyamatban')).toBeInTheDocument();
  expect(await screen.findByText('Befejezve')).toBeInTheDocument();
  expect(await screen.findByText('Zárolva')).toBeInTheDocument();
});
