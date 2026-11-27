import { Badge, Button, Tooltip } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useNotificationSummary } from './hooks';

// Polling notification indicator (baseline decision D15: 15s polling of the
// social notification-summary endpoint). Shows the combined count of pending
// incoming friend requests and actionable duels as a badge; clicking goes to
// the friends page. Rendered only for authenticated users (the caller gates
// this via `enabled`).
export function NotificationBell({ enabled }: { enabled: boolean }) {
  const summary = useNotificationSummary(enabled);

  const count =
    (summary.data?.pendingFriendRequestCount ?? 0) +
    (summary.data?.actionableDuelCount ?? 0);

  const title =
    count === 0
      ? 'Nincs új értesítés'
      : `${summary.data?.pendingFriendRequestCount ?? 0} barátkérelem, ${
          summary.data?.actionableDuelCount ?? 0
        } párbaj vár rád`;

  return (
    <Tooltip title={title}>
      <Button
        component={RouterLink}
        to="/friends"
        color="inherit"
        aria-label={`Értesítések: ${count}`}
      >
        <Badge badgeContent={count} color="error" max={99}>
          <span aria-hidden style={{ fontSize: 20, lineHeight: 1 }}>
            🔔
          </span>
        </Badge>
      </Button>
    </Tooltip>
  );
}
