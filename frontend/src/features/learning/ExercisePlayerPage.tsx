import { Alert, Container } from '@mui/material';
import { useParams } from 'react-router-dom';
import { ExercisePlayer } from './ExercisePlayer';

// Route wrapper for /lessons/:id/exercises/:exerciseId — reads the exerciseId
// param and hands it to the player.
export function ExercisePlayerPage() {
  const { exerciseId } = useParams<{ exerciseId: string }>();

  if (!exerciseId) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">Hiányzó feladatazonosító.</Alert>
      </Container>
    );
  }

  return <ExercisePlayer exerciseId={exerciseId} />;
}
