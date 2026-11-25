import {
  FormControl,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
  Typography,
} from '@mui/material';
import type { TrueFalseContent } from '../types';

interface Props {
  content: TrueFalseContent;
  value: boolean | null;
  onChange: (answer: boolean) => void;
  disabled?: boolean;
}

// Simple true/false control. Does not reveal which answer is correct.
export function TrueFalseExercise({ content, value, onChange, disabled }: Props) {
  return (
    <FormControl component="fieldset" disabled={disabled}>
      <Typography sx={{ mb: 1 }}>{content.statement}</Typography>
      <FormLabel component="legend">Igaz vagy hamis?</FormLabel>
      <RadioGroup
        value={value === null ? '' : String(value)}
        onChange={(e) => onChange(e.target.value === 'true')}
      >
        <FormControlLabel value="true" control={<Radio />} label="Igaz" />
        <FormControlLabel value="false" control={<Radio />} label="Hamis" />
      </RadioGroup>
    </FormControl>
  );
}
