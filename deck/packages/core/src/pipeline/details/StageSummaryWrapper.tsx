import React from 'react';

import type { Application } from '../../application';
import type { IExecution, IExecutionStage, IExecutionStageSummary } from '../../domain';
import { AngularJSAdapter } from '../../reactShims';

export interface IStageSummaryWrapperProps {
  application: Application;
  execution: IExecution;
  sourceUrl: string;
  stage: IExecutionStage;
  stageSummary: IExecutionStageSummary;
}

export function StageSummaryWrapper(props: IStageSummaryWrapperProps) {
  return (
    <AngularJSAdapter
      template={`
        <stage-summary
          application="props.application"
          execution="props.execution"
          source-url="props.sourceUrl"
          stage="props.stage"
          stage-summary="props.stageSummary">
        </stage-summary>
      `}
      locals={props}
    />
  );
}
