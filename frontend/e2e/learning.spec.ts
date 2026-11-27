import { expect, test } from '@playwright/test';

// Walking-skeleton happy path against the MSW-mocked app:
// lesson list -> open a lesson -> answer an auto exercise -> feedback/progress.
test('lesson list -> auto answer -> progress', async ({ page }) => {
  await page.goto('/lessons');

  // Lesson list loads; open the first unlocked lesson.
  await page.getByText('1. Alapok').click();

  // Lesson detail: open the MULTIPLE_CHOICE exercise.
  await expect(page.getByText('Feladatok')).toBeVisible();
  await page.getByText('Melyik a helyes?').click();

  // Answer and submit the auto exercise.
  await page.getByLabel('Hello').check();
  await page.getByRole('button', { name: 'Beküldés' }).click();

  // Feedback from the mocked attempt response renders.
  await expect(page.getByText('Szép munka!')).toBeVisible();
  await expect(page.getByText('Helyes')).toBeVisible();
});
