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
import {
  AutoExerciseInput,
  EMPTY_ANSWER_STATE,
  buildAutoAnswer,
} from './exercises/AutoExerciseInput';
import type {
  ExercisePresentationResponse,
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

// AI exercises (TRANSLATION, AI_SCENARIO) are optional for lesson completion.
function isAiExercise(type: ExercisePresentationResponse['type']): boolean {
  return type === 'TRANSLATION' || type === 'AI_SCENARIO';
}

// Exercise player: fetches the presentation, branches on type to render the
// matching input, validates for UX, submits the attempt, and shows feedback.
// Never renders or derives the correct answer.
export function ExercisePlayer({ exerciseId }: Props) {
  const exercise = useExercise(exerciseId);
  const submit = useSubmitAttempt(exerciseId);
  const [state, setState] = useState(EMPTY_ANSWER_STATE);
  const [validationError, setValidationError] = useState<string | null>(null);

  const presentation = exercise.data;
  const disabled = submit.isPending || submit.isSuccess;

  const onSubmit = () => {
    if (!presentation) return;
    const result = buildAutoAnswer(presentation.content, state);
    if ('error' in result) {
      setValidationError(result.error);
      return;
    }
    setValidationError(null);
    submit.mutate(result.answer);
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

          {isAiExercise(presentation.type) && (
            <Chip
              label="Opcionális (nem kötelező a lecke teljesítéséhez)"
              color="default"
              size="small"
              sx={{ mb: 1 }}
            />
          )}

          <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
            <AutoExerciseInput
              content={presentation.content}
              state={state}
              onChange={setState}
              disabled={disabled}
            />

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
