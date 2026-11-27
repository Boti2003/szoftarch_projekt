import { useEffect, useRef, useState } from 'react';
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
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useExercise, useSubmitAttempt } from './hooks';
import { exerciseRoute, nextExercise } from './navigation';
import {
  AutoExerciseInput,
  EMPTY_ANSWER_STATE,
  buildAutoAnswer,
} from './exercises/AutoExerciseInput';
import type {
  ExerciseMetadataResponse,
  ExercisePresentationResponse,
  PointAwardStatus,
  Uuid,
} from './types';

interface Props {
  exerciseId: Uuid;
  // Lesson context lets the player offer "next exercise" / "back to lesson"
  // navigation and invalidate the right progress queries after a submit.
  // Optional so the component keeps working without it.
  lessonId?: Uuid;
  languageId?: Uuid | null;
  exercises?: ExerciseMetadataResponse[];
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
export function ExercisePlayer({
  exerciseId,
  lessonId,
  languageId,
  exercises,
}: Props) {
  const navigate = useNavigate();
  const exercise = useExercise(exerciseId);
  const submit = useSubmitAttempt(exerciseId, { lessonId, languageId });
  const [state, setState] = useState(EMPTY_ANSWER_STATE);
  const [validationError, setValidationError] = useState<string | null>(null);

  // Reset the answer/feedback state when the exercise changes so moving to the
  // next exercise starts fresh (the route param can change in place).
  const resetKey = useRef(exerciseId);
  useEffect(() => {
    if (resetKey.current !== exerciseId) {
      resetKey.current = exerciseId;
      setState(EMPTY_ANSWER_STATE);
      setValidationError(null);
      submit.reset();
    }
  }, [exerciseId, submit]);

  const presentation = exercise.data;
  const disabled = submit.isPending || submit.isSuccess;

  const next =
    lessonId && exercises ? nextExercise(exercises, exerciseId) : null;

  const onSubmit = () => {
    if (!presentation) return;
    const result = buildAutoAnswer(presentation.content, state, presentation.type);
    if ('error' in result) {
      setValidationError(result.error);
      return;
    }
    setValidationError(null);
    submit.mutate(result.answer);
  };

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      {lessonId && (
        <Button
          component={RouterLink}
          to={`/lessons/${lessonId}`}
          size="small"
          sx={{ mb: 2 }}
        >
          ← Vissza a leckéhez
        </Button>
      )}

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

                {lessonId && (
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1}
                    sx={{ pt: 1 }}
                  >
                    {next ? (
                      <Button
                        variant="contained"
                        onClick={() =>
                          navigate(
                            exerciseRoute(
                              lessonId,
                              next.exerciseId,
                              next.type,
                            ),
                          )
                        }
                      >
                        Következő feladat →
                      </Button>
                    ) : (
                      <Alert severity="success" sx={{ flexGrow: 1 }}>
                        Ez volt a lecke utolsó feladata. Térj vissza a leckéhez a
                        folytatáshoz.
                      </Alert>
                    )}
                    <Button
                      variant="outlined"
                      component={RouterLink}
                      to={`/lessons/${lessonId}`}
                    >
                      Vissza a leckéhez
                    </Button>
                  </Stack>
                )}
              </Stack>
            </Paper>
          )}
        </>
      )}
    </Container>
  );
}
