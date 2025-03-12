import classnames from 'classnames';
import React from 'react';

import type { IIconProps } from '@spinnaker/presentation';
import { IconTooltip } from '../../presentation';

import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';

export interface IEnvironmentItemProps {
  title: string | React.ReactElement;
  className?: string;
  iconTooltip: string;
  iconName: IIconProps['name'];
  withPadding?: boolean;
  size?: 'regular' | 'small';
  rightElement?: React.ReactElement;
}

export const EnvironmentItem: React.FC<IEnvironmentItemProps> = ({
  title,
  size = 'regular',
  iconName,
  iconTooltip,
  className,
  rightElement,
  withPadding = true,
  children,
}) => {
  return (
    <div className={classnames(className, 'environment-row-element', { 'with-padding': withPadding })}>
      <div className={classnames('row-icon', size)}>
        <IconTooltip
          tooltip={iconTooltip}
          name={iconName}
          color="primary-g1"
          size={size === 'regular' ? '17px' : '15px'}
          delayShow={TOOLTIP_DELAY_SHOW}
        />
      </div>
      <div className="row-details">
        <div className={classnames('row-title', size)}>
          {title}
          {rightElement && <div>{rightElement}</div>}
        </div>
        {children}
      </div>
    </div>
  );
};
