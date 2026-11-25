import { Box, Chip, FormLabel, Stack } from '@mui/material';
import type { ErrorFindingContent } from '../types';

interface Props {
  content: ErrorFindingContent;
  value: number[];
  onChange: (markedTokenIndexes: number[]) => void;
  disabled?: boolean;
}

// Toggle token indexes to mark the errors. Selection highlights the user's
// choice only; it never signals which token is actually wrong.
export function ErrorFindingExercise({ content, value, onChange, disabled }: Props) {
  const toggle = (index: number) => {
    if (disabled) return;
    onChange(
      value.includes(index)
        ? value.filter((i) => i !== index)
        : [...value, index],
    );
  };

  return (
    <Box component="fieldset" sx={{ border: 0, p: 0, m: 0 }}>
      <FormLabel component="legend">Jelöld meg a hibás szavakat</FormLabel>
      <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1 }} useFlexGap>
        {content.tokens.map((token) => {
          const selected = value.includes(token.index);
          return (
            <Chip
              key={token.index}
              label={token.text}
              color={selected ? 'primary' : 'default'}
              variant={selected ? 'filled' : 'outlined'}
              onClick={() => toggle(token.index)}
              disabled={disabled}
              aria-pressed={selected}
              clickable
            />
          );
        })}
      </Stack>
    </Box>
  );
}
