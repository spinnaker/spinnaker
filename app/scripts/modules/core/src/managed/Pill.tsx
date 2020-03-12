import React from 'react';
import styles from './Pill.module.css';

interface IPillProps {
  text: string;
  bgColor?: string;
  textColor?: string;
}

export function Pill({ text, bgColor = '#666666', textColor = '#ffffff' }: IPillProps) {
  return (
    <div className={styles.Pill} style={{ backgroundColor: bgColor, color: textColor }}>
      {text}
    </div>
  );
}
