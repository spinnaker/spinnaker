import classNames from 'classnames';
import React from 'react';

import { Icon } from '@spinnaker/presentation';
import { Application } from 'core/application';
import { IManagedResourceSummary } from 'core/domain';

import { ManagedResourceStatusPopover } from './ManagedResourceStatusPopover';
import { viewConfigurationByStatus } from './managedResourceStatusConfig';

import './ManagedResourceStatusIndicator.less';

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
