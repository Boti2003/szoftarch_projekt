import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Divider,
  LinearProgress,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import {
  useAcceptDuel,
  useDuel,
  useDuelExercises,
  useRejectDuel,
  useSubmitDuelAnswer,
  useUserProfiles,
} from './hooks';
import { userLabel } from './format';
import { DuelExerciseInput } from './DuelExerciseInput';
import type { DuelDetailView, DuelStatus } from './types';

const STATUS_LABELS: Record<DuelStatus, string> = {
  PENDING: 'Függőben',
  ACCEPTED: 'Elfogadva',
  IN_PROGRESS: 'Folyamatban',
  COMPLETED: 'Befejezve',
  REJECTED: 'Elutasítva',
  EXPIRED: 'Lejárt',
};

const TOTAL_EXERCISES = 5;

// Duel detail page (/duels/:id): shows the duel status, lets the opponent
// accept/reject a pending challenge, plays the exercises while IN_PROGRESS,
// and shows the gated result (opponent detail appears only once the viewer has
// finished) with the winner once the duel is COMPLETED.
export function DuelDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const duel = useDuel(id);
  const accept = useAcceptDuel();
  const reject = useRejectDuel();
  // Resolve the counterpart id to a display name (empty until the duel loads).
  const d0 = duel.data;
  const counterpartForProfiles =
    d0 && user ? (user.id === d0.challengerId ? d0.opponentId : d0.challengerId) : null;
  const profiles = useUserProfiles(counterpartForProfiles ? [counterpartForProfiles] : []);

  if (duel.isLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Betöltés" />
        </Box>
      </Container>
    );
  }
  if (duel.isError || !duel.data) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">Nem sikerült betölteni a párbajt.</Alert>
        <Button sx={{ mt: 2 }} onClick={() => navigate('/duels')}>
          Vissza a párbajokhoz
        </Button>
      </Container>
    );
  }

  const d = duel.data;
  const isOpponent = user?.id === d.opponentId;
  const counterpartId = user?.id === d.challengerId ? d.opponentId : d.challengerId;
  const selfCompleted = d.self.status === 'COMPLETED';

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Button size="small" onClick={() => navigate('/duels')} sx={{ mb: 2 }}>
        ← Párbajok
      </Button>

      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h4" component="h1">
          Párbaj
        </Typography>
        <Chip label={STATUS_LABELS[d.status]} color="primary" />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 3 }}>
        Ellenfél: {userLabel(counterpartId, profiles.get(counterpartId)?.displayName)}
      </Typography>

      {/* Pending challenge the opponent can accept/reject */}
      {d.status === 'PENDING' && isOpponent && (
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
          <Typography gutterBottom>Elfogadod a kihívást?</Typography>
          <Stack direction="row" spacing={1}>
            <Button
              variant="contained"
              disabled={accept.isPending}
              onClick={() => accept.mutate(d.id)}
            >
              Elfogadás
            </Button>
            <Button
              color="error"
              disabled={reject.isPending}
              onClick={() => reject.mutate(d.id, { onSuccess: () => navigate('/duels') })}
            >
              Elutasítás
            </Button>
          </Stack>
        </Paper>
      )}

      {d.status === 'PENDING' && !isOpponent && (
        <Alert severity="info" sx={{ mb: 3 }}>
          A kihívás elküldve. Várakozás az ellenfél válaszára.
        </Alert>
      )}

      {d.status === 'REJECTED' && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          A párbajt elutasították.
        </Alert>
      )}

      {d.status === 'EXPIRED' && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          A párbaj lejárt.
        </Alert>
      )}

      {/* Play area while the duel is active and the viewer has not finished */}
      {(d.status === 'ACCEPTED' || d.status === 'IN_PROGRESS') && !selfCompleted && (
        <DuelPlay duelId={d.id} answeredCount={d.self.answeredCount} />
      )}

      {/* Waiting for the opponent after the viewer finished but duel not complete */}
      {d.status === 'IN_PROGRESS' && selfCompleted && (
        <Alert severity="info" sx={{ mb: 3 }}>
          Befejezted a párbajt. Várakozás az ellenfélre – az eredmény akkor látszik,
          ha mindketten végeztetek.
        </Alert>
      )}

      {/* Result: self always visible; opponent gated server-side until self done */}
      <ResultCard duel={d} />
    </Container>
  );
}

function DuelPlay({ duelId, answeredCount }: { duelId: string; answeredCount: number }) {
  const exercises = useDuelExercises(duelId, true);
  const submit = useSubmitDuelAnswer(duelId);
  const [pendingAnswer, setPendingAnswer] = useState<Record<string, unknown> | null>(null);
  const [lastCorrect, setLastCorrect] = useState<boolean | null>(null);

  if (exercises.isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress aria-label="Feladatok betöltése" />
      </Box>
    );
  }
  if (exercises.isError || !exercises.data) {
    return <Alert severity="error">Nem sikerült betölteni a feladatokat.</Alert>;
  }

  // The backend advances answeredCount; show the next unanswered exercise.
  const current = exercises.data[answeredCount];
  const progress = Math.min(answeredCount, TOTAL_EXERCISES);

  if (!current) {
    return (
      <Alert severity="success" sx={{ mb: 3 }}>
        Minden feladatot megválaszoltál.
      </Alert>
    );
  }

  const onSubmit = () => {
    if (pendingAnswer === null) {
      return;
    }
    submit.mutate(
      { position: current.position, answer: pendingAnswer },
      {
        onSuccess: (result) => {
          setLastCorrect(result.correct);
          setPendingAnswer(null);
        },
      },
    );
  };

  return (
    <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        {progress + 1}. feladat / {TOTAL_EXERCISES}
      </Typography>
      <LinearProgress
        variant="determinate"
        value={(progress / TOTAL_EXERCISES) * 100}
        sx={{ mb: 2 }}
      />

      <Typography variant="h6" component="h2" gutterBottom>
        {current.prompt}
      </Typography>

      <Box sx={{ mt: 1 }}>
        <DuelExerciseInput exercise={current} onChange={setPendingAnswer} />
      </Box>

      {lastCorrect !== null && (
        <Alert severity={lastCorrect ? 'success' : 'warning'} sx={{ mt: 2 }}>
          {lastCorrect ? 'Helyes válasz!' : 'Nem volt helyes – a párbaj folytatódik.'}
        </Alert>
      )}

      <Box sx={{ mt: 2 }}>
        <Button
          variant="contained"
          onClick={onSubmit}
          disabled={submit.isPending || pendingAnswer === null}
        >
          Beküldés
        </Button>
      </Box>
    </Paper>
  );
}

function ResultCard({ duel }: { duel: DuelDetailView }) {
  const { user } = useAuth();
  const isCompleted = duel.status === 'COMPLETED';

  let outcome: string | null = null;
  if (isCompleted) {
    if (duel.winnerId === null) {
      outcome = 'Döntetlen';
    } else if (duel.winnerId === user?.id) {
      outcome = 'Nyertél! 🎉';
    } else {
      outcome = 'Vesztettél';
    }
  }

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Eredmény
        </Typography>

        {outcome && (
          <Chip
            label={outcome}
            color={duel.winnerId === null ? 'default' : duel.winnerId === user?.id ? 'success' : 'error'}
            sx={{ mb: 2 }}
          />
        )}

        <Stack spacing={1}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography>Te</Typography>
            <Typography fontWeight="bold">
              {duel.self.score} pont · {duel.self.answeredCount}/{TOTAL_EXERCISES}
            </Typography>
          </Box>
          <Divider />
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography>Ellenfél</Typography>
            {duel.opponent ? (
              <Typography fontWeight="bold">
                {duel.opponent.score} pont · {duel.opponent.answeredCount}/{TOTAL_EXERCISES}
              </Typography>
            ) : (
              <Typography color="text.secondary">
                Rejtve, amíg be nem fejezed
              </Typography>
            )}
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}
