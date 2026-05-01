import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import { App } from './App';

// Add `fonts-loaded` to <html> once Material Symbols + Inter are ready.
// This unhides icons (which would otherwise show their literal name briefly).
const fonts = (document as Document & { fonts?: { ready: Promise<unknown> } }).fonts;
if (fonts) {
  fonts.ready.then(() => {
    document.documentElement.classList.add('fonts-loaded');
  });
} else {
  document.documentElement.classList.add('fonts-loaded');
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
