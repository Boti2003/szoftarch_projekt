import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { useActivateLanguage, useEnrollments, useLanguages } from './hooks';
import type { EnrollmentResponse, Uuid } from './types';

// Language-selection page (/languages): lists the available languages, shows
// the user's enrollments and which language is active, and lets the user
// activate or switch the active language.
export function LanguagesPage() {
  const languages = useLanguages();
  const enrollments = useEnrollments();
  const activate = useActivateLanguage();

  const isLoading = languages.isLoading || enrollments.isLoading;
  const isError = languages.isError || enrollments.isError;

  const activeLanguageId = enrollments.data?.activeLanguageId ?? null;
  const enrollmentByLanguage = new Map<Uuid, EnrollmentResponse>(
    (enrollments.data?.enrollments ?? []).map((e) => [e.languageId, e]),
  );

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Nyelvek
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Válaszd ki, melyik nyelvet szeretnéd tanulni. Az aktív nyelv határozza
        meg a leckéket és a napi kihívást.
      </Typography>

      <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress aria-label="Betöltés" />
          </Box>
        )}

        {isError && !isLoading && (
          <Alert severity="error">
            Nem sikerült betölteni a nyelveket. Próbáld újra később.
          </Alert>
        )}

        {!isLoading && !isError && (languages.data?.length ?? 0) === 0 && (
          <Alert severity="info">Jelenleg nincs elérhető nyelv.</Alert>
        )}

        {!isLoading && !isError && (languages.data?.length ?? 0) > 0 && (
          <>
            {activate.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                Nem sikerült aktiválni a nyelvet. Próbáld újra.
              </Alert>
            )}
            <List>
              {languages.data!.map((language) => {
                const enrollment = enrollmentByLanguage.get(language.id);
                const isActive = activeLanguageId === language.id;
                return (
                  <ListItem
                    key={language.id}
                    divider
                    secondaryAction={
                      isActive ? (
                        <Chip label="Aktív" color="primary" />
                      ) : (
                        <Button
                          variant="outlined"
                          onClick={() => activate.mutate(language.id)}
                          disabled={activate.isPending}
                        >
                          {enrollment ? 'Aktiválás' : 'Kezdés'}
                        </Button>
                      )
                    }
                  >
                    <ListItemText
                      primary={language.name}
                      secondary={
                        <Stack
                          component="span"
                          direction="row"
                          spacing={1}
                          alignItems="center"
                        >
                          <span>{language.code.toUpperCase()}</span>
                          {enrollment && (
                            <span>· Beiratkozva</span>
                          )}
                        </Stack>
                      }
                    />
                  </ListItem>
                );
              })}
            </List>
          </>
        )}
      </Paper>
    </Container>
  );
}
