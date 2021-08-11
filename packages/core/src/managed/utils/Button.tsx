import React from 'react';

import { Icon, IconNames } from '@spinnaker/presentation';

import './Button.less';

const DEFAULT_APPEARANCE = 'secondary';

export interface IButtonProps {
  iconName?: IconNames;
  appearance?: 'primary' | 'secondary';
  disabled?: boolean;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => any;
  children?: React.ReactNode;
}

export const Button = ({ iconName, appearance = DEFAULT_APPEARANCE, disabled, onClick, children }: IButtonProps) => {
  return (
    <button
      disabled={disabled}
      className={`flex-container-h center middle text-bold action-button action-button-${appearance}`}
      onClick={onClick}
    >
      {iconName && (
        <div className="sp-margin-s-right flex-container-h center middle">
          <Icon name={iconName} appearance={appearance === 'primary' ? 'light' : 'neutral'} size="small" />
        </div>
      )}
      {children}
    </button>
  );
};
