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
  // A demó-fixture több leckét mutat (angol: 9 lecke), így egyes státusz-chipek
  // többször is előfordulnak — getAllByText a nem egyedi címkékhez.
  expect((await screen.findAllByText('Befejezve')).length).toBeGreaterThanOrEqual(1);
  expect((await screen.findAllByText('Zárolva')).length).toBeGreaterThanOrEqual(1);
});
