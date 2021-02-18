import { Application } from 'core/application/application.model';
import { IExecution, IExecutionStageSummary } from 'core/domain';
import { HoverablePopover } from 'core/presentation/HoverablePopover';
import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { ManualJudgmentApproval } from './ManualJudgmentApproval';
import { ExecutionBarLabel } from '../common/ExecutionBarLabel';

export interface IManualJudgmentExecutionLabelProps {
  stage: IExecutionStageSummary;
  execution: IExecution;
  application: Application;
  executionMarker: boolean;
}

export class ManualJudgmentExecutionLabel extends React.Component<IManualJudgmentExecutionLabelProps> {
  public render() {
    if (!this.props.executionMarker) {
      return <ExecutionBarLabel {...this.props} />;
    }
    const stage = this.props.stage;
    if (stage.isRunning) {
      const template = (
        <div>
          <div>
            <b>{stage.name}</b>
          </div>
          <ManualJudgmentApproval
            stage={stage.masterStage}
            application={this.props.application}
            execution={this.props.execution}
          />
        </div>
      );
      return <HoverablePopover template={template}>{this.props.children}</HoverablePopover>;
    }
    const tooltip = <Tooltip id={stage.id}>{stage.name}</Tooltip>;
    return (
      <OverlayTrigger placement="top" overlay={tooltip}>
        <span>{this.props.children}</span>
      </OverlayTrigger>
    );
  }
}
