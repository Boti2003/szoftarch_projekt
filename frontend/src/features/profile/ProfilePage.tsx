import { useState, FormEvent } from 'react';
import { Alert, Box, Button, Card, CardContent, Chip, Divider, LinearProgress, TextField, Typography } from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import { apiFetch, UserProfile } from '../../api/client';

export function ProfilePage() {
  const { user, refreshProfile } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [message, setMessage] = useState<{ text: string; severity: 'success' | 'error' } | null>(null);
  const [saving, setSaving] = useState(false);

  if (!user) return null;

  // Szintlépési progressz: hány százalékánál tart a jelenlegi szintnek
  const prevLevelPoints = Math.pow(user.level - 1, 2) * 50;
  const range = user.nextLevelPoints - prevLevelPoints;
  const progressPercent = range > 0
    ? Math.min(100, Math.max(0, Math.round(((user.totalPoints - prevLevelPoints) / range) * 100)))
    : 100;

  const handleUpdate = async (e: FormEvent) => {
    e.preventDefault();
    setMessage(null);
    setSaving(true);
    try {
      await apiFetch<UserProfile>('/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ displayName }),
      });
      await refreshProfile();
      setMessage({ text: 'Profil sikeresen frissítve!', severity: 'success' });
    } catch (err: unknown) {
      setMessage({ text: err instanceof Error ? err.message : 'Hiba a mentés során', severity: 'error' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Card>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h5" fontWeight="bold">Felhasználói Profil</Typography>
            <Chip label={`Szint ${user.level}`} color="primary" sx={{ fontWeight: 'bold' }} />
          </Box>

          <Typography color="text.secondary" gutterBottom>@{user.username} ({user.email})</Typography>

          {/* Gamifikációs tapasztalati pont csík */}
          <Box sx={{ my: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="body2" fontWeight="medium">Tapasztalati pontok (XP)</Typography>
              <Typography variant="body2" color="text.secondary">
                {user.totalPoints} / {user.nextLevelPoints} XP ({progressPercent}%)
              </Typography>
            </Box>
            <LinearProgress variant="determinate" value={progressPercent} sx={{ height: 10, borderRadius: 5 }} />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
              Még {Math.max(0, user.nextLevelPoints - user.totalPoints)} pont szükséges a {user.level + 1}. szinthez.
            </Typography>
          </Box>

          <Divider sx={{ my: 3 }} />

          {message && <Alert severity={message.severity} sx={{ mb: 2 }}>{message.text}</Alert>}

          <Typography variant="subtitle1" fontWeight="bold" gutterBottom>Profil módosítása</Typography>
          <Box component="form" onSubmit={handleUpdate} sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
            <TextField
              label="Megjelenített név"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              size="small"
              required
              sx={{ flexGrow: 1 }}
            />
            <Button type="submit" variant="contained" disabled={saving}>
              {saving ? 'Mentés...' : 'Mentés'}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
