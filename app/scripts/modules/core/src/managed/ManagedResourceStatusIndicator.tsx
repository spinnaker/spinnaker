import * as React from 'react';
import * as classNames from 'classnames';

import { IManagedResourceSummary } from 'core/managed';

import './ManagedResourceStatusIndicator.less';

const resourceStatusToClassNames = {
  ACTUATING: {
    iconClass: 'icon-md-actuating',
    colorClass: 'info',
  },
  CREATED: {
    iconClass: 'icon-md-created',
    colorClass: 'info',
  },
  DIFF: {
    iconClass: 'icon-md-diff',
    colorClass: 'info',
  },
  ERROR: {
    iconClass: 'icon-md-error',
    colorClass: 'error',
  },
  HAPPY: {
    iconClass: 'icon-md',
    colorClass: 'info',
  },
  PAUSED: {
    iconClass: 'icon-md-paused',
    colorClass: 'warning',
  },
  UNHAPPY: {
    iconClass: 'icon-md-flapping',
    colorClass: 'error',
  },
  UNKNOWN: {
    iconClass: 'icon-md-unknown',
    colorClass: 'warning',
  },
};

export interface IManagedResourceStatusIndicatorProps {
  shape: 'square' | 'circle';
  resourceSummary: IManagedResourceSummary;
}

export const ManagedResourceStatusIndicator = ({
  shape,
  resourceSummary: { status },
}: IManagedResourceStatusIndicatorProps) => {
  return (
    <div
      className={classNames(
        'flex-container-h middle ManagedResourceStatusIndicator',
        shape,
        resourceStatusToClassNames[status].colorClass,
      )}
    >
      <i className={classNames('fa', resourceStatusToClassNames[status].iconClass)} />
    </div>
  );
};
