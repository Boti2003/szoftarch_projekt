import { AppBar, Box, Button, Chip, Toolbar, Typography } from '@mui/material';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <AppBar position="static" color="default" elevation={1}>
      <Toolbar sx={{ justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography
            variant="h6"
            component={RouterLink}
            to="/"
            sx={{ textDecoration: 'none', color: 'inherit', fontWeight: 'bold' }}
          >
            Nyelvtanuló
          </Typography>
          <Button component={RouterLink} to="/leaderboard" color="inherit">
            Ranglista
          </Button>
          {user && (
            <Button component={RouterLink} to="/profile" color="inherit">
              Profil
            </Button>
          )}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          {user ? (
            <>
              <Chip
                label={`Szint ${user.level} (${user.totalPoints} XP)`}
                color="primary"
                size="small"
                variant="outlined"
              />
              <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'block' } }}>
                {user.displayName}
              </Typography>
              <Button onClick={handleLogout} variant="outlined" size="small" color="inherit">
                Kijelentkezés
              </Button>
            </>
          ) : (
            <>
              <Button component={RouterLink} to="/login" color="inherit">
                Bejelentkezés
              </Button>
              <Button component={RouterLink} to="/register" variant="contained" size="small">
                Regisztráció
              </Button>
            </>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );
}
