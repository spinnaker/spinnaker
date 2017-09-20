import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { IExecutionStageSummary, IExecution } from 'core/domain';
import { Application } from 'core/application/application.model';
import { ExecutionBarLabel } from 'core/pipeline/config/stages/core/ExecutionBarLabel';
import { ReactInjector } from 'core/reactShims';

import { GroupExecutionPopover } from './GroupExecutionPopover';

import './groupStage.less';

export interface IGroupExecutionLabelProps {
  stage: IExecutionStageSummary;
  execution: IExecution;
  application: Application;
  executionMarker: boolean;
}

export interface IGroupedStageListItemProps {
  execution: IExecution;
  groupStage: IExecutionStageSummary;
  stage: IExecutionStageSummary;
}

@autoBindMethods
export class GroupExecutionLabel extends React.Component<IGroupExecutionLabelProps> {
  private subStageClicked(groupStage: IExecutionStageSummary, stage: IExecutionStageSummary): void {
    const { executionService } = ReactInjector;
    executionService.toggleDetails(this.props.execution, groupStage.index, stage.index);
  }

  public render() {
    const { stage } = this.props;

    if (!this.props.executionMarker) {
      return (<ExecutionBarLabel {...this.props}/>);
    }

    return (
      <GroupExecutionPopover stage={stage} subStageClicked={this.subStageClicked}>
        {this.props.children}
      </GroupExecutionPopover>
    );
  }
}
