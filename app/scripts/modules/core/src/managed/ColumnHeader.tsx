import React from 'react';
import classNames from 'classnames';

import { Icon, IconNames } from '../presentation';

import styles from './ColumnHeader.module.css';

export interface IColumnHeaderProps {
  text: string;
  icon: IconNames;
}

export function ColumnHeader({ text, icon }: IColumnHeaderProps) {
  return (
    <div className={styles.ColumnHeader}>
      {icon && <Icon name={icon} appearance="light" size="medium" className={styles.icon} />}
      {text && (
        <span className={classNames('text-bold', styles.text)} style={icon && { marginRight: '56px' }}>
          {text}
        </span>
      )}
    </div>
  );
}
