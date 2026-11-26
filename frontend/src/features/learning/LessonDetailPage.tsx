import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Chip,
  CircularProgress,
  Container,
  Divider,
  Link,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import Markdown from 'react-markdown';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useEnrollments, useLesson, useLessons } from './hooks';
import { exerciseRoute, nextLesson } from './navigation';
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
// list with per-exercise navigation into the player, plus breadcrumb and
// next-lesson navigation.
export function LessonDetailPage() {
  const { id } = useParams<{ id: string }>();
  const lesson = useLesson(id);
  const enrollments = useEnrollments();
  const lessons = useLessons(enrollments.data?.activeLanguageId ?? null);

  const next = id && lessons.data ? nextLesson(lessons.data, id) : null;

  return (
    <Container maxWidth="md" sx={{ py: 6 }}>
      <Breadcrumbs aria-label="Útvonal" sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/lessons" underline="hover" color="inherit">
          Leckék
        </Link>
        <Typography color="text.primary">
          {lesson.data ? lesson.data.title : 'Lecke'}
        </Typography>
      </Breadcrumbs>

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
                const to = exerciseRoute(
                  lesson.data!.lessonId,
                  exercise.exerciseId,
                  exercise.type,
                );
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

          <Divider sx={{ mt: 2 }} />
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={1}
            justifyContent="space-between"
            sx={{ mt: 3 }}
          >
            <Button
              variant="outlined"
              component={RouterLink}
              to="/lessons"
            >
              ← Vissza a leckelistához
            </Button>

            {next?.lesson && !next.locked && (
              <Button
                variant="contained"
                component={RouterLink}
                to={`/lessons/${next.lesson.id}`}
              >
                Következő lecke →
              </Button>
            )}

            {next?.lesson && next.locked && (
              <Box>
                <Button variant="contained" disabled>
                  Következő lecke zárolva
                </Button>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  display="block"
                  sx={{ mt: 0.5 }}
                >
                  Előbb teljesítsd ezt a leckét legalább 70%-ra.
                </Typography>
              </Box>
            )}
          </Stack>
        </>
      )}
    </Container>
  );
}
