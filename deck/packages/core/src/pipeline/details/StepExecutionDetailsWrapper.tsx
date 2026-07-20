import React from 'react';

import type { Application } from '../../application';
import type { IExecution, IExecutionStage, IStageTypeConfig } from '../../domain';
import { AngularJSAdapter } from '../../reactShims';

export interface IStepExecutionDetailsWrapperProps {
  application: Application;
  config: IStageTypeConfig;
  configSections: string[];
  execution: IExecution;
  stage: IExecutionStage;
  sourceUrl: string;
}

export function StepExecutionDetailsWrapper(props: IStepExecutionDetailsWrapperProps) {
  return (
    <AngularJSAdapter
      template={`
        <step-execution-details
          application="props.application"
          config="props.config"
          config-sections="props.configSections"
          execution="props.execution"
          source-url="props.sourceUrl"
          stage="props.stage">
        </step-execution-details>
      `}
      locals={props}
    />
  );
}
