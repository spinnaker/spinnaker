import classNames from 'classnames';
import React from 'react';

import { ReactComponent as Star } from './icons/star.svg';
import { Tooltip } from '../presentation';

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
    })}
  >
    {critical && (
      <Tooltip value="This is a production environment">
        <span
          className={classNames('critical-symbol', {
            'sp-margin-s-right': size !== 'extraSmall',
            'sp-margin-xs-right': size === 'extraSmall',
          })}
        >
          <Star style={{ width: size === 'extraSmall' ? '12px' : '16px', fill: 'var(--color-white)' }} />
        </span>
      </Tooltip>
    )}
    {name}
  </span>
);
