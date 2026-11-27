import { useEffect, useState } from 'react';
import {
  Box,
  Chip,
  FormControlLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import type { DuelExerciseView } from './types';

interface IdText {
  id: string;
  text: string;
}
interface Token {
  index: number;
  text: string;
}

function asIdTextList(value: unknown): IdText[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((v): v is Record<string, unknown> => typeof v === 'object' && v !== null)
    .map((v) => ({ id: String(v.id ?? ''), text: String(v.text ?? '') }));
}

function asTokenList(value: unknown): Token[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((v): v is Record<string, unknown> => typeof v === 'object' && v !== null)
    .map((v) => ({ index: Number(v.index ?? 0), text: String(v.text ?? '') }));
}

/**
 * Renders the correct input for a duel exercise type and reports the answer
 * payload (in Learning's expected shape) via {@code onChange}. When no valid
 * answer is selected yet, {@code onChange} is called with {@code null} so the
 * parent can disable submission.
 *
 * Answer shapes:
 *  - MULTIPLE_CHOICE -> { selectedOptionIds: [id] }
 *  - TRUE_FALSE      -> { answer: boolean }
 *  - ODD_ONE_OUT     -> { selectedItemId: id }
 *  - ERROR_FINDING   -> { markedTokenIndexes: number[] }
 *  - MATCHING        -> { pairs: [{ leftId, rightId }] }
 */
export function DuelExerciseInput({
  exercise,
  onChange,
}: {
  exercise: DuelExerciseView;
  onChange: (answer: Record<string, unknown> | null) => void;
}) {
  const c = exercise.content ?? {};

  // Local state per type, reset whenever the exercise (position) changes.
  const [choice, setChoice] = useState('');
  const [tf, setTf] = useState<'true' | 'false' | ''>('');
  const [marked, setMarked] = useState<number[]>([]);
  const [pairs, setPairs] = useState<Record<string, string>>({});
  const [text, setText] = useState('');

  useEffect(() => {
    setChoice('');
    setTf('');
    setMarked([]);
    setPairs({});
    setText('');
    onChange(null);
    // Reset only when moving to a different exercise.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exercise.position, exercise.type]);

  switch (exercise.type) {
    case 'MULTIPLE_CHOICE': {
      const options = exercise.options.length > 0 ? exercise.options : asIdTextList(c.options);
      return (
        <RadioGroup
          value={choice}
          onChange={(e) => {
            setChoice(e.target.value);
            onChange({ selectedOptionIds: [e.target.value] });
          }}
        >
          {options.map((o) => (
            <FormControlLabel key={o.id} value={o.id} control={<Radio />} label={o.text} />
          ))}
        </RadioGroup>
      );
    }

    case 'ODD_ONE_OUT': {
      const items = asIdTextList(c.items);
      return (
        <RadioGroup
          value={choice}
          onChange={(e) => {
            setChoice(e.target.value);
            onChange({ selectedItemId: e.target.value });
          }}
        >
          {items.map((o) => (
            <FormControlLabel key={o.id} value={o.id} control={<Radio />} label={o.text} />
          ))}
        </RadioGroup>
      );
    }

    case 'TRUE_FALSE': {
      const statement = typeof c.statement === 'string' ? c.statement : '';
      return (
        <Box>
          {statement && (
            <Typography sx={{ mb: 1, fontStyle: 'italic' }}>„{statement}”</Typography>
          )}
          <RadioGroup
            row
            value={tf}
            onChange={(e) => {
              const v = e.target.value as 'true' | 'false';
              setTf(v);
              onChange({ answer: v === 'true' });
            }}
          >
            <FormControlLabel value="true" control={<Radio />} label="Igaz" />
            <FormControlLabel value="false" control={<Radio />} label="Hamis" />
          </RadioGroup>
        </Box>
      );
    }

    case 'ERROR_FINDING': {
      const tokens = asTokenList(c.tokens);
      const toggle = (idx: number) => {
        const next = marked.includes(idx)
          ? marked.filter((i) => i !== idx)
          : [...marked, idx];
        setMarked(next);
        onChange(next.length > 0 ? { markedTokenIndexes: next } : null);
      };
      return (
        <Box>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Kattints a hibás szó(k)ra.
          </Typography>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            {tokens.map((t) => (
              <Chip
                key={t.index}
                label={t.text}
                color={marked.includes(t.index) ? 'primary' : 'default'}
                variant={marked.includes(t.index) ? 'filled' : 'outlined'}
                onClick={() => toggle(t.index)}
                clickable
              />
            ))}
          </Stack>
        </Box>
      );
    }

    case 'MATCHING': {
      const left = asIdTextList(c.leftItems);
      const right = asIdTextList(c.rightItems);
      const setPair = (leftId: string, rightId: string) => {
        const next = { ...pairs, [leftId]: rightId };
        setPairs(next);
        const built = Object.entries(next)
          .filter(([, r]) => r !== '')
          .map(([leftId2, rightId2]) => ({ leftId: leftId2, rightId: rightId2 }));
        onChange(built.length === left.length ? { pairs: built } : null);
      };
      return (
        <Stack spacing={2}>
          {left.map((l) => (
            <Box key={l.id} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Typography sx={{ minWidth: 120 }}>{l.text}</Typography>
              <TextField
                select
                size="small"
                label="Pár"
                value={pairs[l.id] ?? ''}
                onChange={(e) => setPair(l.id, e.target.value)}
                sx={{ minWidth: 160 }}
              >
                {right.map((r) => (
                  <MenuItem key={r.id} value={r.id}>
                    {r.text}
                  </MenuItem>
                ))}
              </TextField>
            </Box>
          ))}
        </Stack>
      );
    }

    default: {
      // Unknown/free-text type: a plain text field, submitted as { answer }.
      return (
        <TextField
          label="Válasz"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            onChange(e.target.value.trim() ? { answer: e.target.value.trim() } : null);
          }}
          fullWidth
          multiline
          minRows={2}
        />
      );
    }
  }
}
