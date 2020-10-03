import React from 'react';
import classNames from 'classnames';
import { DateTime } from 'luxon';

import { IconNames } from '../presentation';

import { StatusBubble } from './StatusBubble';
import { RelativeTimestamp } from './RelativeTimestamp';

import './StatusCard.less';

export interface IStatusCardProps {
  appearance: 'inactive' | 'neutral' | 'info' | 'progress' | 'success' | 'warning' | 'error' | 'archived';
  background?: boolean;
  iconName: IconNames;
  title: React.ReactNode;
  timestamp?: DateTime;
  description?: React.ReactNode;
  actions?: React.ReactNode;
}

export const StatusCard = ({
  appearance,
  background,
  iconName,
  title,
  timestamp,
  description,
  actions,
}: IStatusCardProps) => (
  <div
    className={classNames(
      'StatusCard flex-container-h space-between middle wrap sp-padding-s-yaxis sp-padding-l-xaxis',
      `status-card-${appearance}`,
      { 'with-background': !!background },
    )}
  >
    <div className="flex-container-h middle">
      <div className="flex-container-h center middle sp-margin-l-right">
        <StatusBubble iconName={iconName} appearance={appearance} size="medium" />
      </div>
      <div className="sp-margin-m-right" style={{ minWidth: 33 }}>
        {timestamp && <RelativeTimestamp timestamp={timestamp} clickToCopy={true} />}
      </div>
      <div className="flex-container-v sp-margin-xs-yaxis">
        <div className="text-bold">{title}</div>
        {description && <div className="text-regular">{description}</div>}
      </div>
    </div>
    {actions && <div className="flex-container-h right middle flex-grow sp-margin-s-left">{actions}</div>}
  </div>
);
