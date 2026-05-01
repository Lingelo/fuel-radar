import type { CSSProperties } from 'react';

interface IconProps {
  name: string;
  className?: string;
  filled?: boolean;
  size?: number;
}

export function Icon({ name, className = '', filled = false, size }: IconProps) {
  const style: CSSProperties = {};
  if (filled) style.fontVariationSettings = "'FILL' 1";
  if (size) style.fontSize = `${size}px`;
  return (
    <span className={`material-symbols-outlined ${className}`} style={style}>
      {name}
    </span>
  );
}
