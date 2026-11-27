import { Box, Chip, Stack, TextField, Typography } from '@mui/material';
import { translationSchema } from '../answerSchemas';
import type { TranslationContent } from '../types';

interface Props {
  content: TranslationContent;
  value: string;
  onChange: (text: string) => void;
  disabled?: boolean;
}

// TRANSLATION input: shows the source text, target language and rubric hints,
// and a multiline field validated (non-empty) for UX only. The actual AI
// scoring happens server-side; the correct answer is never shown.
export function TranslationExercise({ content, value, onChange, disabled }: Props) {
  const touched = value.length > 0;
  const parsed = translationSchema.safeParse({ text: value });
  const errorMessage = touched && !parsed.success ? parsed.error.issues[0].message : undefined;

  return (
    <Box>
      <Typography variant="overline" color="text.secondary">
        Fordítandó szöveg ({content.targetLanguageCode})
      </Typography>
      <Typography sx={{ mb: 2 }}>{content.sourceText}</Typography>

      {content.rubric.length > 0 && (
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Szempontok:
          </Typography>
          {content.rubric.map((hint) => (
            <Chip key={hint} size="small" label={hint} />
          ))}
        </Stack>
      )}

      <TextField
        label="Fordítás"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        multiline
        minRows={3}
        fullWidth
        disabled={disabled}
        error={Boolean(errorMessage)}
        helperText={errorMessage}
      />
    </Box>
  );
}
