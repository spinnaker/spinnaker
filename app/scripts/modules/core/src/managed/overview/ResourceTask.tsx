import { UISref } from '@uirouter/react';
import React from 'react';

import { RelativeTimestamp } from '../RelativeTimestamp';
import { IOrchestratedItem } from '../../domain';
import { robotToHuman, Tooltip } from '../../presentation';
import { LoadingAnimation } from '../../presentation/LoadingAnimation';
import { useDataWithRefresh } from '../../presentation/hooks/useDataWithRefresh.hook';
import { TaskReader } from '../../task';
import { TaskProgressBar } from '../../task/TaskProgressBar';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';

interface IResourceTaskProps {
  id: string;
  name: string;
}

const DEFAULT_ICON_CLASSNAMES = 'far fa-hourglass md-icon-pending';

const getTaskStatusIconProps = (task?: IOrchestratedItem): string => {
  if (!task) return DEFAULT_ICON_CLASSNAMES;
  if (task.isCompleted) return 'fas fa-check md-icon-success';
  if (task.isCanceled || task.isFailed) return 'fas fa-times md-icon-fail';
  return DEFAULT_ICON_CLASSNAMES;
};

export const ResourceTask = ({ id, name }: IResourceTaskProps) => {
  const { status, result } = useDataWithRefresh(() => TaskReader.getTask(id), undefined, [id]);

  if (status === 'PENDING' && !result) {
    return <LoadingAnimation />;
  }
  const iconClassnames = getTaskStatusIconProps(result);
  const currentStage = result?.steps?.find((step) => step.isRunning);
  return (
    <li key={id} className="resource-task">
      <i className={iconClassnames} />
      <div>
        <UISref to="home.applications.application.tasks.taskDetails" params={{ taskId: id }}>
          <a>{name}</a>
        </UISref>
        {result && <TaskProgressBar task={result} className="task-progress" />}
        {result && (
          <div className="delimited-elements task-metadata">
            <span>
              <Tooltip value="Started at" delayShow={TOOLTIP_DELAY_SHOW}>
                <i className="far fa-clock task-runtime" />
              </Tooltip>
              <RelativeTimestamp timestamp={result.startTime} withSuffix removeStyles />
            </span>
            {currentStage && (
              <span>
                Current stage: {robotToHuman(currentStage.name)} (
                <RelativeTimestamp timestamp={currentStage.startTime} withSuffix />)
              </span>
            )}
          </div>
        )}
      </div>
    </li>
  );
};
