import * as React from 'react';
import { get } from 'lodash';
import { BindAll } from 'lodash-decorators';

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

export interface IStageSummaryState {
}

@BindAll()
export class StageSummary extends React.Component<IStageSummaryProps, IStageSummaryState> {

  private getSourceUrl(): string {
    return get(this.props, 'config.executionSummaryUrl', require('../../pipeline/config/stages/core/executionSummary.html'));
  }

  public render(): React.ReactElement<StageSummary> {
    const sourceUrl = this.getSourceUrl();
    if (sourceUrl) {
      const { application, execution, stage, stageSummary } = this.props;
      const { StageSummaryWrapper } = NgReact;
      return (
        <div className="stage-summary">
          <StageSummaryWrapper application={application} execution={execution} sourceUrl={sourceUrl} stage={stage} stageSummary={stageSummary} />
        </div>
      );
    }
    return null;
  }
}
