import React from 'react';

import { StageSummaryWrapper } from './StageSummaryWrapper';
import type { Application } from '../../application';
import type { IExecution, IExecutionStage, IExecutionStageSummary, IStageTypeConfig } from '../../domain';

export interface IStageSummaryProps {
  application: Application;
  execution: IExecution;
  config: IStageTypeConfig;
  stage: IExecutionStage;
  stageSummary: IExecutionStageSummary;
}

export function StageSummary(props: IStageSummaryProps) {
  const { application, execution, stage, stageSummary, config } = props;

  // AngularJS override
  const sourceUrl = config?.executionSummaryUrl ?? require('../config/stages/common/executionSummary.html');
  // React override
  const SummaryComponent = config?.executionSummaryComponent;

  if (SummaryComponent) {
    return (
      <div className="stage-summary">
        <SummaryComponent {...props} />
      </div>
    );
  }

  return (
    <div className="stage-summary">
      <StageSummaryWrapper
        application={application}
        execution={execution}
        sourceUrl={sourceUrl}
        stage={stage}
        stageSummary={stageSummary}
      />
    </div>
  );
}
