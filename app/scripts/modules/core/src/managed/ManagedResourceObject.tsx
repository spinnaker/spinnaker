import React, { memo } from 'react';
import { useSref } from '@uirouter/react';

import { Application } from 'core/application';
import { IManagedResourceSummary, IManagedEnvironmentSummary, IManagedArtifactSummary } from '../domain/IManagedEntity';
import { getKindName } from './ManagedReader';

import { ObjectRow } from './ObjectRow';
import { AnimatingPill, Pill } from './Pill';
import { getResourceIcon, getExperimentalDisplayLink } from './resources/resourceRegistry';
import { getArtifactVersionDisplayName } from './displayNames';
import { StatusBubble } from './StatusBubble';
import { viewConfigurationByStatus } from './managedResourceStatusConfig';
import { ManagedResourceStatusPopover } from './ManagedResourceStatusPopover';
import { Icon } from '@spinnaker/presentation';

export interface IManagedResourceObjectProps {
  application: Application;
  resource: IManagedResourceSummary;
  environment?: string;
  showReferenceName?: boolean;
  artifactVersionsByState?: IManagedEnvironmentSummary['artifacts'][0]['versions'];
  artifactDetails?: IManagedArtifactSummary;
  depth?: number;
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

export const ManagedResourceObject = memo(
  ({
    application,
    resource,
    environment,
    showReferenceName,
    artifactVersionsByState,
    artifactDetails,
    depth,
  }: IManagedResourceObjectProps) => {
    const { kind, displayName } = resource;

    const routingInfo = getNativeResourceRoutingInfo(resource) ?? { state: '', params: {} };
    const routeProps = useSref(routingInfo.state, routingInfo.params);

    const displayLink = getExperimentalDisplayLink(resource);
    const displayLinkProps = displayLink && { href: displayLink, target: '_blank', rel: 'noopener noreferrer' };

    const linkProps = routeProps.href ? routeProps : displayLinkProps;

    const current =
      artifactVersionsByState?.current &&
      artifactDetails?.versions.find(({ version }) => version === artifactVersionsByState?.current);
    const deploying =
      artifactVersionsByState?.deploying &&
      artifactDetails?.versions.find(({ version }) => version === artifactVersionsByState?.deploying);

    const isCurrentVersionPinned = !!current?.environments.find(({ name }) => name === environment)?.pinned;
    const currentPill = current && (
      <Pill
        text={`${getArtifactVersionDisplayName(current)}${showReferenceName ? ' ' + artifactDetails.reference : ''}`}
        bgColor={isCurrentVersionPinned ? 'var(--color-status-warning)' : null}
        textColor={isCurrentVersionPinned ? 'var(--color-icon-dark)' : null}
      />
    );
    const deployingPill = deploying && (
      <>
        <Icon appearance="neutral" name="caretRight" size="medium" />
        <AnimatingPill
          text={`${getArtifactVersionDisplayName(deploying)}${
            showReferenceName ? ' ' + artifactDetails.reference : ''
          }`}
          textColor="var(--color-icon-neutral)"
        />
      </>
    );

    const viewConfig = viewConfigurationByStatus[resource.status];
    const resourceStatus = resource.status !== 'HAPPY' && viewConfig && (
      <ManagedResourceStatusPopover application={application} placement="left" resourceSummary={resource}>
        <StatusBubble appearance={viewConfig.appearance} iconName={viewConfig.iconName} size="small" />
      </ManagedResourceStatusPopover>
    );

    return (
      <ObjectRow
        icon={getResourceIcon(kind)}
        title={linkProps ? <a {...linkProps}>{displayName}</a> : displayName}
        depth={depth}
        content={resourceStatus}
        metadata={
          <span className="flex-container-h middle">
            {currentPill}
            {deployingPill}
          </span>
        }
      />
    );
  },
);
