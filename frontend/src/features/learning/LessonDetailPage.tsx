import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Container,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import Markdown from 'react-markdown';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useLesson } from './hooks';
import type { ExerciseDifficulty, ExerciseType } from './types';

const DIFFICULTY_LABEL: Record<ExerciseDifficulty, string> = {
  EASY: 'Könnyű',
  MEDIUM: 'Közepes',
  HARD: 'Nehéz',
};

// AI (TRANSLATION, AI_SCENARIO) exercises are optional for lesson completion.
function isAiExercise(type: ExerciseType): boolean {
  return type === 'TRANSLATION' || type === 'AI_SCENARIO';
}

// Lesson detail (/lessons/:id): renders the theory markdown and the exercise
// list with per-exercise navigation into the player.
export function LessonDetailPage() {
  const { id } = useParams<{ id: string }>();
  const lesson = useLesson(id);

  return (
    <Container maxWidth="md" sx={{ py: 6 }}>
      {lesson.isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Betöltés" />
        </Box>
      )}

      {lesson.isError && !lesson.isLoading && (
        <Alert severity="error">
          Nem sikerült betölteni a leckét. Próbáld újra később.
        </Alert>
      )}

      {lesson.data && (
        <>
          <Typography variant="h4" component="h1" gutterBottom>
            {lesson.data.orderIndex}. {lesson.data.title}
          </Typography>

          <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
            <Box sx={{ overflowWrap: 'anywhere' }}>
              <Markdown>{lesson.data.theoryMarkdown}</Markdown>
            </Box>
          </Paper>

          <Typography variant="h5" component="h2" sx={{ mt: 4 }} gutterBottom>
            Feladatok
          </Typography>
          <Divider />

          {lesson.data.exercises.length === 0 ? (
            <Alert severity="info" sx={{ mt: 2 }}>
              Ehhez a leckéhez nincs feladat.
            </Alert>
          ) : (
            <List>
              {lesson.data.exercises.map((exercise) => {
                const to =
                  exercise.type === 'AI_SCENARIO'
                    ? `/scenario/${exercise.exerciseId}`
                    : `/lessons/${lesson.data!.lessonId}/exercises/${exercise.exerciseId}`;
                return (
                  <ListItemButton
                    key={exercise.exerciseId}
                    divider
                    component={RouterLink}
                    to={to}
                  >
                    <ListItemText
                      primary={exercise.prompt}
                      secondary={
                        <Stack
                          component="span"
                          direction="row"
                          spacing={1}
                          alignItems="center"
                          sx={{ mt: 0.5 }}
                        >
                          <Chip
                            component="span"
                            size="small"
                            label={DIFFICULTY_LABEL[exercise.difficulty]}
                          />
                          <Chip
                            component="span"
                            size="small"
                            color={isAiExercise(exercise.type) ? 'default' : 'primary'}
                            label={
                              exercise.requiredForCompletion
                                ? 'Kötelező'
                                : 'Opcionális'
                            }
                          />
                        </Stack>
                      }
                    />
                  </ListItemButton>
                );
              })}
            </List>
          )}
        </>
      )}
    </Container>
  );
}
