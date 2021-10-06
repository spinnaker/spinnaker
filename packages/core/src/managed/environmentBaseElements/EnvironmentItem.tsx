import classnames from 'classnames';
import React from 'react';

import type { IIconProps } from '@spinnaker/presentation';
import { IconTooltip } from '../../presentation';

import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';

interface IEnvironmentItemProps {
  title: string | React.ReactElement;
  className?: string;
  iconTooltip: string;
  iconName: IIconProps['name'];
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
  children,
}) => {
  return (
    <div className={classnames(className, 'environment-row-element')}>
      <div className={classnames('row-icon', size)}>
        <IconTooltip
          tooltip={iconTooltip}
          name={iconName}
          color="primary-g1"
          size={size === 'regular' ? '16px' : '14px'}
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
