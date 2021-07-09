import React from 'react';

import { Application } from '../../../../application/application.model';
import { ConfirmationModalService } from '../../../../confirmationModal';
import { IExecution, IExecutionStage } from '../../../../domain';
import { ReactInjector } from '../../../../reactShims';
import { duration } from '../../../../utils/timeFormatters';

export const DEFAULT_SKIP_WAIT_TEXT = 'The pipeline will proceed immediately, marking this stage completed.';

export interface ISkipConditionWaitProps {
  execution: IExecution;
  stage: IExecutionStage;
  application: Application;
}

const skipRemainingWait = (
  event: React.MouseEvent<HTMLElement>,
  stage: IExecutionStage,
  execution: IExecution,
  application: Application,
): void => {
  const { executionService } = ReactInjector;
  (event.target as HTMLElement).blur(); // forces closing of the popover when the modal opens
  const matcher = ({ stages }: IExecution) => {
    const match = stages.find((test) => test.id === stage.id);
    return match.status !== 'RUNNING';
  };

  const data = { status: 'SKIPPED' };
  ConfirmationModalService.confirm({
    header: 'Really skip wait?',
    buttonText: 'Skip',
    body: stage.context.skipWaitText || DEFAULT_SKIP_WAIT_TEXT,
    submitMethod: () => {
      return executionService
        .patchExecution(execution.id, stage.id, data)
        .then(() => executionService.waitUntilExecutionMatches(execution.id, matcher))
        .then((updated) => executionService.updateExecution(application, updated));
    },
  });
};

export const SkipConditionWait = ({ stage, execution, application }: ISkipConditionWaitProps) => {
  const { conditions } = stage.outputs;
  return (
    <div>
      <div>
        {conditions && conditions.length > 0 && (
          <ul className="nostyle" style={{ marginBottom: '10px' }}>
            {conditions.map(({ name, description }: { name: string; description: string }, index: number) => (
              <li key={name + index}>
                <b>{name}</b>: {description}
              </li>
            ))}
          </ul>
        )}
        {stage.context.status === 'SKIPPED' && <span>(skipped after {duration(stage.runningTimeInMs)})</span>}
      </div>
      {stage.isSuspended && (
        <div className="action-buttons">
          <button
            className="btn btn-xs btn-primary"
            onClick={(event) => skipRemainingWait(event, stage, execution, application)}
          >
            <span style={{ marginRight: '5px' }} className="small glyphicon glyphicon-fast-forward" />
            Skip remaining wait
          </button>
        </div>
      )}
    </div>
  );
};
