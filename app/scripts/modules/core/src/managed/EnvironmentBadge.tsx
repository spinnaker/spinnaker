import React from 'react';
import classNames from 'classnames';

import './EnvironmentBadge.less';

export interface IEnvironmentBadgeProps {
  name: string;
  critical?: boolean;
  size?: 'extraSmall' | 'small' | 'medium';
}

const DEFAULT_SIZE = 'medium';

export const EnvironmentBadge = ({ name, critical, size = DEFAULT_SIZE }: IEnvironmentBadgeProps) => (
  <span
    className={classNames('EnvironmentBadge sp-padding-s-xaxis text-bold', size, {
      'sp-padding-xs-yaxis': size !== 'extraSmall',
      'sp-padding-2xs-yaxis': size === 'extraSmall',
      critical,
    })}
  >
    {name}
  </span>
);
