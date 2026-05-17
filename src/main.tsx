import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import { App } from './App';

// Add `fonts-loaded` to <html> once the Material Symbols icon font is ready.
// We only gate on this one face (not `document.fonts.ready`, which waits for
// every declared family — Inter 400/500/600/700 too). The icon font is the
// only one whose absence makes UI render literal text, so blocking on the
// rest just keeps icons hidden longer than necessary.
type FontFaceSetLike = {
  load: (font: string) => Promise<unknown>;
};
const fonts = (document as Document & { fonts?: FontFaceSetLike }).fonts;
const markReady = () => document.documentElement.classList.add('fonts-loaded');
if (fonts) {
  fonts
    .load('24px "Material Symbols Outlined"')
    .then(markReady)
    .catch(markReady);
  // Safety net: never leave icons hidden if the font hangs for any reason.
  setTimeout(markReady, 3000);
} else {
  markReady();
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
