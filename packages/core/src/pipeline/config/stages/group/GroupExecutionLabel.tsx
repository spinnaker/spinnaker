import React from 'react';

import { GroupExecutionPopover } from './GroupExecutionPopover';
import { Application } from '../../../../application/application.model';
import { ExecutionBarLabel } from '../common/ExecutionBarLabel';
import { IExecution, IExecutionStageSummary } from '../../../../domain';
import { ReactInjector } from '../../../../reactShims';

import './groupStage.less';

export interface IGroupExecutionLabelProps {
  stage: IExecutionStageSummary;
  execution: IExecution;
  application: Application;
  executionMarker: boolean;
  width?: number;
}

export class GroupExecutionLabel extends React.Component<IGroupExecutionLabelProps> {
  private subStageClicked = (groupStage: IExecutionStageSummary, stage: IExecutionStageSummary): void => {
    const { executionService } = ReactInjector;
    executionService.toggleDetails(this.props.execution, groupStage.index, stage.index);
  };

  public render() {
    const { stage, width } = this.props;

    if (!this.props.executionMarker) {
      return <ExecutionBarLabel {...this.props} />;
    }

    return (
      <GroupExecutionPopover stage={stage} subStageClicked={this.subStageClicked} width={width}>
        {this.props.children}
      </GroupExecutionPopover>
    );
  }
}
