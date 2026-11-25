import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { useExercise, useSubmitAttempt } from './hooks';
import { MultipleChoiceExercise } from './exercises/MultipleChoiceExercise';
import { TrueFalseExercise } from './exercises/TrueFalseExercise';
import { MatchingExercise } from './exercises/MatchingExercise';
import { OddOneOutExercise } from './exercises/OddOneOutExercise';
import { ErrorFindingExercise } from './exercises/ErrorFindingExercise';
import {
  errorFindingSchema,
  matchingSchema,
  multipleChoiceSchema,
  oddOneOutSchema,
  trueFalseSchema,
} from './answerSchemas';
import type {
  ExerciseAnswer,
  ExercisePresentationResponse,
  MatchingPair,
  PointAwardStatus,
  Uuid,
} from './types';

interface Props {
  exerciseId: Uuid;
}

const POINT_AWARD_LABEL: Record<PointAwardStatus, string> = {
  NOT_APPLICABLE: 'Nem jár pont',
  PENDING: 'Pont jóváírása folyamatban',
  AWARDED: 'Pont jóváírva',
  FAILED_RETRYABLE: 'A pont jóváírása nem sikerült, később újrapróbálható',
};

// Mutable per-type answer state. Collected into the API answer payload on submit.
interface AnswerState {
  selectedOptionIds: string[];
  trueFalse: boolean | null;
  pairs: MatchingPair[];
  selectedItemId: string | null;
  markedTokenIndexes: number[];
}

const EMPTY_STATE: AnswerState = {
  selectedOptionIds: [],
  trueFalse: null,
  pairs: [],
  selectedItemId: null,
  markedTokenIndexes: [],
};

// Returns the validated answer payload, or an error message for the UX.
function buildAnswer(
  presentation: ExercisePresentationResponse,
  state: AnswerState,
): { answer: ExerciseAnswer } | { error: string } {
  const content = presentation.content;
  switch (content.type) {
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
      if (parsed.data.pairs.length < content.leftItems.length) {
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
    default:
      return { error: 'Ez a feladattípus itt nem oldható meg.' };
  }
}

// Exercise player: fetches the presentation, branches on type to render the
// matching input, validates for UX, submits the attempt, and shows feedback.
// Never renders or derives the correct answer.
export function ExercisePlayer({ exerciseId }: Props) {
  const exercise = useExercise(exerciseId);
  const submit = useSubmitAttempt(exerciseId);
  const [state, setState] = useState<AnswerState>(EMPTY_STATE);
  const [validationError, setValidationError] = useState<string | null>(null);

  const presentation = exercise.data;

  const onSubmit = () => {
    if (!presentation) return;
    const result = buildAnswer(presentation, state);
    if ('error' in result) {
      setValidationError(result.error);
      return;
    }
    setValidationError(null);
    submit.mutate(result.answer);
  };

  const renderInput = (p: ExercisePresentationResponse) => {
    const content = p.content;
    const disabled = submit.isPending || submit.isSuccess;
    switch (content.type) {
      case 'MULTIPLE_CHOICE':
        return (
          <MultipleChoiceExercise
            content={content}
            value={state.selectedOptionIds}
            onChange={(selectedOptionIds) =>
              setState((s) => ({ ...s, selectedOptionIds }))
            }
            disabled={disabled}
          />
        );
      case 'TRUE_FALSE':
        return (
          <TrueFalseExercise
            content={content}
            value={state.trueFalse}
            onChange={(trueFalse) => setState((s) => ({ ...s, trueFalse }))}
            disabled={disabled}
          />
        );
      case 'MATCHING':
        return (
          <MatchingExercise
            content={content}
            value={state.pairs}
            onChange={(pairs) => setState((s) => ({ ...s, pairs }))}
            disabled={disabled}
          />
        );
      case 'ODD_ONE_OUT':
        return (
          <OddOneOutExercise
            content={content}
            value={state.selectedItemId}
            onChange={(selectedItemId) =>
              setState((s) => ({ ...s, selectedItemId }))
            }
            disabled={disabled}
          />
        );
      case 'ERROR_FINDING':
        return (
          <ErrorFindingExercise
            content={content}
            value={state.markedTokenIndexes}
            onChange={(markedTokenIndexes) =>
              setState((s) => ({ ...s, markedTokenIndexes }))
            }
            disabled={disabled}
          />
        );
      default:
        return (
          <Alert severity="info">
            Ez a feladattípus máshol oldható meg.
          </Alert>
        );
    }
  };

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      {exercise.isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Betöltés" />
        </Box>
      )}

      {exercise.isError && !exercise.isLoading && (
        <Alert severity="error">
          Nem sikerült betölteni a feladatot. Próbáld újra később.
        </Alert>
      )}

      {presentation && (
        <>
          <Typography variant="h5" component="h1" gutterBottom>
            {presentation.prompt}
          </Typography>

          <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
            {renderInput(presentation)}

            {validationError && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                {validationError}
              </Alert>
            )}

            {submit.isError && (
              <Alert severity="error" sx={{ mt: 2 }}>
                Nem sikerült beküldeni a választ. Próbáld újra.
              </Alert>
            )}

            {!submit.isSuccess && (
              <Button
                variant="contained"
                sx={{ mt: 3 }}
                onClick={onSubmit}
                disabled={submit.isPending}
              >
                Beküldés
              </Button>
            )}
          </Paper>

          {submit.isSuccess && submit.data && (
            <Paper variant="outlined" sx={{ p: 3, mt: 3 }}>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip
                    label={submit.data.correct ? 'Helyes' : 'Helytelen'}
                    color={submit.data.correct ? 'success' : 'error'}
                  />
                  <Typography>
                    Eredmény: {Math.round(submit.data.scoreRatio * 100)}%
                  </Typography>
                </Stack>
                <Typography>{submit.data.feedback}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {POINT_AWARD_LABEL[submit.data.pointAwardStatus]}
                </Typography>
              </Stack>
            </Paper>
          )}
        </>
      )}
    </Container>
  );
}
