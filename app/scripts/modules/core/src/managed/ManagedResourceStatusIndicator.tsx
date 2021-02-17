import React from 'react';
import classNames from 'classnames';

import { IManagedResourceSummary } from 'core/domain';
import { Application } from 'core/application';
import { viewConfigurationByStatus } from './managedResourceStatusConfig';

import { ManagedResourceStatusPopover } from './ManagedResourceStatusPopover';
import './ManagedResourceStatusIndicator.less';
import { Icon } from '@spinnaker/presentation';

export interface IManagedResourceStatusIndicatorProps {
  shape: 'square' | 'circle';
  resourceSummary: IManagedResourceSummary;
  application: Application;
}

export const ManagedResourceStatusIndicator = ({
  shape,
  resourceSummary,
  application,
}: IManagedResourceStatusIndicatorProps) => {
  const { status } = resourceSummary;

  return (
    <div className="flex-container-h stretch ManagedResourceStatusIndicator">
      <ManagedResourceStatusPopover application={application} placement="left" resourceSummary={resourceSummary}>
        <div className={classNames('flex-container-h middle', shape, viewConfigurationByStatus[status].appearance)}>
          <Icon appearance="light" name={viewConfigurationByStatus[status].iconName} size="small" />
        </div>
      </ManagedResourceStatusPopover>
    </div>
  );
};
