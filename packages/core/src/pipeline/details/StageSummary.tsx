import React from 'react';

import { Application } from '../../application';
import { IExecution, IExecutionStage, IExecutionStageSummary, IStageTypeConfig } from '../../domain';
import { NgReact } from '../../reactShims';

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

  const { StageSummaryWrapper } = NgReact;
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
