import {
  FormControl,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
} from '@mui/material';
import type { OddOneOutContent } from '../types';

interface Props {
  content: OddOneOutContent;
  value: string | null;
  onChange: (selectedItemId: string) => void;
  disabled?: boolean;
}

// Single-select of the item that does not belong. No correctness hint.
export function OddOneOutExercise({ content, value, onChange, disabled }: Props) {
  return (
    <FormControl component="fieldset" disabled={disabled}>
      <FormLabel component="legend">Melyik nem illik a sorba?</FormLabel>
      <RadioGroup value={value ?? ''} onChange={(e) => onChange(e.target.value)}>
        {content.items.map((item) => (
          <FormControlLabel
            key={item.id}
            value={item.id}
            control={<Radio />}
            label={item.text}
          />
        ))}
      </RadioGroup>
    </FormControl>
  );
}
