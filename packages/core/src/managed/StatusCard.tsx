import classNames from 'classnames';
import { DateTime } from 'luxon';
import React from 'react';

import { IconNames } from '@spinnaker/presentation';

import { RelativeTimestamp } from './RelativeTimestamp';
import { StatusBubble } from './StatusBubble';

import './StatusCard.less';

export interface IStatusCardProps {
  appearance: 'future' | 'neutral' | 'info' | 'progress' | 'success' | 'warning' | 'error' | 'archived';
  background?: boolean;
  active?: boolean;
  iconName: IconNames;
  title: React.ReactNode;
  timestamp?: DateTime | string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
}

export const StatusCard: React.FC<IStatusCardProps> = ({
  appearance,
  background,
  active,
  iconName,
  title,
  timestamp,
  description,
  actions,
}) => {
  let timestampAsDateTime: DateTime | undefined = undefined;
  try {
    if (timestamp) {
      timestampAsDateTime = typeof timestamp === 'string' ? DateTime.fromISO(timestamp) : timestamp;
    }
  } catch (e) {
    console.error(`Failed to parse timestamp ${timestamp}`);
  }

  return (
    <div
      className={classNames(
        'StatusCard flex-container-h space-between middle wrap sp-padding-s-yaxis sp-padding-l-xaxis',
        `status-card-${appearance}`,
        { 'with-background': !!background, active: active ?? true },
      )}
    >
      <div className="flex-container-h middle">
        <div className="flex-container-h center middle sp-margin-l-right">
          <StatusBubble iconName={iconName} appearance={appearance} size="small" />
        </div>
        <div className="sp-margin-m-right" style={{ minWidth: 33 }}>
          {timestampAsDateTime && <RelativeTimestamp timestamp={timestampAsDateTime} clickToCopy={true} />}
        </div>
        <div className="flex-container-v sp-margin-xs-yaxis">
          <div className="text-bold">{title}</div>
          {description && <div className="text-regular">{description}</div>}
        </div>
      </div>
      {actions && <div className="flex-container-h right middle flex-grow sp-margin-s-left">{actions}</div>}
    </div>
  );
};
