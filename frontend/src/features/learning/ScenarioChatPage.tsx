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
  TextField,
  Typography,
} from '@mui/material';
import {
  Link as RouterLink,
  useNavigate,
  useParams,
} from 'react-router-dom';
import { ApiError } from '../../api/client';
import {
  useCompleteScenario,
  useLesson,
  useSendScenarioMessage,
  useStartScenario,
} from './hooks';
import { exerciseRoute, nextExercise } from './navigation';
import type { ScenarioCompleteResponse, Uuid } from './types';

interface ChatTurn {
  role: 'user' | 'ai';
  text: string;
}

// Maps an error to a user-facing message. A 502 with code 'ai_unavailable'
// is a retryable outage (no points lost); other errors get a generic message.
function errorMessage(error: unknown): { message: string; retryable: boolean } {
  if (error instanceof ApiError && error.code === 'ai_unavailable') {
    return {
      message:
        'Az AI szolgáltatás átmenetileg nem elérhető. Próbáld újra később – pontot nem veszítesz.',
      retryable: true,
    };
  }
  return { message: 'Hiba történt. Próbáld újra.', retryable: false };
}

// AI scenario chat. Reachable both with lesson context
// (/lessons/:lessonId/scenario/:exerciseId) and without it (legacy
// /scenario/:exerciseId). Starts a session, exchanges turn-by-turn messages,
// then completes to get the closing feedback. When lesson context is present,
// the closing feedback offers "back to lesson" / "next exercise" navigation,
// matching the exercise player (U12). Keyboard accessible.
export function ScenarioChatPage() {
  const { exerciseId, lessonId } = useParams<{
    exerciseId: string;
    lessonId?: string;
  }>();
  const navigate = useNavigate();

  // Lesson context (optional): the lesson's exercise list drives the
  // "next exercise" target. Only fetched when the lesson id is in the route.
  const lesson = useLesson(lessonId);

  const start = useStartScenario(exerciseId as Uuid);
  const sessionId: Uuid | undefined = start.data?.sessionId;
  const send = useSendScenarioMessage(sessionId);
  const complete = useCompleteScenario(sessionId);

  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [draft, setDraft] = useState('');
  const [turnCount, setTurnCount] = useState(0);
  const [result, setResult] = useState<ScenarioCompleteResponse | null>(null);
  const startedRef = useRef(false);

  // Auto-start the session once on mount.
  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    start.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openingMessage = start.data?.openingMessage;

  const next =
    lessonId && lesson.data && exerciseId
      ? nextExercise(lesson.data.exercises, exerciseId)
      : null;

  const onSend = () => {
    const message = draft.trim();
    if (!message || !sessionId) return;
    setTurns((prev) => [...prev, { role: 'user', text: message }]);
    setDraft('');
    send.mutate(message, {
      onSuccess: (data) => {
        setTurns((prev) => [...prev, { role: 'ai', text: data.aiMessage }]);
        setTurnCount(data.turnCount);
      },
    });
  };

  const onComplete = () => {
    if (!sessionId) return;
    complete.mutate(undefined, {
      onSuccess: (data) => setResult(data),
    });
  };

  if (!exerciseId) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">Hiányzó feladatazonosító.</Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      {lessonId ? (
        <Button
          component={RouterLink}
          to={`/lessons/${lessonId}`}
          size="small"
          sx={{ mb: 2 }}
        >
          ← Vissza a leckéhez
        </Button>
      ) : (
        <Button component={RouterLink} to="/lessons" size="small" sx={{ mb: 2 }}>
          ← Vissza a leckékhez
        </Button>
      )}
      <Typography variant="h5" component="h1" gutterBottom>
        AI beszélgetős feladat
      </Typography>
      <Chip
        label="Opcionális (nem kötelező a lecke teljesítéséhez)"
        size="small"
        sx={{ mb: 2 }}
      />

      {start.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Beszélgetés indítása" />
        </Box>
      )}

      {start.isError && (
        <Alert
          severity={errorMessage(start.error).retryable ? 'warning' : 'error'}
          action={
            <Button color="inherit" size="small" onClick={() => start.mutate()}>
              Újra
            </Button>
          }
        >
          {errorMessage(start.error).message}
        </Alert>
      )}

      {start.isSuccess && (
        <>
          <Paper variant="outlined" sx={{ p: 3, mb: 2 }}>
            <Stack spacing={2}>
              {openingMessage && (
                <Box>
                  <Typography variant="overline" color="text.secondary">
                    AI
                  </Typography>
                  <Typography>{openingMessage}</Typography>
                </Box>
              )}
              {turns.map((turn, i) => (
                <Box key={i}>
                  <Typography variant="overline" color="text.secondary">
                    {turn.role === 'user' ? 'Te' : 'AI'}
                  </Typography>
                  <Typography>{turn.text}</Typography>
                </Box>
              ))}
            </Stack>
          </Paper>

          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Fordulók: {turnCount}
          </Typography>

          {send.isError && (
            <Alert
              severity={errorMessage(send.error).retryable ? 'warning' : 'error'}
              sx={{ mb: 2 }}
            >
              {errorMessage(send.error).message}
            </Alert>
          )}

          {!result && (
            <Stack direction="row" spacing={1} alignItems="flex-start">
              <TextField
                label="Üzeneted"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    onSend();
                  }
                }}
                fullWidth
                multiline
                minRows={1}
                disabled={send.isPending || complete.isPending}
              />
              <Button
                variant="contained"
                onClick={onSend}
                disabled={!draft.trim() || send.isPending || complete.isPending}
              >
                Küldés
              </Button>
            </Stack>
          )}

          {!result && (
            <Button
              variant="outlined"
              sx={{ mt: 2 }}
              onClick={onComplete}
              disabled={complete.isPending}
            >
              Beszélgetés lezárása
            </Button>
          )}

          {complete.isError && (
            <Alert
              severity={
                errorMessage(complete.error).retryable ? 'warning' : 'error'
              }
              sx={{ mt: 2 }}
            >
              {errorMessage(complete.error).message}
            </Alert>
          )}

          {result && (
            <Paper variant="outlined" sx={{ p: 3, mt: 3 }}>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip
                    label={result.completed ? 'Teljesítve' : 'Nem teljesült'}
                    color={result.completed ? 'success' : 'default'}
                  />
                  <Typography>
                    Eredmény: {Math.round(result.scoreRatio * 100)}%
                  </Typography>
                </Stack>
                <Typography>{result.feedback}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Szerzett pont: {result.points}
                </Typography>

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
