import { expect, test } from 'vitest';
import type { AttemptResponse } from '../features/learning/types';
import { EX_MATCHING, EX_MULTIPLE_CHOICE } from './mswServer';

// Bizonyítja, hogy az MSW attempt-mock VALÓDIAN kiértékeli a beküldött választ
// (nem fogad el vakon mindent): a helyes beküldés correct:true, a helytelen
// (pl. felcserélt MATCHING párok) correct:false.
async function submit(exerciseId: string, answer: unknown): Promise<AttemptResponse> {
  const res = await fetch(`/api/v1/exercises/${exerciseId}/attempts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer }),
  });
  return (await res.json()) as AttemptResponse;
}

test('helyes MATCHING párosítás → correct:true, Szép munka!', async () => {
  const result = await submit(EX_MATCHING, {
    pairs: [
      { leftId: 'l1', rightId: 'r1' },
      { leftId: 'l2', rightId: 'r2' },
    ],
  });
  expect(result.correct).toBe(true);
  expect(result.feedback).toBe('Szép munka!');
  expect(result.scoreRatio).toBe(1);
});

test('helytelen MATCHING (felcserélt párok) → correct:false, Nem egészen...', async () => {
  const result = await submit(EX_MATCHING, {
    pairs: [
      { leftId: 'l1', rightId: 'r2' },
      { leftId: 'l2', rightId: 'r1' },
    ],
  });
  expect(result.correct).toBe(false);
  expect(result.feedback).toBe('Nem egészen, próbáld újra!');
  expect(result.scoreRatio).toBe(0);
});

test('helytelen MULTIPLE_CHOICE (rossz opció) → correct:false', async () => {
  const result = await submit(EX_MULTIPLE_CHOICE, { selectedOptionIds: ['b'] });
  expect(result.correct).toBe(false);
});
