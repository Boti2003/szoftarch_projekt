import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
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
import { apiFetch, LeaderboardResponse } from '../../api/client';

export function LeaderboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState<LeaderboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const pageSize = 10;

  useEffect(() => {
    setLoading(true);
    apiFetch<LeaderboardResponse>(`/leaderboard?page=${page}&size=${pageSize}`)
      .then(setData)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Hiba a ranglista betöltésekor'))
      .finally(() => setLoading(false));
  }, [page]);

  const getRankBadge = (rank: number) => {
    if (rank === 1) return '🥇 1.';
    if (rank === 2) return '🥈 2.';
    if (rank === 3) return '🥉 3.';
    return `#${rank}`;
  };

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', p: 3 }}>
      <Typography variant="h5" fontWeight="bold" gutterBottom>
        Globális Ranglista
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {/* Saját rang kártya */}
      {data?.myRank && (
        <Card sx={{ mb: 3, bgcolor: 'primary.light', color: 'primary.contrastText' }}>
          <CardContent sx={{ py: 2 }}>
            <Typography variant="subtitle1" fontWeight="bold">
              Saját helyezésed: {getRankBadge(data.myRank.rank)} – {data.myRank.totalPoints} pont (Szint {data.myRank.level})
            </Typography>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', my: 6 }}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'action.hover' }}>
                  <TableCell sx={{ fontWeight: 'bold' }}>Helyezés</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>Felhasználó</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>Megjelenített név</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }} align="center">Szint</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }} align="right">Összpontszám</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data?.entries.map((entry) => {
                  const isCurrentUser = user && user.id === entry.userId;
                  return (
                    <TableRow
                      key={entry.userId}
                      sx={{
                        bgcolor: isCurrentUser ? 'action.selected' : 'inherit',
                        fontWeight: isCurrentUser ? 'bold' : 'normal',
                      }}
                    >
                      <TableCell>{getRankBadge(entry.rank)}</TableCell>
                      <TableCell>@{entry.username}</TableCell>
                      <TableCell>{entry.displayName}</TableCell>
                      <TableCell align="center">{entry.level}</TableCell>
                      <TableCell align="right">{entry.totalPoints} XP</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Lapozó gombok */}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 2 }}>
            <Button
              variant="outlined"
              size="small"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Előző oldal
            </Button>
            <Typography variant="body2" color="text.secondary">
              {page + 1}. oldal / {Math.max(1, data?.totalPages || 1)}
            </Typography>
            <Button
              variant="outlined"
              size="small"
              disabled={!data || page >= data.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Következő oldal
            </Button>
          </Box>
        </>
      )}
    </Box>
  );
}
