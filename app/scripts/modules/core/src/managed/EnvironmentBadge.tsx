import React from 'react';
import classNames from 'classnames';

import './EnvironmentBadge.less';

export interface IEnvironmentBadgeProps {
  name: string;
  critical?: boolean;
  size?: 'small' | 'medium';
}

const DEFAULT_SIZE = 'medium';

export const EnvironmentBadge = ({ name, critical, size = DEFAULT_SIZE }: IEnvironmentBadgeProps) => (
  <span className={classNames('EnvironmentBadge sp-padding-xs-yaxis sp-padding-s-xaxis text-bold', size, { critical })}>
    {name}
  </span>
);
