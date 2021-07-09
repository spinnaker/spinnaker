import React, { memo } from 'react';

import { Icon } from '@spinnaker/presentation';

import { ManagedResourceStatusPopover } from './ManagedResourceStatusPopover';
import { StatusBubble } from './StatusBubble';
import { Application } from '../application';
import { IManagedResourceSummary } from '../domain/IManagedEntity';
import { viewConfigurationByStatus } from './managedResourceStatusConfig';
import { ResourceDeploymentStatus, ResourceDeploymentStatusProps } from './overview/ResourceDeploymentStatus';
import { Tooltip } from '../presentation';
import { showManagedResourceHistoryModal } from './resourceHistory/ManagedResourceHistoryModal';
import { ResourceTitle } from './resources/ResourceTitle';
import { IResourceLinkProps, resourceManager } from './resources/resourceRegistry';

import './ObjectRow.less';

export interface IManagedResourceObjectProps {
  application: Application;
  resource: IManagedResourceSummary;
  depth?: number;
  metadata?: ResourceDeploymentStatusProps;
}

export const EventsLink = (props: Pick<IManagedResourceSummary, 'id' | 'displayName'>) => {
  return (
    <Tooltip placement="top" value="Open resource history">
      <a
        href="#"
        className="resource-events-link"
        onClick={(e) => {
          e.preventDefault();
          showManagedResourceHistoryModal(props);
        }}
      >
        <Icon name="history" size="extraSmall" />
      </a>
    </Tooltip>
  );
};

export const ManagedResourceObject = memo(
  ({ application, resource, metadata, depth = 0 }: IManagedResourceObjectProps) => {
    const { kind, displayName } = resource;

    const resourceLinkProps: IResourceLinkProps = {
      kind,
      displayName,
      account: resource.locations.account,
      detail: resource.moniker?.detail,
      stack: resource.moniker?.stack,
    };

    const viewConfig = viewConfigurationByStatus[resource.status];

    const resourceStatus =
      resource.status !== 'HAPPY' && viewConfig ? (
        <ManagedResourceStatusPopover application={application} placement="left" resourceSummary={resource}>
          <StatusBubble appearance={viewConfig.appearance} iconName={viewConfig.iconName} size="small" />
        </ManagedResourceStatusPopover>
      ) : undefined;

    return (
      <div className="ObjectRow" style={{ marginLeft: 16 * depth }}>
        <span className="object-row-content">
          <div className="object-row-column object-row-title-column">
            <Icon name={resourceManager.getIcon(kind)} size="medium" appearance="dark" className="sp-margin-s-right" />
            <span className="object-row-title">
              <ResourceTitle props={resourceLinkProps} />
            </span>
          </div>
          <div className="object-row-column flex-grow">
            {resourceStatus}
            <div className="flex-pull-right flex-container-h middle">
              <EventsLink id={resource.id} displayName={resource.displayName} />
              {metadata && <ResourceDeploymentStatus {...metadata} />}
            </div>
          </div>
        </span>
      </div>
    );
  },
);
