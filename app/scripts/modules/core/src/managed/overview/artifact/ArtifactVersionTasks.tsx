import React from 'react';

import { Tooltip } from 'core/presentation';

import { DurationRender } from '../../RelativeTimestamp';
import { VersionOperationIcon } from './VersionOperation';
import { QueryArtifactVersionTask, QueryVerificationStatus } from '../types';
import { TOOLTIP_DELAY } from '../../utils/defaults';

import './ArtifactVersionTasks.less';

const statusToText: {
  [key in QueryVerificationStatus]: string;
} = {
  FAIL: 'failed',
  FORCE_PASS: 'has been overridden',
  PASS: 'passed',
  PENDING: 'in progress',
  NOT_EVALUATED: 'has not started yet',
};

interface IArtifactVersionTaskProps {
  type: string;
  task: QueryArtifactVersionTask;
}

const ArtifactVersionTask = ({ type, task }: IArtifactVersionTaskProps) => {
  const status = task.status || 'PENDING';
  const { link, startedAt, completedAt } = task;
  return (
    <div className="version-task">
      <VersionOperationIcon status={status} />
      <div className="task-content">
        {type} {task.id} {statusToText[status]}{' '}
        {startedAt && (
          <span className="task-metadata task-runtime">
            <Tooltip value="Runtime duration" delayShow={TOOLTIP_DELAY}>
              <i className="far fa-clock" />
            </Tooltip>
            <DurationRender {...{ startedAt, completedAt }} />
          </span>
        )}
        {link && (
          <span className="task-metadata">
            <a href={link} target="_blank" rel="noreferrer">
              View logs
            </a>
          </span>
        )}
      </div>
    </div>
  );
};

interface IVerificationsProps {
  type: string;
  tasks?: QueryArtifactVersionTask[];
}

export const ArtifactVersionTasks = ({ type, tasks }: IVerificationsProps) => {
  if (!tasks || !tasks.length) return null;
  return (
    <div className="ArtifactVersionTasks">
      {tasks.map((task) => (
        <ArtifactVersionTask key={task.id} type={type} task={task} />
      ))}
    </div>
  );
};
