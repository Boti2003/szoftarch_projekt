import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { ExercisePlayer } from './ExercisePlayer';
import {
  EX_ERROR_FINDING,
  EX_MATCHING,
  EX_MULTIPLE_CHOICE,
  EX_ODD_ONE_OUT,
  EX_TRUE_FALSE,
} from '../../test/mswServer';

function renderPlayer(exerciseId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ExercisePlayer exerciseId={exerciseId} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('MULTIPLE_CHOICE beküldése után megjelenik a visszajelzés', async () => {
  renderPlayer(EX_MULTIPLE_CHOICE);

  // Prompt and options render; no solution/correctness hint before submit.
  const option = await screen.findByLabelText('Hello');
  expect(screen.queryByText('Helyes')).not.toBeInTheDocument();
  expect(screen.queryByText('Helytelen')).not.toBeInTheDocument();
  expect(screen.queryByText('Szép munka!')).not.toBeInTheDocument();

  fireEvent.click(option);
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  // Feedback from the attempt fixture renders after submit.
  expect(await screen.findByText('Szép munka!')).toBeInTheDocument();
  expect(screen.getByText('Helyes')).toBeInTheDocument();
  expect(screen.getByText('Eredmény: 100%')).toBeInTheDocument();
});

test('TRUE_FALSE feladat hibamentesen renderel', async () => {
  renderPlayer(EX_TRUE_FALSE);
  expect(await screen.findByText('Igaz')).toBeInTheDocument();
  expect(screen.getByText('Hamis')).toBeInTheDocument();
});

test('MATCHING feladat hibamentesen renderel', async () => {
  renderPlayer(EX_MATCHING);
  expect(await screen.findByText('Párosítsd az elemeket')).toBeInTheDocument();
});

test('ODD_ONE_OUT feladat hibamentesen renderel', async () => {
  renderPlayer(EX_ODD_ONE_OUT);
  expect(await screen.findByLabelText('car')).toBeInTheDocument();
});

test('ERROR_FINDING feladat hibamentesen renderel', async () => {
  renderPlayer(EX_ERROR_FINDING);
  expect(await screen.findByText('Jelöld meg a hibás szavakat')).toBeInTheDocument();
});
