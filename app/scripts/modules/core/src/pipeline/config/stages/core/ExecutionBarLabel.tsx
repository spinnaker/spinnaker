import * as React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { IExecutionStageLabelComponentProps } from 'core/domain';
import { ExecutionWindowActions } from 'core/pipeline/config/stages/executionWindows/ExecutionWindowActions';
import { HoverablePopover } from 'core/presentation/HoverablePopover';
import { ReactInjector } from 'core/reactShims';

export interface IExecutionBarLabelProps extends IExecutionStageLabelComponentProps {
  tooltip?: JSX.Element;
}

export class ExecutionBarLabel extends React.Component<IExecutionBarLabelProps> {
  public render() {
    const { stage, application, execution, executionMarker } = this.props;
    const inSuspendedExecutionWindow = stage.inSuspendedExecutionWindow;
    if (inSuspendedExecutionWindow && executionMarker) {
      const executionWindowStage = stage.stages.find(s => s.type === 'restrictExecutionDuringTimeWindow');
      const template = (
        <div>
          <div>
            <b>{stage.name}</b> (waiting for execution window)
          </div>
          <ExecutionWindowActions application={application} execution={execution} stage={executionWindowStage} />
        </div>
      );
      return <HoverablePopover template={template}>{this.props.children}</HoverablePopover>;
    }
    if (executionMarker) {
      const LabelComponent = stage.labelComponent;
      const tooltip = (
        <Tooltip id={stage.id}>
          <LabelComponent application={application} execution={execution} stage={stage} />
        </Tooltip>
      );
      return (
        <OverlayTrigger placement="top" overlay={tooltip}>
          {this.props.children}
        </OverlayTrigger>
      );
    }

    let stageName = stage.name ? stage.name : stage.type;
    const params = ReactInjector.$uiRouter.globals.params;
    if (stage.type === 'group' && stage.groupStages && stage.index === Number(params.stage)) {
      const subStageIndex = Number(params.subStage);
      if (!Number.isNaN(subStageIndex)) {
        const activeStage = stage.groupStages[subStageIndex];
        if (activeStage) {
          stageName += `: ${activeStage.name}`;
        }
      }
    }
    return <span>{stageName}</span>;
  }
}
