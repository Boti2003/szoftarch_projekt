import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { fetchSystemHealth, type SystemHealth } from '../../api/client';

function statusColor(status: string): 'success' | 'error' | 'warning' | 'default' {
  if (status === 'UP') return 'success';
  if (status === 'DOWN') return 'error';
  if (status === 'DEGRADED') return 'warning';
  return 'default';
}

const COMPONENTS: Array<{ key: keyof SystemHealth; label: string }> = [
  { key: 'gateway', label: 'Gateway' },
  { key: 'identity', label: 'Identity' },
  { key: 'learning', label: 'Learning' },
  { key: 'social', label: 'Social' },
  { key: 'ai', label: 'AI' },
];

export function HealthDashboard() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['system-health'],
    queryFn: fetchSystemHealth,
    refetchInterval: 15_000,
  });

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" gutterBottom>
        Rendszerállapot
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Walking skeleton health dashboard – a Gateway-en keresztül kérdezi le a
        domain service-eket.
      </Typography>

      <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}

        {isError && (
          <Alert severity="error">
            Nem sikerült lekérni a rendszerállapotot. Fut a Gateway és futnak a
            service-ek?
          </Alert>
        )}

        {data && (
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="subtitle1">Összesített státusz</Typography>
              <Chip label={data.status} color={statusColor(data.status)} />
            </Box>
            {COMPONENTS.map(({ key, label }) => (
              <Box
                key={key}
                sx={{ display: 'flex', justifyContent: 'space-between' }}
              >
                <Typography>{label}</Typography>
                <Chip
                  size="small"
                  label={String(data[key])}
                  color={statusColor(String(data[key]))}
                />
              </Box>
            ))}
          </Stack>
        )}
      </Paper>
    </Container>
  );
}
