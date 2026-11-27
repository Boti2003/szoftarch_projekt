import { type ReactNode } from 'react';
import { Container } from '@mui/material';

// Tartalom-keret a feature oldalakhoz. A globális fejlécet és navigációt a
// Navbar adja (egyetlen, egységes fejléc minden oldalon); ez a komponens csak
// a reszponzív, középre igazított tartalomterületet biztosítja.
export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <Container component="main" maxWidth="md" sx={{ py: 3 }}>
      {children}
    </Container>
  );
}
