import * as React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { IExecutionStageSummary, IExecution } from 'core/domain';
import { ExecutionWindowActions } from 'core/pipeline/config/stages/executionWindows/ExecutionWindowActions';
import { Application } from 'core/application/application.model';
import { HoverablePopover } from 'core/presentation/HoverablePopover';

export interface IExecutionBarLabelProps {
  application: Application;
  execution: IExecution;
  stage: IExecutionStageSummary;
  tooltip?: JSX.Element;
  executionMarker: boolean;
}

export class ExecutionBarLabel extends React.Component<IExecutionBarLabelProps, any> {
  public render() {
    const { stage, application, execution, executionMarker } = this.props;
    const inSuspendedExecutionWindow = stage.inSuspendedExecutionWindow;
    if (inSuspendedExecutionWindow && executionMarker) {
      const executionWindowStage = stage.stages.find(s => s.type === 'restrictExecutionDuringTimeWindow');
      const template = (
        <div>
          <div><b>{stage.name}</b> (waiting for execution window)</div>
          <ExecutionWindowActions application={application} execution={execution} stage={executionWindowStage}/>
        </div>
      );
      return (
        <HoverablePopover template={template}>
          {this.props.children}
        </HoverablePopover>
      );
    }
    if (executionMarker) {
      const LabelComponent = stage.labelComponent;
      const tooltip = (
        <Tooltip id={stage.id}>
          <LabelComponent application={application} execution={execution} stage={stage}/>
        </Tooltip>
      );
      return (
        <OverlayTrigger placement="top" overlay={tooltip}>
          {this.props.children}
        </OverlayTrigger>
      );
    }
    return (
      <span>{ stage.name ? stage.name : stage.type }</span>
    )
  }
}
