import * as React from 'react';
import { get } from 'lodash';

import { Application } from 'core/application';
import { IExecution, IExecutionStage, IExecutionStageSummary, IStageTypeConfig } from 'core/domain';
import { NgReact } from 'core/reactShims';

export interface IStageSummaryProps {
  application: Application;
  execution: IExecution;
  config: IStageTypeConfig;
  stage: IExecutionStage;
  stageSummary: IExecutionStageSummary;
}

export class StageSummary extends React.Component<IStageSummaryProps> {
  private getSourceUrl(): string {
    return get(this.props, 'config.executionSummaryUrl', require('../config/stages/common/executionSummary.html'));
  }

  public render(): React.ReactElement<StageSummary> {
    const sourceUrl = this.getSourceUrl();
    if (sourceUrl) {
      const { application, execution, stage, stageSummary } = this.props;
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
    return null;
  }
}
