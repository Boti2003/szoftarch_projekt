import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Container,
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

// Lesson list (/lessons): uses the active language from enrollments, loads its
// lessons, and renders status chips + locked/unlocked affordances.
export function LessonListPage() {
  const enrollments = useEnrollments();
  const activeLanguageId = enrollments.data?.activeLanguageId ?? null;
  const lessons = useLessons(activeLanguageId);

  const isLoading = enrollments.isLoading || lessons.isLoading;
  const isError = enrollments.isError || lessons.isError;

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Leckék
      </Typography>

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
                        label={STATUS_LABEL[lesson.status]}
                        color={statusColor(lesson.status)}
                      />
                      {!lesson.unlocked && (
                        <Chip component="span" size="small" label="Zárolva" />
                      )}
                    </Stack>
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
