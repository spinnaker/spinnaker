import React from 'react';

import { DurationRender, RelativeTimestamp } from '../../RelativeTimestamp';
import { VersionOperationIcon } from './VersionOperation';
import { useRetryVersionActionMutation } from '../../graphql/graphql-sdk';
import { Tooltip, useApplicationContextSafe } from '../../../presentation';
import { QueryArtifactVersion, QueryArtifactVersionTask, QueryArtifactVersionTaskStatus } from '../types';
import { TOOLTIP_DELAY_SHOW } from '../../utils/defaults';
import { useLogEvent } from '../../utils/logging';
import { NotifierService, Spinner } from '../../../widgets';

import './ArtifactVersionTasks.less';

const statusToText: {
  [key in QueryArtifactVersionTaskStatus]: string;
} = {
  FAIL: 'failed',
  FORCE_PASS: 'has been overridden',
  PASS: 'passed',
  PENDING: 'in progress',
  NOT_EVALUATED: 'has not started yet',
};

export interface ITaskArtifactVersionProps {
  environment: string;
  version: string;
  reference: string;
  status: QueryArtifactVersion['status'];
}

interface IBaseTaskProps {
  type: string;
  artifact: ITaskArtifactVersionProps;
}

interface IArtifactVersionTaskProps extends IBaseTaskProps {
  task: QueryArtifactVersionTask;
}

const ArtifactVersionTask = ({ type, artifact, task }: IArtifactVersionTaskProps) => {
  const status = task.status || 'PENDING';
  const { link, startedAt, completedAt } = task;
  const logEvent = useLogEvent('ArtifactVersionTask');
  const app = useApplicationContextSafe();
  const [retryTask, { loading: mutationInFlight, error }] = useRetryVersionActionMutation({
    variables: {
      payload: {
        application: app.name,
        environment: artifact.environment,
        reference: artifact.reference,
        version: artifact.version,
        actionId: task.actionId,
        actionType: task.actionType,
      },
    },
  });

  React.useEffect(() => {
    if (error) {
      NotifierService.publish({
        key: task.id,
        content: `Failed to re-run ${type} - ${error.message}`,
        options: { type: 'error' },
      });
    }
  }, [error]);

  return (
    <div className="version-task">
      <VersionOperationIcon status={status} />
      <div className="task-content">
        <span className="delimited-element">
          {type} {task.actionId} {statusToText[status]}{' '}
          {startedAt && completedAt && (
            <>
              (<RelativeTimestamp timestamp={completedAt} withSuffix />)
            </>
          )}
        </span>
        {startedAt && (
          <span className="delimited-element task-runtime">
            <Tooltip value="Runtime duration" delayShow={TOOLTIP_DELAY_SHOW}>
              <i className="far fa-clock" />
            </Tooltip>
            <DurationRender {...{ startedAt, completedAt }} />
          </span>
        )}
        {link && (
          <span className="delimited-element">
            <a href={link} target="_blank" rel="noreferrer">
              View logs
            </a>
          </span>
        )}
        {status === 'FAIL' && artifact.status === 'CURRENT' && (
          <div className="sp-margin-s-top horizontal middle">
            <button
              className="btn btn-default btn-sm sp-padding-2xs-yaxis sp-padding-s-xaxis"
              disabled={mutationInFlight}
              onClick={() => {
                retryTask();
                logEvent({ action: `Retry_${type}` });
              }}
            >
              Retry {type.toLowerCase()}
            </button>
            {mutationInFlight && (
              <Spinner className="sp-margin-s-left" mode="circular" size="nano" color="var(--color-accent)" />
            )}
          </div>
        )}
      </div>
    </div>
  );
};

interface IArtifactVersionTasksProps extends IBaseTaskProps {
  tasks?: QueryArtifactVersionTask[];
}

export const ArtifactVersionTasks = ({ tasks, ...restProps }: IArtifactVersionTasksProps) => {
  if (!tasks || !tasks.length) return null;
  return (
    <div className="ArtifactVersionTasks">
      {tasks.map((task) => (
        <ArtifactVersionTask key={task.id} {...restProps} task={task} />
      ))}
    </div>
  );
};
