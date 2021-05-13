import React from 'react';

import { Icon, useApplicationContextSafe } from 'core/presentation';
import { IconTooltip } from 'core/presentation/IconTooltip';

import { MdResourceActuationState, useFetchResourceStatusQuery } from '../graphql/graphql-sdk';
import spinner from './loadingIndicator.svg';
import { showManagedResourceHistoryModal } from '../resourceHistory/ManagedResourceHistoryModal';
import { ResourceTitle } from '../resources/ResourceTitle';
import { IResourceLinkProps, resourceManager } from '../resources/resourceRegistry';
import { QueryResource } from './types';
import { TOOLTIP_DELAY } from '../utils/defaults';

import './Resource.less';

const statusUtils: {
  [key in Exclude<MdResourceActuationState['status'], 'UP_TO_DATE'>]: {
    color?: string;
    icon: string;
    defaultReason: string;
  };
} = {
  ERROR: { color: 'var(--color-status-error)', icon: 'fas fa-times', defaultReason: 'Failed to update resource' },
  NOT_MANAGED: { color: 'var(--color-status-warning)', icon: 'fas fa-pause', defaultReason: 'Resource is not managed' },
  WAITING: { icon: 'far fa-hourglass', defaultReason: 'Resource is currently locked and can not be updated' },
  PROCESSING: { icon: 'far fa-hourglass', defaultReason: 'Resource is being updated' },
};

const Status = ({
  appName,
  environmentName,
  resourceId,
}: {
  appName: string;
  environmentName: string;
  resourceId: string;
}) => {
  const { data: resourceStatuses, error, loading } = useFetchResourceStatusQuery({ variables: { appName } });
  const state = resourceStatuses?.application?.environments
    .find((env) => env.name === environmentName)
    ?.state.resources?.find((resource) => resource.id === resourceId)?.state;

  if (error || (!loading && !state)) {
    return (
      <div className="resource-status">
        <Icon name="mdUnknown" size="14px" color="status-warning" />
        <span>Failed to fetch resource status</span>
      </div>
    );
  }

  if (state) {
    if (state.status === 'UP_TO_DATE') return null;

    return (
      <div className="resource-status">
        <i
          className={statusUtils[state.status].icon}
          style={{ color: statusUtils[state.status].color || 'var(--color-titanium)' }}
        />
        <div>
          <div>{state.reason || statusUtils[state.status].defaultReason}</div>
          {state.event && state.event !== state.reason && <div>{state.event}</div>}
        </div>
      </div>
    );
  }

  return <img src={spinner} height={14} />;
};

export const Resource = ({ resource, environment }: { resource: QueryResource; environment: string }) => {
  const icon = resourceManager.getIcon(resource.kind);
  const app = useApplicationContextSafe();

  const resourceLinkProps: IResourceLinkProps = {
    kind: resource.kind,
    displayName: resource.displayName,
    account: resource.location?.account,
    detail: resource.moniker?.detail,
    stack: resource.moniker?.stack,
  };

  const regions = resource.location?.regions || [];

  return (
    <div className="Resource environment-row-element">
      <div className="row-icon">
        <IconTooltip tooltip={resource.kind} name={icon} color="primary-g1" delayShow={TOOLTIP_DELAY} />
      </div>
      <div className="row-details">
        <div className="row-title">
          <ResourceTitle props={resourceLinkProps} />
        </div>
        <div className="resource-metadata">
          <span>
            {regions.map((region, index) => (
              <span key={region}>
                {region}
                {index < regions.length - 1 && ', '}
              </span>
            ))}
          </span>
          <span>
            <a
              href="#"
              onClick={(e) => {
                e.preventDefault();
                showManagedResourceHistoryModal({ id: resource.id, displayName: resource.displayName || resource.id });
              }}
            >
              View logs
            </a>
          </span>
        </div>
        <div>
          <Status appName={app.name} environmentName={environment} resourceId={resource.id} />
        </div>
      </div>
    </div>
  );
};
