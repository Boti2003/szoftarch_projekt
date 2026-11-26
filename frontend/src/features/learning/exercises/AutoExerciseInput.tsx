import { useState } from 'react';
import { Alert } from '@mui/material';
import { MultipleChoiceExercise } from './MultipleChoiceExercise';
import { TrueFalseExercise } from './TrueFalseExercise';
import { MatchingExercise } from './MatchingExercise';
import { OddOneOutExercise } from './OddOneOutExercise';
import { ErrorFindingExercise } from './ErrorFindingExercise';
import { TranslationExercise } from './TranslationExercise';
import {
  errorFindingSchema,
  matchingSchema,
  multipleChoiceSchema,
  oddOneOutSchema,
  translationSchema,
  trueFalseSchema,
} from '../answerSchemas';
import type {
  ErrorFindingContent,
  ExerciseAnswer,
  ExerciseContent,
  ExerciseType,
  MatchingContent,
  MatchingPair,
  MultipleChoiceContent,
  OddOneOutContent,
  TranslationContent,
  TrueFalseContent,
} from '../types';

// Mutable per-type answer state. Collected into the API answer payload on submit.
export interface AnswerState {
  selectedOptionIds: string[];
  trueFalse: boolean | null;
  pairs: MatchingPair[];
  selectedItemId: string | null;
  markedTokenIndexes: number[];
  translationText: string;
}

export const EMPTY_ANSWER_STATE: AnswerState = {
  selectedOptionIds: [],
  trueFalse: null,
  pairs: [],
  selectedItemId: null,
  markedTokenIndexes: [],
  translationText: '',
};

// Validates the collected state against the per-type Zod schema and returns the
// API answer payload, or a UX error message. Never judges correctness.
// A `type` a prezentáció külső típusa (a backend NEM teszi a content-be a type-ot,
// ezért azt itt kapjuk; fallback a content.type a mock/régi fixture-ökhöz).
export function buildAutoAnswer(
  content: ExerciseContent,
  state: AnswerState,
  type?: ExerciseType,
): { answer: ExerciseAnswer } | { error: string } {
  switch (type ?? content.type) {
    case 'MULTIPLE_CHOICE': {
      const parsed = multipleChoiceSchema.safeParse({
        selectedOptionIds: state.selectedOptionIds,
      });
      return parsed.success
        ? { answer: parsed.data }
        : { error: parsed.error.issues[0].message };
    }
    case 'TRUE_FALSE': {
      const parsed = trueFalseSchema.safeParse({ answer: state.trueFalse });
      return parsed.success
        ? { answer: parsed.data }
        : { error: parsed.error.issues[0].message };
    }
    case 'MATCHING': {
      const parsed = matchingSchema.safeParse({ pairs: state.pairs });
      if (!parsed.success) return { error: parsed.error.issues[0].message };
      if (parsed.data.pairs.length < (content as MatchingContent).leftItems.length) {
        return { error: 'Minden elemhez válassz párt.' };
      }
      return { answer: parsed.data };
    }
    case 'ODD_ONE_OUT': {
      const parsed = oddOneOutSchema.safeParse({
        selectedItemId: state.selectedItemId ?? '',
      });
      return parsed.success
        ? { answer: parsed.data }
        : { error: parsed.error.issues[0].message };
    }
    case 'ERROR_FINDING': {
      const parsed = errorFindingSchema.safeParse({
        markedTokenIndexes: state.markedTokenIndexes,
      });
      return parsed.success
        ? { answer: parsed.data }
        : { error: parsed.error.issues[0].message };
    }
    case 'TRANSLATION': {
      const parsed = translationSchema.safeParse({ text: state.translationText });
      return parsed.success
        ? { answer: parsed.data }
        : { error: parsed.error.issues[0].message };
    }
    default:
      return { error: 'Ez a feladattípus itt nem oldható meg.' };
  }
}

interface Props {
  content: ExerciseContent;
  type?: ExerciseType;
  state: AnswerState;
  onChange: (state: AnswerState) => void;
  disabled?: boolean;
}

// Branches on the content type to render the matching input component. The
// answer state is owned by the caller so it can build + submit the payload.
// Never renders or derives the correct answer.
export function AutoExerciseInput({ content, type, state, onChange, disabled }: Props) {
  switch (type ?? content.type) {
    case 'MULTIPLE_CHOICE':
      return (
        <MultipleChoiceExercise
          content={content as MultipleChoiceContent}
          value={state.selectedOptionIds}
          onChange={(selectedOptionIds) => onChange({ ...state, selectedOptionIds })}
          disabled={disabled}
        />
      );
    case 'TRUE_FALSE':
      return (
        <TrueFalseExercise
          content={content as TrueFalseContent}
          value={state.trueFalse}
          onChange={(trueFalse) => onChange({ ...state, trueFalse })}
          disabled={disabled}
        />
      );
    case 'MATCHING':
      return (
        <MatchingExercise
          content={content as MatchingContent}
          value={state.pairs}
          onChange={(pairs) => onChange({ ...state, pairs })}
          disabled={disabled}
        />
      );
    case 'ODD_ONE_OUT':
      return (
        <OddOneOutExercise
          content={content as OddOneOutContent}
          value={state.selectedItemId}
          onChange={(selectedItemId) => onChange({ ...state, selectedItemId })}
          disabled={disabled}
        />
      );
    case 'ERROR_FINDING':
      return (
        <ErrorFindingExercise
          content={content as ErrorFindingContent}
          value={state.markedTokenIndexes}
          onChange={(markedTokenIndexes) => onChange({ ...state, markedTokenIndexes })}
          disabled={disabled}
        />
      );
    case 'TRANSLATION':
      return (
        <TranslationExercise
          content={content as TranslationContent}
          value={state.translationText}
          onChange={(translationText) => onChange({ ...state, translationText })}
          disabled={disabled}
        />
      );
    default:
      return (
        <Alert severity="info">Ez a feladattípus máshol oldható meg.</Alert>
      );
  }
}

// Convenience hook for components that own a single exercise's answer state.
export function useAnswerState() {
  return useState<AnswerState>(EMPTY_ANSWER_STATE);
}
