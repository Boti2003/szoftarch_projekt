import { useState, type ReactNode } from 'react';
import {
  AppBar,
  Box,
  Button,
  Container,
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
import { Link as RouterLink, useLocation } from 'react-router-dom';

interface NavItem {
  label: string;
  to: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Rendszerállapot', to: '/' },
  { label: 'Nyelvek', to: '/languages' },
  { label: 'Leckék', to: '/lessons' },
  { label: 'Napi kihívás', to: '/daily' },
];

// Reszponzív, billentyűzettel elérhető fő elrendezés a learning feature-ökhöz.
export function AppLayout({ children }: { children: ReactNode }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [drawerOpen, setDrawerOpen] = useState(false);
  const location = useLocation();

  const isActive = (to: string) =>
    to === '/' ? location.pathname === '/' : location.pathname.startsWith(to);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static" component="header">
        <Toolbar>
          {isMobile && (
            <IconButton
              edge="start"
              color="inherit"
              aria-label="Navigáció megnyitása"
              onClick={() => setDrawerOpen(true)}
              sx={{ mr: 1 }}
            >
              {/* Egyszerű hamburger ikon extra ikonkészlet-függőség nélkül. */}
              <Box aria-hidden sx={{ fontSize: 24, lineHeight: 1 }}>
                ☰
              </Box>
            </IconButton>
          )}
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Nyelvtanuló
          </Typography>
          {!isMobile && (
            <Box component="nav" aria-label="Fő navigáció" sx={{ display: 'flex', gap: 1 }}>
              {NAV_ITEMS.map((item) => (
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
        </Toolbar>
      </AppBar>

      <Drawer anchor="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box
          component="nav"
          aria-label="Fő navigáció"
          sx={{ width: 240 }}
          role="presentation"
          onClick={() => setDrawerOpen(false)}
        >
          <List>
            {NAV_ITEMS.map((item) => (
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

      <Container component="main" maxWidth="md" sx={{ flexGrow: 1, py: 3 }}>
        {children}
      </Container>
    </Box>
  );
}
