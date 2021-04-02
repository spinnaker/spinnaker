import { useSref } from '@uirouter/react';
import React, { memo } from 'react';

import { Icon } from '@spinnaker/presentation';
import { Application } from 'core/application';
import { Tooltip } from 'core/presentation';

import { getKindName } from './ManagedReader';
import { ManagedResourceStatusPopover } from './ManagedResourceStatusPopover';
import { StatusBubble } from './StatusBubble';
import { IManagedResourceSummary } from '../domain/IManagedEntity';
import { viewConfigurationByStatus } from './managedResourceStatusConfig';
import { ResourceDeploymentStatus, ResourceDeploymentStatusProps } from './overview/ResourceDeploymentStatus';
import { showManagedResourceHistoryModal } from './resourceHistory/ManagedResourceHistoryModal';
import { resourceManager } from './resources/resourceRegistry';

import './ObjectRow.less';

export interface IManagedResourceObjectProps {
  application: Application;
  resource: IManagedResourceSummary;
  depth?: number;
  metadata?: ResourceDeploymentStatusProps;
}

// We'll add detail drawers for resources soon, but in the meantime let's link
// to infrastructure views for 'native' Spinnaker resources in a one-off way
// so the registry doesn't have to know about it.
const getNativeResourceRoutingInfo = (
  resource: IManagedResourceSummary,
): { state: string; params: { [key: string]: string } } | null => {
  const {
    kind,
    moniker,
    displayName,
    locations: { account },
  } = resource;
  const kindName = getKindName(kind);
  const params = {
    acct: account,
    stack: moniker?.stack ?? '(none)',
    detail: moniker?.detail ?? '(none)',
    q: displayName,
  };

  switch (kindName) {
    case 'cluster':
      return { state: 'home.applications.application.insight.clusters', params };

    case 'security-group':
      return { state: 'home.applications.application.insight.firewalls', params };

    case 'classic-load-balancer':
    case 'application-load-balancer':
      return { state: 'home.applications.application.insight.loadBalancers', params };
  }

  return null;
};

const EventsLink = (props: Pick<IManagedResourceSummary, 'id' | 'displayName'>) => {
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

    const routingInfo = getNativeResourceRoutingInfo(resource) ?? { state: '', params: {} };
    const routeProps = useSref(routingInfo.state, routingInfo.params);

    const displayLink = resourceManager.getExperimentalDisplayLink(resource);
    const displayLinkProps = displayLink && { href: displayLink, target: '_blank', rel: 'noopener noreferrer' };

    const linkProps = routeProps.href ? routeProps : displayLinkProps;
    const title = linkProps ? <a {...linkProps}>{displayName}</a> : displayName;

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
            <span className="object-row-title">{title}</span>
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
