import React from 'react';

import { Icon, IconNames } from '@spinnaker/presentation';

import './ObjectRow.less';

interface IObjectRowProps {
  content?: JSX.Element;
  icon: IconNames;
  title: JSX.Element | string;
  metadata?: JSX.Element;
  depth?: number;
}

export const ObjectRow = ({ content, icon, title, metadata, depth = 1 }: IObjectRowProps) => {
  return (
    <div className="ObjectRow" style={getStylesFromDepth(depth)}>
      <span className="object-row-content">
        <div className="object-row-column object-row-title-column">
          <Icon name={icon} size="medium" appearance="dark" className="sp-margin-s-right" />
          <span className="object-row-title">{title}</span>
        </div>
        <div className="object-row-column flex-grow">
          {content}
          {metadata && <div className="flex-pull-right">{metadata}</div>}
        </div>
      </span>
    </div>
  );
};

const getStylesFromDepth = (depth: number): React.CSSProperties => {
  return {
    marginLeft: 16 * depth,
  };
};
