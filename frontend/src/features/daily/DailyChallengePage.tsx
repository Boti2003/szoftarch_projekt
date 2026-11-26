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
import { useEnrollments } from '../learning/hooks';
import {
  AutoExerciseInput,
  EMPTY_ANSWER_STATE,
  buildAutoAnswer,
} from '../learning/exercises/AutoExerciseInput';
import type {
  DailyOwnStatus,
  ExercisePresentationResponse,
  Uuid,
} from '../learning/types';
import { useDailyToday, useSubmitDailyAttempt } from './hooks';

const STATUS_LABEL: Record<DailyOwnStatus['status'], string> = {
  NOT_STARTED: 'Nincs elkezdve',
  IN_PROGRESS: 'Folyamatban',
  COMPLETED: 'Teljesítve',
};

function statusColor(
  status: DailyOwnStatus['status'],
): 'default' | 'warning' | 'success' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'IN_PROGRESS') return 'warning';
  return 'default';
}

// One daily exercise: owns its answer state, validates for UX, submits the
// attempt scoped to the active language, and shows the feedback. Reuses the
// shared auto-exercise input components. Never reveals the correct answer.
function DailyExerciseCard({
  presentation,
  languageId,
}: {
  presentation: ExercisePresentationResponse;
  languageId: Uuid;
}) {
  const submit = useSubmitDailyAttempt(languageId);
  const [state, setState] = useState(EMPTY_ANSWER_STATE);
  const [validationError, setValidationError] = useState<string | null>(null);

  const disabled = submit.isPending || submit.isSuccess;

  const onSubmit = () => {
    const result = buildAutoAnswer(presentation.content, state, presentation.type);
    if ('error' in result) {
      setValidationError(result.error);
      return;
    }
    setValidationError(null);
    submit.mutate({ exerciseId: presentation.exerciseId, answer: result.answer });
  };

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        {presentation.prompt}
      </Typography>

      <AutoExerciseInput
        content={presentation.content}
        type={presentation.type}
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

      {submit.isSuccess && submit.data && (
        <Stack spacing={1} sx={{ mt: 3 }}>
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
          {!submit.data.correct && (
            <Button
              variant="contained"
              color="primary"
              onClick={() => {
                setState(EMPTY_ANSWER_STATE);
                setValidationError(null);
                submit.reset();
              }}
              sx={{ alignSelf: 'flex-start' }}
            >
              Próbáld újra
            </Button>
          )}
        </Stack>
      )}
    </Paper>
  );
}

// Daily-challenge page (/daily): resolves the active language from enrollments,
// loads today's challenge (exercises + ownStatus), lets the user solve each
// exercise, and shows completion/bonus status.
export function DailyChallengePage() {
  const enrollments = useEnrollments();
  const activeLanguageId = enrollments.data?.activeLanguageId ?? null;
  const daily = useDailyToday(activeLanguageId);

  const isLoading = enrollments.isLoading || daily.isLoading;
  const isError = enrollments.isError || daily.isError;

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Napi kihívás
      </Typography>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Betöltés" />
        </Box>
      )}

      {isError && !isLoading && (
        <Alert severity="error">
          Nem sikerült betölteni a napi kihívást. Próbáld újra később.
        </Alert>
      )}

      {!isLoading && !isError && !activeLanguageId && (
        <Alert severity="info">
          Nincs aktív nyelv. Előbb válassz nyelvet a Nyelvek oldalon.
        </Alert>
      )}

      {!isLoading && !isError && activeLanguageId && daily.data && (
        <Stack spacing={3}>
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">
                Mai dátum: {daily.data.date}
              </Typography>
              <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                <Chip
                  label={STATUS_LABEL[daily.data.ownStatus.status]}
                  color={statusColor(daily.data.ownStatus.status)}
                />
                <Typography>
                  Helyes válaszok: {daily.data.ownStatus.correctCount}
                </Typography>
                {daily.data.ownStatus.bonusAwarded && (
                  <Chip label="Bónusz megszerezve" color="secondary" />
                )}
              </Stack>
            </Stack>
          </Paper>

          {daily.data.exercises.length === 0 ? (
            <Alert severity="info">Ma nincs feladat a kihívásban.</Alert>
          ) : (
            daily.data.exercises.map((exercise) => (
              <DailyExerciseCard
                key={exercise.exerciseId}
                presentation={exercise}
                languageId={activeLanguageId}
              />
            ))
          )}
        </Stack>
      )}
    </Container>
  );
}
