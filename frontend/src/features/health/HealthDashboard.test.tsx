import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { expect, test } from 'vitest';
import { HealthDashboard } from './HealthDashboard';

test('a health dashboard megjeleníti a service-ek státuszát', async () => {
  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <HealthDashboard />
    </QueryClientProvider>,
  );

  expect(await screen.findByText('Rendszerállapot')).toBeInTheDocument();
  // Az MSW UP státuszt ad vissza minden komponensre.
  expect(await screen.findByText('Identity')).toBeInTheDocument();
});
