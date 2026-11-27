import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLanguages } from '../learning/hooks';
import { useCreateDuel, useDuels, useFriends } from './hooks';
import { userLabel } from './format';
import type { DuelStatus, DuelSummaryView } from './types';

const STATUS_LABELS: Record<DuelStatus, string> = {
  PENDING: 'Függőben',
  ACCEPTED: 'Elfogadva',
  IN_PROGRESS: 'Folyamatban',
  COMPLETED: 'Befejezve',
  REJECTED: 'Elutasítva',
  EXPIRED: 'Lejárt',
};

const STATUS_COLOR: Record<DuelStatus, 'default' | 'primary' | 'success' | 'warning' | 'error'> = {
  PENDING: 'warning',
  ACCEPTED: 'primary',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  REJECTED: 'error',
  EXPIRED: 'default',
};

// Duels page (/duels): lists the user's duels (optionally filtered by status)
// and lets the user start a new duel against a friend in a chosen language.
export function DuelsPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [statusFilter, setStatusFilter] = useState<DuelStatus | 'ALL'>('ALL');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  const duels = useDuels(statusFilter === 'ALL' ? undefined : statusFilter);

  const counterpart = (duel: DuelSummaryView) =>
    user && duel.challengerId === user.id ? duel.opponentId : duel.challengerId;

  const roleLabel = (duel: DuelSummaryView) =>
    user && duel.challengerId === user.id ? 'Te hívtad ki' : 'Téged hívott ki';

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h4" component="h1">
          Párbajok
        </Typography>
        <Button variant="contained" onClick={() => setDialogOpen(true)}>
          Új párbaj
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 2 }}>
        Hívj ki egy barátot aszinkron párbajra, vagy folytasd a folyamatban lévőket.
      </Typography>

      <TextField
        select
        size="small"
        label="Szűrés státusz szerint"
        value={statusFilter}
        onChange={(e) => setStatusFilter(e.target.value as DuelStatus | 'ALL')}
        sx={{ mb: 2, minWidth: 200 }}
      >
        <MenuItem value="ALL">Összes</MenuItem>
        {(Object.keys(STATUS_LABELS) as DuelStatus[]).map((s) => (
          <MenuItem key={s} value={s}>
            {STATUS_LABELS[s]}
          </MenuItem>
        ))}
      </TextField>

      {duels.isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Betöltés" />
        </Box>
      )}

      {duels.isError && !duels.isLoading && (
        <Alert severity="error">
          Nem sikerült betölteni a párbajokat. Próbáld újra később.
        </Alert>
      )}

      {!duels.isLoading && !duels.isError && (duels.data?.length ?? 0) === 0 && (
        <Alert severity="info">Nincs megjeleníthető párbaj.</Alert>
      )}

      {!duels.isLoading && !duels.isError && (duels.data?.length ?? 0) > 0 && (
        <Paper variant="outlined">
          <List>
            {duels.data!.map((duel) => (
              <ListItem key={duel.id} divider disablePadding>
                <ListItemButton onClick={() => navigate(`/duels/${duel.id}`)}>
                  <ListItemText
                    primary={userLabel(counterpart(duel))}
                    secondary={roleLabel(duel)}
                  />
                  <Chip
                    label={STATUS_LABELS[duel.status]}
                    color={STATUS_COLOR[duel.status]}
                    size="small"
                    variant="outlined"
                  />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Paper>
      )}

      <NewDuelDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onCreated={(id) => {
          setDialogOpen(false);
          setSnackbar('Párbaj létrehozva!');
          navigate(`/duels/${id}`);
        }}
      />

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" variant="filled" sx={{ width: '100%' }}>
          {snackbar}
        </Alert>
      </Snackbar>
    </Container>
  );
}

function NewDuelDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (duelId: string) => void;
}) {
  const friends = useFriends(0, 100);
  const languages = useLanguages();
  const createDuel = useCreateDuel();
  const [opponentId, setOpponentId] = useState('');
  const [languageId, setLanguageId] = useState('');

  const friendItems = useMemo(() => friends.data?.items ?? [], [friends.data]);

  const canSubmit = opponentId !== '' && languageId !== '' && !createDuel.isPending;

  const handleCreate = () => {
    createDuel.mutate(
      { opponentId, languageId },
      {
        onSuccess: (duel) => {
          setOpponentId('');
          setLanguageId('');
          onCreated(duel.id);
        },
      },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Új párbaj</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {createDuel.isError && (
            <Alert severity="error">
              Nem sikerült létrehozni a párbajt. Csak elfogadott barátot hívhatsz ki.
            </Alert>
          )}

          {friendItems.length === 0 ? (
            <Alert severity="info">
              Előbb vegyél fel barátot – csak barátot hívhatsz ki párbajra.
            </Alert>
          ) : (
            <TextField
              select
              label="Ellenfél"
              value={opponentId}
              onChange={(e) => setOpponentId(e.target.value)}
              fullWidth
            >
              {friendItems.map((f) => (
                <MenuItem key={f.userId} value={f.userId}>
                  {userLabel(f.userId, f.displayName)}
                </MenuItem>
              ))}
            </TextField>
          )}

          <TextField
            select
            label="Nyelv"
            value={languageId}
            onChange={(e) => setLanguageId(e.target.value)}
            fullWidth
            disabled={languages.isLoading}
          >
            {(languages.data ?? []).map((lang) => (
              <MenuItem key={lang.id} value={lang.id}>
                {lang.name}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Mégsem</Button>
        <Button variant="contained" onClick={handleCreate} disabled={!canSubmit}>
          Kihívás
        </Button>
      </DialogActions>
    </Dialog>
  );
}
