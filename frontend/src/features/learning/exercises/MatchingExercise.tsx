import {
  Box,
  FormControl,
  FormLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import type { MatchingContent, MatchingPair } from '../types';

interface Props {
  content: MatchingContent;
  value: MatchingPair[];
  onChange: (pairs: MatchingPair[]) => void;
  disabled?: boolean;
}

// Pair each left item with a right item via a per-row select. The select only
// reflects the user's pairing; it does not indicate the correct match.
export function MatchingExercise({ content, value, onChange, disabled }: Props) {
  const rightFor = (leftId: string): string =>
    value.find((p) => p.leftId === leftId)?.rightId ?? '';

  const setPair = (leftId: string, rightId: string) => {
    const others = value.filter((p) => p.leftId !== leftId);
    onChange(rightId ? [...others, { leftId, rightId }] : others);
  };

  return (
    <Box component="fieldset" sx={{ border: 0, p: 0, m: 0 }}>
      <FormLabel component="legend">Párosítsd az elemeket</FormLabel>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {content.leftItems.map((left) => (
          <Stack
            key={left.id}
            direction="row"
            spacing={2}
            alignItems="center"
          >
            <Typography sx={{ minWidth: 120 }}>{left.text}</Typography>
            <FormControl size="small" sx={{ minWidth: 160 }} disabled={disabled}>
              <Select
                displayEmpty
                value={rightFor(left.id)}
                onChange={(e) => setPair(left.id, e.target.value)}
                inputProps={{ 'aria-label': `Pár ehhez: ${left.text}` }}
              >
                <MenuItem value="">
                  <em>Válassz…</em>
                </MenuItem>
                {content.rightItems.map((right) => (
                  <MenuItem key={right.id} value={right.id}>
                    {right.text}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}
