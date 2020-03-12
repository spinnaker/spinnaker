import React from 'react';
import styles from './ColumnHeader.module.css';

export interface IColumnHeaderProps {
  text: string;
  icon: string;
}

export function ColumnHeader({ text, icon }: IColumnHeaderProps) {
  return (
    <div className={styles.ColumnHeader}>
      {icon && <i className={`ico icon-${icon} ${styles.icon}`} />}
      {text && (
        <span className={styles.text} style={icon && { marginRight: '56px' }}>
          {text}
        </span>
      )}
    </div>
  );
}
