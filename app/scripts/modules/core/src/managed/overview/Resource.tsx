import React from 'react';

import { ResourceTask } from './ResourceTask';
import { EnvironmentItem } from '../environmentBaseElements/EnvironmentItem';
import { MdResourceActuationState, useFetchResourceStatusQuery } from '../graphql/graphql-sdk';
import { Icon, useApplicationContextSafe } from '../../presentation';
import { showManagedResourceHistoryModal } from '../resourceHistory/ManagedResourceHistoryModal';
import { ResourceTitle } from '../resources/ResourceTitle';
import { IResourceLinkProps, resourceManager } from '../resources/resourceRegistry';
import { QueryResource } from './types';
import { useLogEvent } from '../utils/logging';
import { Spinner } from '../../widgets';

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
  DELETING: { icon: 'far fa-trash-alt', defaultReason: 'Resource is being deleted' },
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
          {Boolean(state.tasks?.length) && (
            <ul className="tasks-list">
              {state.tasks?.map(({ id, name }) => (
                <ResourceTask key={id} id={id} name={name} />
              ))}
            </ul>
          )}
        </div>
      </div>
    );
  }

  return <Spinner className="sp-margin-xs-top" mode="circular" size="nano" color="var(--color-accent)" />;
};

export const Resource = ({ resource, environment }: { resource: QueryResource; environment: string }) => {
  const icon = resourceManager.getIcon(resource.kind);
  const app = useApplicationContextSafe();
  const logEvent = useLogEvent('Resource');

  const account = resource.location?.account;

  const resourceLinkProps: IResourceLinkProps = {
    kind: resource.kind,
    displayName: resource.displayName,
    account,
    detail: resource.moniker?.detail,
    stack: resource.moniker?.stack,
  };

  const regions = resource.location?.regions || [];

  return (
    <EnvironmentItem
      iconName={icon}
      iconTooltip={resource.kind}
      className="Resource"
      title={<ResourceTitle props={resourceLinkProps} />}
    >
      <div className="resource-metadata delimited-elements">
        <span>
          {regions.map((region, index) => (
            <span key={region}>
              {region}
              {index < regions.length - 1 && ', '}
            </span>
          ))}
        </span>
        {account && <span>{account}</span>}
        <span>
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              showManagedResourceHistoryModal({ id: resource.id, displayName: resource.displayName || resource.id });
              logEvent({ action: 'ViewHistory' });
            }}
          >
            View history
          </a>
        </span>
      </div>
      <div>
        <Status appName={app.name} environmentName={environment} resourceId={resource.id} />
      </div>
    </EnvironmentItem>
  );
};
