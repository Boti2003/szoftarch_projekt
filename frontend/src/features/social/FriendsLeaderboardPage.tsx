import {
  Alert,
  Box,
  CircularProgress,
  Container,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import { useFriendsLeaderboard } from './hooks';
import { userLabel } from './format';

// Friends leaderboard (/leaderboards/friends): the authenticated user plus
// their friends, ranked by points descending. The current user's row is
// highlighted. Rank badges match the global LeaderboardPage.
export function FriendsLeaderboardPage() {
  const { user } = useAuth();
  const leaderboard = useFriendsLeaderboard();

  const rankBadge = (rank: number) => {
    if (rank === 1) return '🥇 1.';
    if (rank === 2) return '🥈 2.';
    if (rank === 3) return '🥉 3.';
    return `#${rank}`;
  };

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Baráti ranglista
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Te és a barátaid rangsora pont szerint.
      </Typography>

      {leaderboard.isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="Betöltés" />
        </Box>
      )}

      {leaderboard.isError && !leaderboard.isLoading && (
        <Alert severity="error">
          Nem sikerült betölteni a baráti ranglistát. Próbáld újra később.
        </Alert>
      )}

      {!leaderboard.isLoading &&
        !leaderboard.isError &&
        (leaderboard.data?.length ?? 0) === 0 && (
          <Alert severity="info">Még nincs megjeleníthető rangsor.</Alert>
        )}

      {!leaderboard.isLoading &&
        !leaderboard.isError &&
        (leaderboard.data?.length ?? 0) > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'action.hover' }}>
                  <TableCell sx={{ fontWeight: 'bold' }}>Helyezés</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>Felhasználó</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }} align="center">
                    Szint
                  </TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }} align="right">
                    Összpontszám
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {leaderboard.data!.map((entry) => {
                  const isCurrentUser = user && user.id === entry.userId;
                  return (
                    <TableRow
                      key={entry.userId}
                      sx={{
                        bgcolor: isCurrentUser ? 'action.selected' : 'inherit',
                        fontWeight: isCurrentUser ? 'bold' : 'normal',
                      }}
                    >
                      <TableCell>{rankBadge(entry.rank)}</TableCell>
                      <TableCell>
                        {isCurrentUser ? 'Te' : userLabel(entry.userId, entry.displayName)}
                      </TableCell>
                      <TableCell align="center">{entry.level}</TableCell>
                      <TableCell align="right">{entry.points} XP</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
    </Container>
  );
}
