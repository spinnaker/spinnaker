import classnames from 'classnames';
import React from 'react';

import { ResourceTask } from './ResourceTask';
import { ConfirmationModalService } from '../../confirmationModal/confirmationModal.service';
import type { IEnvironmentItemProps } from '../environmentBaseElements/EnvironmentItem';
import { EnvironmentItem } from '../environmentBaseElements/EnvironmentItem';
import type { MdResourceActuationState } from '../graphql/graphql-sdk';
import {
  FetchResourceStatusDocument,
  useFetchResourceStatusQuery,
  useToggleResourceManagementMutation,
} from '../graphql/graphql-sdk';
import { Icon, Markdown, useApplicationContextSafe } from '../../presentation';
import { showManagedResourceHistoryModal } from '../resourceHistory/ManagedResourceHistoryModal';
import { showResourceDefinitionModal } from '../resources/ResourceDefinitionModal';
import { ResourceTitle } from '../resources/ResourceTitle';
import { ToggleResourceManagement } from '../resources/ToggleResourceManagement';
import { resourceManager } from '../resources/resourceRegistry';
import type { QueryResource } from './types';
import { getIsDebugMode } from '../utils/debugMode';
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
  NOT_MANAGED: {
    color: 'var(--color-status-warning)',
    icon: 'fas fa-pause',
    defaultReason: 'Resource management is paused',
  },
  WAITING: { icon: 'far fa-hourglass', defaultReason: 'Resource is currently locked and can not be updated' },
  PROCESSING: { icon: 'far fa-hourglass', defaultReason: 'Resource is being updated' },
  DELETING: { icon: 'far fa-trash-alt', defaultReason: 'Resource is being deleted' },
};

interface IStatusProps {
  appName: string;
  environmentName: string;
  resourceId: string;
  regions: string[];
  account?: string;
}

const Status = ({ appName, environmentName, resourceId, regions, account }: IStatusProps) => {
  const { data: resourceStatuses, error, loading } = useFetchResourceStatusQuery({ variables: { appName } });
  const [enableResourceManagement] = useToggleResourceManagementMutation({
    variables: { payload: { id: resourceId, isPaused: false } },
    refetchQueries: [{ query: FetchResourceStatusDocument, variables: { appName } }],
  });
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
  if (!state) return <Spinner className="sp-margin-xs-top" mode="circular" size="nano" color="var(--color-accent)" />;
  if (state.status === 'UP_TO_DATE') return null;
  const isNotManaged = state.status === 'NOT_MANAGED';
  const reasonElem = (
    <div>
      {state.reason || statusUtils[state.status].defaultReason}
      {isNotManaged ? ' (click to enable...)' : undefined}
    </div>
  );
  return (
    <div className="resource-status">
      <i
        className={statusUtils[state.status].icon}
        style={{
          color: statusUtils[state.status].color || 'var(--color-titanium)',
        }}
      />
      <div>
        {isNotManaged ? (
          <button
            className="as-link"
            onClick={() => {
              ConfirmationModalService.confirm({
                header: `Really resume resource management?`,
                bodyContent: <ToggleResourceManagement isPaused regions={regions} />,
                account: account,
                buttonText: `Resume management`,
                submitMethod: enableResourceManagement,
              });
            }}
          >
            {reasonElem}
          </button>
        ) : (
          reasonElem
        )}
        {state.event && state.event !== state.reason && <Markdown className="event" message={state.event} />}
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
};

interface IBaseResourceProps {
  resource: QueryResource;
  environment: string;
}

const ResourceMetadata = ({ resource }: IBaseResourceProps) => {
  const logEvent = useLogEvent('Resource');
  const isDebug = getIsDebugMode();

  const account = resource.location?.account;
  const regions = resource.location?.regions || [];

  return (
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
      {isDebug && (
        <span>
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              showResourceDefinitionModal({ resource: resource });
              logEvent({ action: 'ViewDefinition' });
            }}
          >
            View definition
          </a>
        </span>
      )}
    </div>
  );
};

export const Resource = ({
  resource,
  environment,
  withPadding,
  size,
  className,
}: IBaseResourceProps & Pick<IEnvironmentItemProps, 'size' | 'withPadding' | 'className'>) => {
  const icon = resourceManager.getIcon(resource.kind);
  const app = useApplicationContextSafe();

  const account = resource.location?.account;
  const regions = resource.location?.regions || [];

  return (
    <EnvironmentItem
      iconName={icon}
      iconTooltip={resource.kind}
      className={classnames('Resource', className)}
      title={<ResourceTitle resource={resource} />}
      size={size}
      withPadding={withPadding}
    >
      <ResourceMetadata environment={environment} resource={resource} />
      <div>
        <Status
          appName={app.name}
          environmentName={environment}
          resourceId={resource.id}
          account={account}
          regions={regions}
        />
      </div>
    </EnvironmentItem>
  );
};
