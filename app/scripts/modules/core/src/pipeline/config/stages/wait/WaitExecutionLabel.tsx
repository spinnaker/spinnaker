import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { IExecutionStageLabelProps } from 'core/domain';
import { HoverablePopover } from 'core/presentation/HoverablePopover';
import { ExecutionBarLabel } from '../common/ExecutionBarLabel';

import { SkipWait } from './SkipWait';

export interface IWaitExecutionLabelState {
  target?: any;
}

export class WaitExecutionLabel extends React.Component<IExecutionStageLabelProps, IWaitExecutionLabelState> {
  constructor(props: IExecutionStageLabelProps) {
    super(props);
    this.state = {};
  }

  public render() {
    const { stage, executionMarker, application, execution, children } = this.props;

    if (!executionMarker) {
      return <ExecutionBarLabel {...this.props} />;
    }
    if (stage.isRunning) {
      const template = (
        <div>
          <div>
            <b>{stage.name}</b>
          </div>
          <SkipWait stage={stage.masterStage} application={application} execution={execution} />
        </div>
      );
      return <HoverablePopover template={template}>{children}</HoverablePopover>;
    }
    const tooltip = <Tooltip id={stage.id}>{stage.name}</Tooltip>;
    return (
      <OverlayTrigger placement="top" overlay={tooltip}>
        <span>{children}</span>
      </OverlayTrigger>
    );
  }
}
