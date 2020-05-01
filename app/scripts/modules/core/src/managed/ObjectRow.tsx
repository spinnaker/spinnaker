import React from 'react';
import classNames from 'classnames';

import { Icon, IconNames } from '../presentation';

import styles from './ObjectRow.module.css';

interface IObjectRowProps {
  content?: JSX.Element;
  icon: IconNames;
  title: JSX.Element | string;
  metadata?: JSX.Element;
  depth?: number;
}

export const ObjectRow = ({ content, icon, title, metadata, depth = 1 }: IObjectRowProps) => {
  return (
    <div className={styles.ObjectRow} style={getStylesFromDepth(depth)}>
      <span className={styles.clickableArea}>
        <div className={classNames([styles.col, styles.titleCol])}>
          <Icon name={icon} size="medium" appearance="dark" className="sp-margin-s-right" />
          <span className={styles.rowTitle}>{title}</span>
        </div>
        <div className={styles.col}>{content}</div>
        <div className={classNames([styles.col, styles.metaDataCol])}>{metadata}</div>
      </span>
    </div>
  );
};

const getStylesFromDepth = (depth: number): React.CSSProperties => {
  return {
    marginLeft: 16 * depth,
  };
};
