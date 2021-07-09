import React from 'react';

import { Icon, IconNames } from '@spinnaker/presentation';

import './ColumnHeader.less';

export interface IColumnHeaderProps {
  text: string;
  icon: IconNames;
}

export function ColumnHeader({ text, icon }: IColumnHeaderProps) {
  return (
    <div className="ColumnHeader">
      {icon && <Icon name={icon} appearance="light" size="medium" className="column-header-icon" />}
      {text && (
        <span className="text-bold column-header-text" style={icon && { marginRight: '56px' }}>
          {text}
        </span>
      )}
    </div>
  );
}
