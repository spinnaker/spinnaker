import React from 'react';

import { GroupExecutionPopover } from './GroupExecutionPopover';
import type { Application } from '../../../../application/application.model';
import { DeckRuntimeContext } from '../../../../bootstrap/DeckRuntimeContext';
import { ExecutionBarLabel } from '../common/ExecutionBarLabel';
import type { IExecution, IExecutionStageSummary } from '../../../../domain';

import './groupStage.less';

export interface IGroupExecutionLabelProps {
  stage: IExecutionStageSummary;
  execution: IExecution;
  application: Application;
  executionMarker: boolean;
  width?: number;
}

export class GroupExecutionLabel extends React.Component<IGroupExecutionLabelProps> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private subStageClicked = (groupStage: IExecutionStageSummary, stage: IExecutionStageSummary): void => {
    const { executionService } = this.context.services;
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
