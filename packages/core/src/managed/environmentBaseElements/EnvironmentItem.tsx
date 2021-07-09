import classnames from 'classnames';
import React from 'react';

import { IIconProps } from '@spinnaker/presentation';
import { IconTooltip } from '../../presentation';

import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';

interface IEnvironmentItemProps {
  title: string | React.ReactElement;
  className?: string;
  iconTooltip: string;
  iconName: IIconProps['name'];
  size?: 'regular' | 'small';
}

export const EnvironmentItem: React.FC<IEnvironmentItemProps> = ({
  title,
  size = 'regular',
  iconName,
  iconTooltip,
  className,
  children,
}) => {
  return (
    <div className={classnames(className, 'environment-row-element')}>
      <div className={classnames('row-icon', size)}>
        <IconTooltip
          tooltip={iconTooltip}
          name={iconName}
          color="primary-g1"
          size={size === 'regular' ? '18px' : '16px'}
          delayShow={TOOLTIP_DELAY_SHOW}
        />
      </div>
      <div className="row-details">
        <div className={classnames('row-title', size)}>{title}</div>
        {children}
      </div>
    </div>
  );
};
