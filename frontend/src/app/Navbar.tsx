import { useState } from 'react';
import {
  AppBar,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Toolbar,
  Typography,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { NotificationBell } from '../features/social/NotificationBell';

interface NavLink {
  label: string;
  to: string;
  authOnly?: boolean;
}

const NAV_LINKS: NavLink[] = [
  { label: 'Ranglista', to: '/leaderboard' },
  { label: 'Tanulás', to: '/languages', authOnly: true },
  { label: 'Leckék', to: '/lessons', authOnly: true },
  { label: 'Napi kihívás', to: '/daily', authOnly: true },
  { label: 'Barátok', to: '/friends', authOnly: true },
  { label: 'Párbajok', to: '/duels', authOnly: true },
  { label: 'Profil', to: '/profile', authOnly: true },
];

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleLogout = async () => {
    setDrawerOpen(false);
    await logout();
    navigate('/login');
  };

  const links = NAV_LINKS.filter((l) => !l.authOnly || user);
  const isActive = (to: string) => location.pathname.startsWith(to);

  return (
    <AppBar position="static" color="default">
      <Toolbar sx={{ justifyContent: 'space-between', gap: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
          {isMobile && (
            <IconButton
              edge="start"
              color="inherit"
              aria-label="Navigáció megnyitása"
              onClick={() => setDrawerOpen(true)}
            >
              <Box aria-hidden sx={{ fontSize: 24, lineHeight: 1 }}>☰</Box>
            </IconButton>
          )}
          <Typography
            variant="h6"
            component={RouterLink}
            to="/"
            sx={{ textDecoration: 'none', color: 'inherit', fontWeight: 'bold', whiteSpace: 'nowrap' }}
          >
            Nyelvtanuló
          </Typography>
          {!isMobile && (
            <Box component="nav" aria-label="Fő navigáció" sx={{ display: 'flex', gap: 0.5 }}>
              {links.map((item) => (
                <Button
                  key={item.to}
                  component={RouterLink}
                  to={item.to}
                  color="inherit"
                  aria-current={isActive(item.to) ? 'page' : undefined}
                  sx={{ fontWeight: isActive(item.to) ? 700 : 400 }}
                >
                  {item.label}
                </Button>
              ))}
            </Box>
          )}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          {user ? (
            <>
              <NotificationBell enabled={Boolean(user)} />
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

      {/* Mobil navigáció: a fő linkek egy oldalsó menüben. */}
      <Drawer anchor="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box
          component="nav"
          aria-label="Fő navigáció"
          sx={{ width: 240 }}
          role="presentation"
          onClick={() => setDrawerOpen(false)}
        >
          <Typography variant="h6" sx={{ p: 2, fontWeight: 'bold' }}>
            Nyelvtanuló
          </Typography>
          <Divider />
          <List>
            {links.map((item) => (
              <ListItem key={item.to} disablePadding>
                <ListItemButton
                  component={RouterLink}
                  to={item.to}
                  aria-current={isActive(item.to) ? 'page' : undefined}
                >
                  <ListItemText primary={item.label} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Box>
      </Drawer>
    </AppBar>
  );
}
