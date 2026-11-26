import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Container,
  LinearProgress,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useEnrollments, useLessons } from './hooks';
import type { LessonStatus } from './types';

const STATUS_LABEL: Record<LessonStatus, string> = {
  NOT_STARTED: 'Nincs elkezdve',
  IN_PROGRESS: 'Folyamatban',
  COMPLETED: 'Befejezve',
};

function statusColor(status: LessonStatus): 'default' | 'warning' | 'success' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'IN_PROGRESS') return 'warning';
  return 'default';
}

// The lesson list has no per-exercise progress, so the bar reflects the status
// the backend already returns: done = full, in progress = partial, otherwise
// empty. It is a visual cue, not an exact percentage.
function statusProgress(status: LessonStatus): number {
  if (status === 'COMPLETED') return 100;
  if (status === 'IN_PROGRESS') return 40;
  return 0;
}

// Lesson list (/lessons): uses the active language from enrollments, loads its
// lessons, and renders status chips + locked/unlocked affordances.
export function LessonListPage() {
  const enrollments = useEnrollments();
  const activeLanguageId = enrollments.data?.activeLanguageId ?? null;
  const lessons = useLessons(activeLanguageId);

  const isLoading = enrollments.isLoading || lessons.isLoading;
  const isError = enrollments.isError || lessons.isError;

  const activeLanguageName =
    enrollments.data?.enrollments.find((e) => e.languageId === activeLanguageId)
      ?.languageName ?? null;
  const total = lessons.data?.length ?? 0;
  const completed =
    lessons.data?.filter((l) => l.status === 'COMPLETED').length ?? 0;
  const completedPercent = total > 0 ? Math.round((completed / total) * 100) : 0;

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Leckék
      </Typography>

      {!isLoading && !isError && activeLanguageId && total > 0 && (
        <Paper variant="outlined" sx={{ p: 2, mt: 1 }}>
          <Stack spacing={1}>
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="center"
              useFlexGap
              flexWrap="wrap"
            >
              <Typography variant="subtitle1">
                {activeLanguageName
                  ? `Aktív nyelv: ${activeLanguageName}`
                  : 'Haladás'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {completed}/{total} lecke kész ({completedPercent}%)
              </Typography>
            </Stack>
            <LinearProgress
              variant="determinate"
              value={completedPercent}
              color="success"
              aria-label="Összesített haladás"
              sx={{ height: 8, borderRadius: 4 }}
            />
          </Stack>
        </Paper>
      )}

      <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress aria-label="Betöltés" />
          </Box>
        )}

        {isError && !isLoading && (
          <Alert severity="error">
            Nem sikerült betölteni a leckéket. Próbáld újra később.
          </Alert>
        )}

        {!isLoading && !isError && !activeLanguageId && (
          <Alert severity="info">
            Nincs aktív nyelv. Előbb válassz nyelvet a Nyelvek oldalon.
          </Alert>
        )}

        {!isLoading && !isError && activeLanguageId && (lessons.data?.length ?? 0) === 0 && (
          <Alert severity="info">Ehhez a nyelvhez még nincs lecke.</Alert>
        )}

        {!isLoading && !isError && (lessons.data?.length ?? 0) > 0 && (
          <List>
            {lessons.data!.map((lesson) => {
              const content = (
                <ListItemText
                  primary={`${lesson.orderIndex}. ${lesson.title}`}
                  secondary={
                    <Box component="span" sx={{ display: 'block', mt: 0.5 }}>
                      <Stack
                        component="span"
                        direction="row"
                        spacing={1}
                        alignItems="center"
                      >
                        <Chip
                          component="span"
                          size="small"
                          label={STATUS_LABEL[lesson.status]}
                          color={statusColor(lesson.status)}
                        />
                        {!lesson.unlocked && (
                          <Chip component="span" size="small" label="Zárolva" />
                        )}
                      </Stack>
                      <LinearProgress
                        variant="determinate"
                        value={statusProgress(lesson.status)}
                        color={lesson.status === 'COMPLETED' ? 'success' : 'primary'}
                        aria-label={`Haladás: ${STATUS_LABEL[lesson.status]}`}
                        sx={{ mt: 1, height: 6, borderRadius: 3 }}
                      />
                    </Box>
                  }
                />
              );

              if (!lesson.unlocked) {
                return (
                  <ListItemButton
                    key={lesson.id}
                    divider
                    disabled
                    aria-disabled="true"
                  >
                    {content}
                  </ListItemButton>
                );
              }

              return (
                <ListItemButton
                  key={lesson.id}
                  divider
                  component={RouterLink}
                  to={`/lessons/${lesson.id}`}
                >
                  {content}
                </ListItemButton>
              );
            })}
          </List>
        )}
      </Paper>
    </Container>
  );
}
