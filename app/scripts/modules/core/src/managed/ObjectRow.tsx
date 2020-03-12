import React from 'react';

import styles from './ObjectRow.module.css';

interface IObjectRowProps {
  icon: string;
  title: string;
}

export const ObjectRow = ({ icon, title }: IObjectRowProps) => {
  const depth = 0;
  return (
    <div className={styles.ObjectRow} style={getStylesFromDepth(depth)}>
      <div className={styles.leftCol}>
        <i className={`ico icon-${icon}`} />
        <span className={styles.rowTitle}>{title}</span>
      </div>
      <div className={styles.centerCol} style={{ flex: `0 0 ${200 + depth * 16}px` }}>
        {'unknown version'}
      </div>
    </div>
  );
};

const getStylesFromDepth = (depth: number): React.CSSProperties => {
  return {
    marginLeft: 16 * depth,
    position: 'sticky',
    top: 104 + 40 * depth,
    zIndex: 500 - depth,
  };
};
