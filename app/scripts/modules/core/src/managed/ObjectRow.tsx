import React from 'react';

import styles from './ObjectRow.module.css';

interface IObjectRowProps {
  icon: string;
  title: string;
  metadata?: JSX.Element;
  depth?: number;
}

export const ObjectRow = ({ icon, title, metadata, depth = 1 }: IObjectRowProps) => {
  return (
    <div className={styles.ObjectRow} style={getStylesFromDepth(depth)}>
      <span className="clickableArea">
        <div className={styles.leftCol}>
          <i className={`ico icon-${icon}`} />
          <span className={styles.rowTitle}>{title}</span>
        </div>
        <div className={styles.centerCol} style={{ flex: `0 0 ${200 + depth * 16}px` }}>
          {metadata}
        </div>
      </span>
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
