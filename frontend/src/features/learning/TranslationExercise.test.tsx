import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import { ExercisePlayer } from './ExercisePlayer';
import {
  EX_TRANSLATION,
  attemptResponseFixture,
  server,
} from '../../test/mswServer';

function renderPlayer(exerciseId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ExercisePlayer exerciseId={exerciseId} />
    </QueryClientProvider>,
  );
}

test('TRANSLATION { text } payloadot küld és megjeleníti az AI visszajelzést', async () => {
  let sentBody: unknown = null;
  server.use(
    http.post('/api/v1/exercises/:exerciseId/attempts', async ({ request }) => {
      sentBody = await request.json();
      return HttpResponse.json(attemptResponseFixture);
    }),
  );

  renderPlayer(EX_TRANSLATION);

  // Source text and the optional marker render; no feedback before submit.
  expect(await screen.findByText('Jó reggelt!')).toBeInTheDocument();
  expect(
    screen.getByText(/Opcionális \(nem kötelező a lecke teljesítéséhez\)/),
  ).toBeInTheDocument();
  expect(screen.queryByText('Szép munka!')).not.toBeInTheDocument();

  const field = screen.getByLabelText('Fordítás');
  fireEvent.change(field, { target: { value: 'Good morning!' } });
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  expect(await screen.findByText('Szép munka!')).toBeInTheDocument();
  await waitFor(() =>
    expect(sentBody).toEqual({ answer: { text: 'Good morning!' } }),
  );
});
