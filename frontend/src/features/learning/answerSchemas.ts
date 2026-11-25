// Zod schemas per auto exercise type. These validate the form for UX only
// (that the user supplied a plausible answer); they do NOT judge correctness.
import { z } from 'zod';

export const multipleChoiceSchema = z.object({
  selectedOptionIds: z
    .array(z.string())
    .min(1, 'Válassz legalább egy opciót.'),
});

export const trueFalseSchema = z.object({
  answer: z.boolean({ message: 'Válassz igaz vagy hamis közül.' }),
});

export const matchingSchema = z.object({
  pairs: z
    .array(
      z.object({
        leftId: z.string(),
        rightId: z.string().min(1, 'Minden elemhez válassz párt.'),
      }),
    )
    .min(1),
});

export const oddOneOutSchema = z.object({
  selectedItemId: z.string().min(1, 'Válassz egy elemet.'),
});

export const errorFindingSchema = z.object({
  markedTokenIndexes: z
    .array(z.number())
    .min(1, 'Jelölj meg legalább egy szót.'),
});

export const translationSchema = z.object({
  text: z.string().trim().min(1, 'Írd be a fordítást.'),
});

export type MultipleChoiceForm = z.infer<typeof multipleChoiceSchema>;
export type TrueFalseForm = z.infer<typeof trueFalseSchema>;
export type MatchingForm = z.infer<typeof matchingSchema>;
export type OddOneOutForm = z.infer<typeof oddOneOutSchema>;
export type ErrorFindingForm = z.infer<typeof errorFindingSchema>;
export type TranslationForm = z.infer<typeof translationSchema>;
