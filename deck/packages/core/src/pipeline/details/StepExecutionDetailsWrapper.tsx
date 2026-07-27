import React from 'react';

import { StageFailureMessage } from './StageFailureMessage';
import type { Application } from '../../application';
import { ExecutionStepDetails } from '../config/stages/common/ExecutionStepDetails';
import type { IExecution, IExecutionStage, IStageTypeConfig } from '../../domain';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';

export interface IStepExecutionDetailsWrapperProps {
  application: Application;
  config?: IStageTypeConfig;
  execution: IExecution;
  provider?: string;
  stage: IExecutionStage;
}

export function StepExecutionDetailsWrapperComponent(props: IStepExecutionDetailsWrapperProps & IRouterInjectedProps) {
  const { application, config, execution, provider, stage } = props;
  const ExecutionDetailsComponent = config?.executionDetailsComponent;

  if (ExecutionDetailsComponent) {
    return (
      <ExecutionDetailsComponent
        application={application}
        config={config}
        currentSection={props.stateParams.details}
        execution={execution}
        provider={provider || ''}
        stage={stage}
      />
    );
  }

  return (
    <div>
      <div className="step-section-details">
        <div className="row">
          <ExecutionStepDetails item={stage} />
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
    </div>
  );
}

export const StepExecutionDetailsWrapper = withRouter(StepExecutionDetailsWrapperComponent);
