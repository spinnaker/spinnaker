import * as React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { IExecutionStageLabelProps } from 'core/domain';
import { ExecutionWindowActions } from 'core/pipeline/config/stages/executionWindows/ExecutionWindowActions';
import { SkipConditionWait } from 'core/pipeline/config/stages/waitForCondition/SkipConditionWait';
import { HoverablePopover } from 'core/presentation/HoverablePopover';
import { ReactInjector } from 'core/reactShims';
import { Spinner } from 'core/widgets';

export interface IExecutionBarLabelProps extends IExecutionStageLabelProps {
  tooltip?: JSX.Element;
}

export interface IExecutionBarLabelState {
  hydrated: boolean;
}

export class ExecutionBarLabel extends React.Component<IExecutionBarLabelProps, IExecutionBarLabelState> {
  private mounted = false;

  constructor(props: IExecutionBarLabelProps) {
    super(props);
    this.state = {
      hydrated: props.execution && props.execution.hydrated,
    };
  }

  private hydrate = (): void => {
    const { execution, application } = this.props;
    if (!execution) {
      return;
    }
    ReactInjector.executionService.hydrate(application, execution).then(() => {
      if (this.mounted) {
        this.setState({ hydrated: true });
      }
    });
  };

  public componentDidMount() {
    this.mounted = true;
  }

  public componentWillUnmount() {
    this.mounted = false;
  }

  public render() {
    const { stage, application, execution, executionMarker } = this.props;
    const { suspendedStageTypes } = stage;
    if (suspendedStageTypes.has('restrictExecutionDuringTimeWindow') && executionMarker) {
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
    } else if (suspendedStageTypes.has('waitForCondition') && executionMarker) {
      const waitForConditionStage = stage.stages.find(s => s.type === 'waitForCondition');
      const template = (
        <div>
          <p>
            <b>{stage.name}</b> (waiting until conditions are met)
          </p>
          Conditions:
          <SkipConditionWait application={application} execution={execution} stage={waitForConditionStage} />
        </div>
      );
      return <HoverablePopover template={template}>{this.props.children}</HoverablePopover>;
    }
    if (executionMarker) {
      const LabelComponent = stage.labelComponent;
      if (LabelComponent !== ExecutionBarLabel && !this.state.hydrated) {
        const loadingTooltip = (
          <Tooltip id={stage.id}>
            <Spinner size="small" />
          </Tooltip>
        );
        return (
          <span onMouseEnter={this.hydrate}>
            <OverlayTrigger placement="top" overlay={loadingTooltip}>
              {this.props.children}
            </OverlayTrigger>
          </span>
        );
      }
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
