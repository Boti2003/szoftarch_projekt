import {
  Checkbox,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormLabel,
  Radio,
  RadioGroup,
} from '@mui/material';
import type { MultipleChoiceContent } from '../types';

interface Props {
  content: MultipleChoiceContent;
  value: string[];
  onChange: (selectedOptionIds: string[]) => void;
  disabled?: boolean;
}

// Renders radio buttons for single-select, checkboxes for multi-select.
// Never marks or hints which option is correct.
export function MultipleChoiceExercise({ content, value, onChange, disabled }: Props) {
  if (content.multiSelect) {
    const toggle = (id: string, checked: boolean) => {
      onChange(checked ? [...value, id] : value.filter((v) => v !== id));
    };
    return (
      <FormControl component="fieldset" disabled={disabled}>
        <FormLabel component="legend">Válaszd ki a helyes opciókat</FormLabel>
        <FormGroup>
          {content.options.map((option) => (
            <FormControlLabel
              key={option.id}
              control={
                <Checkbox
                  checked={value.includes(option.id)}
                  onChange={(e) => toggle(option.id, e.target.checked)}
                />
              }
              label={option.text}
            />
          ))}
        </FormGroup>
      </FormControl>
    );
  }

  return (
    <FormControl component="fieldset" disabled={disabled}>
      <FormLabel component="legend">Válaszd ki a helyes opciót</FormLabel>
      <RadioGroup
        value={value[0] ?? ''}
        onChange={(e) => onChange([e.target.value])}
      >
        {content.options.map((option) => (
          <FormControlLabel
            key={option.id}
            value={option.id}
            control={<Radio />}
            label={option.text}
          />
        ))}
      </RadioGroup>
    </FormControl>
  );
}
