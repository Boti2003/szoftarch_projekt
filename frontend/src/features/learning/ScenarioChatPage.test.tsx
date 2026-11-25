import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import { ScenarioChatPage } from './ScenarioChatPage';
import { EX_AI_SCENARIO, server } from '../../test/mswServer';

function renderScenario(exerciseId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/scenario/${exerciseId}`]}>
        <Routes>
          <Route path="/scenario/:exerciseId" element={<ScenarioChatPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('indít -> üzenetet küld -> lezár folyamat megjeleníti a záró visszajelzést', async () => {
  renderScenario(EX_AI_SCENARIO);

  // Opening message from the start fixture.
  expect(await screen.findByText('Welcome! What can I get you?')).toBeInTheDocument();
  expect(
    screen.getByText(/Opcionális \(nem kötelező a lecke teljesítéséhez\)/),
  ).toBeInTheDocument();

  // Send one message; the AI reply fixture appears.
  const field = screen.getByLabelText('Üzeneted');
  fireEvent.change(field, { target: { value: 'I would like a coffee.' } });
  fireEvent.click(screen.getByRole('button', { name: 'Küldés' }));

  expect(await screen.findByText('Sure, anything else?')).toBeInTheDocument();

  // Complete the scenario; the closing feedback fixture renders.
  fireEvent.click(screen.getByRole('button', { name: 'Beszélgetés lezárása' }));

  expect(await screen.findByText('Great conversation!')).toBeInTheDocument();
  expect(screen.getByText('Teljesítve')).toBeInTheDocument();
  expect(screen.getByText('Szerzett pont: 27')).toBeInTheDocument();
});

test('502 ai_unavailable hiba esetén újrapróbálható üzenetet mutat', async () => {
  server.use(
    http.post('/api/v1/ai-scenarios/:exerciseId/sessions', () =>
      HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Bad Gateway',
          status: 502,
          code: 'ai_unavailable',
          detail: 'AI service unavailable',
        },
        { status: 502 },
      ),
    ),
  );

  renderScenario(EX_AI_SCENARIO);

  expect(
    await screen.findByText(/Az AI szolgáltatás átmenetileg nem elérhető/),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Újra' })).toBeInTheDocument();
});
