import * as React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { IExecution, IExecutionStageSummary } from '@spinnaker/core';
import { Application } from '@spinnaker/core';
import { ExecutionBarLabel, HoverablePopover } from '@spinnaker/core';

import { EvaluateCloudFormationChangeSetExecutionApproval } from './evaluateCloudFormationChangeSetExecutionApproval';

export interface IEvaluateCloudFormationChangeSetExecutionProps {
  stage: IExecutionStageSummary;
  execution: IExecution;
  application: Application;
  executionMarker: boolean;
}

export class EvaluateCloudFormationChangeSetExecutionLabel extends React.Component<
  IEvaluateCloudFormationChangeSetExecutionProps
> {
  public render() {
    if (!this.props.executionMarker) {
      return <ExecutionBarLabel {...this.props} />;
    }

    const { stage } = this.props;
    if (
      stage.isRunning &&
      stage.stages[0].context.changeSetContainsReplacement &&
      stage.stages[0].context.actionOnReplacement === 'ask'
    ) {
      const template = (
        <div>
          <div>
            <b>{stage.name}</b>
          </div>

          <EvaluateCloudFormationChangeSetExecutionApproval
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
