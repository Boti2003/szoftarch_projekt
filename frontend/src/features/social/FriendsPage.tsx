import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Divider,
  List,
  ListItem,
  ListItemText,
  Paper,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import {
  useAcceptFriendRequest,
  useFriendRequests,
  useFriends,
  useRejectFriendRequest,
  useRemoveFriend,
  useSendFriendRequest,
  useUserSearch,
} from './hooks';
import { userLabel } from './format';
import type { PublicProfile } from './types';

// Friends page (/friends): the user's friend list, incoming/outgoing requests,
// and a username search to send new friend requests. Mirrors the learning
// pages' loading / empty / error handling and MUI usage.
export function FriendsPage() {
  const [tab, setTab] = useState(0);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Barátok
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Keress felhasználókat, kezeld a barátkérelmeidet és a barátlistádat.
      </Typography>

      <Tabs
        value={tab}
        onChange={(_e, v: number) => setTab(v)}
        sx={{ mb: 2 }}
        aria-label="Barátok nézetek"
      >
        <Tab label="Barátlista" />
        <Tab label="Kérelmek" />
        <Tab label="Keresés" />
      </Tabs>

      {tab === 0 && <FriendsList onMessage={setSnackbar} />}
      {tab === 1 && <FriendRequestsPanel onMessage={setSnackbar} />}
      {tab === 2 && <AddFriendPanel onMessage={setSnackbar} />}

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbar(null)}
          severity="success"
          variant="filled"
          sx={{ width: '100%' }}
        >
          {snackbar}
        </Alert>
      </Snackbar>
    </Container>
  );
}

function FriendsList({ onMessage }: { onMessage: (m: string) => void }) {
  const friends = useFriends();
  const removeFriend = useRemoveFriend();

  if (friends.isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress aria-label="Betöltés" />
      </Box>
    );
  }
  if (friends.isError) {
    return (
      <Alert severity="error">
        Nem sikerült betölteni a barátlistát. Próbáld újra később.
      </Alert>
    );
  }
  const items = friends.data?.items ?? [];
  if (items.length === 0) {
    return <Alert severity="info">Még nincs barátod. Keress felhasználókat!</Alert>;
  }

  const onRemove = (friendId: string, label: string) => {
    removeFriend.mutate(friendId, {
      onSuccess: () => onMessage(`${label} törölve a barátok közül.`),
    });
  };

  return (
    <Paper variant="outlined">
      <List>
        {items.map((friend) => {
          const label = userLabel(friend.userId, friend.displayName);
          return (
            <ListItem
              key={friend.userId}
              divider
              secondaryAction={
                <Button
                  color="error"
                  size="small"
                  onClick={() => onRemove(friend.userId, label)}
                  disabled={removeFriend.isPending}
                >
                  Törlés
                </Button>
              }
            >
              <ListItemText
                primary={label}
                secondary={`Szint ${friend.level} · ${friend.points} XP`}
              />
            </ListItem>
          );
        })}
      </List>
    </Paper>
  );
}

function FriendRequestsPanel({ onMessage }: { onMessage: (m: string) => void }) {
  const incoming = useFriendRequests('incoming', 'PENDING');
  const outgoing = useFriendRequests('outgoing', 'PENDING');
  const accept = useAcceptFriendRequest();
  const reject = useRejectFriendRequest();

  const isLoading = incoming.isLoading || outgoing.isLoading;
  const isError = incoming.isError || outgoing.isError;

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress aria-label="Betöltés" />
      </Box>
    );
  }
  if (isError) {
    return (
      <Alert severity="error">
        Nem sikerült betölteni a kérelmeket. Próbáld újra később.
      </Alert>
    );
  }

  const incomingItems = incoming.data?.items ?? [];
  const outgoingItems = outgoing.data?.items ?? [];

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h6" gutterBottom>
          Beérkező kérelmek
        </Typography>
        {incomingItems.length === 0 ? (
          <Alert severity="info">Nincs beérkező barátkérelem.</Alert>
        ) : (
          <Paper variant="outlined">
            <List>
              {incomingItems.map((req) => (
                <ListItem
                  key={req.id}
                  divider
                  secondaryAction={
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        variant="contained"
                        disabled={accept.isPending}
                        onClick={() =>
                          accept.mutate(req.id, {
                            onSuccess: () => onMessage('Barátkérelem elfogadva.'),
                          })
                        }
                      >
                        Elfogad
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        disabled={reject.isPending}
                        onClick={() =>
                          reject.mutate(req.id, {
                            onSuccess: () => onMessage('Barátkérelem elutasítva.'),
                          })
                        }
                      >
                        Elutasít
                      </Button>
                    </Stack>
                  }
                >
                  <ListItemText
                    primary={userLabel(req.senderId)}
                    secondary="Barátkérelmet küldött neked"
                  />
                </ListItem>
              ))}
            </List>
          </Paper>
        )}
      </Box>

      <Divider />

      <Box>
        <Typography variant="h6" gutterBottom>
          Elküldött kérelmek
        </Typography>
        {outgoingItems.length === 0 ? (
          <Alert severity="info">Nincs függőben lévő elküldött kérelem.</Alert>
        ) : (
          <Paper variant="outlined">
            <List>
              {outgoingItems.map((req) => (
                <ListItem key={req.id} divider>
                  <ListItemText
                    primary={userLabel(req.receiverId)}
                    secondary="Függőben"
                  />
                  <Chip label="PENDING" size="small" variant="outlined" />
                </ListItem>
              ))}
            </List>
          </Paper>
        )}
      </Box>
    </Stack>
  );
}

function AddFriendPanel({ onMessage }: { onMessage: (m: string) => void }) {
  const { user } = useAuth();
  const [query, setQuery] = useState('');
  const results = useUserSearch(query);
  const send = useSendFriendRequest();

  const onSend = (profile: PublicProfile) => {
    send.mutate(profile.id, {
      onSuccess: () => onMessage(`Barátkérelem elküldve: ${profile.displayName}.`),
    });
  };

  const items = (results.data ?? []).filter((p) => p.id !== user?.id);

  return (
    <Stack spacing={2}>
      <TextField
        label="Felhasználónév keresése"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        helperText="Írj be legalább 2 karaktert a kereséshez."
        fullWidth
      />

      {send.isError && (
        <Alert severity="error">
          Nem sikerült elküldeni a kérelmet. Lehet, hogy már barátok vagytok, vagy
          van függőben lévő kérelem.
        </Alert>
      )}

      {results.isLoading && query.trim().length >= 2 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress aria-label="Keresés" />
        </Box>
      )}

      {results.isError && (
        <Alert severity="error">A keresés nem sikerült. Próbáld újra.</Alert>
      )}

      {!results.isLoading &&
        !results.isError &&
        query.trim().length >= 2 &&
        items.length === 0 && (
          <Alert severity="info">Nincs találat erre a névre.</Alert>
        )}

      {items.length > 0 && (
        <Paper variant="outlined">
          <List>
            {items.map((profile) => (
              <ListItem
                key={profile.id}
                divider
                secondaryAction={
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={send.isPending}
                    onClick={() => onSend(profile)}
                  >
                    Barátnak jelöl
                  </Button>
                }
              >
                <ListItemText
                  primary={`${profile.displayName} (@${profile.username})`}
                  secondary={`Szint ${profile.level} · ${profile.totalPoints} XP`}
                />
              </ListItem>
            ))}
          </List>
        </Paper>
      )}
    </Stack>
  );
}
