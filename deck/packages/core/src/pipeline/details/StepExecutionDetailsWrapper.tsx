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
  configSections: string[];
  execution: IExecution;
  provider?: string;
  stage: IExecutionStage;
  sourceUrl: string;
}

export function StepExecutionDetailsWrapperComponent(props: IStepExecutionDetailsWrapperProps & IRouterInjectedProps) {
  const { application, config, configSections, execution, provider, stage } = props;
  const ExecutionDetailsComponent = config?.executionDetailsComponent;

  if (ExecutionDetailsComponent) {
    return (
      <ExecutionDetailsComponent
        application={application}
        config={config}
        configSections={configSections}
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
