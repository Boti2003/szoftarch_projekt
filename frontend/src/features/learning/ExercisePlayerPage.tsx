import { Alert, Container } from '@mui/material';
import { useParams } from 'react-router-dom';
import { ExercisePlayer } from './ExercisePlayer';
import { useEnrollments, useLesson } from './hooks';

// Route wrapper for /lessons/:id/exercises/:exerciseId — reads the lesson and
// exercise params, loads the lesson so the player knows the exercise order
// (for "next exercise" navigation) and the active language (for list
// invalidation), and hands everything to the player.
export function ExercisePlayerPage() {
  const { id: lessonId, exerciseId } = useParams<{
    id: string;
    exerciseId: string;
  }>();
  const lesson = useLesson(lessonId);
  const enrollments = useEnrollments();

  if (!exerciseId) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">Hiányzó feladatazonosító.</Alert>
      </Container>
    );
  }

  return (
    <ExercisePlayer
      exerciseId={exerciseId}
      lessonId={lessonId}
      languageId={enrollments.data?.activeLanguageId ?? null}
      exercises={lesson.data?.exercises}
    />
  );
}
