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
    const { stage, application, execution } = this.props;
    const { suspendedStageTypes } = stage;
    const suspendedTypesNeedingHydration = ['restrictExecutionDuringTimeWindow', 'waitForCondition'];
    const requiresHydration =
      stage.labelComponent !== ExecutionBarLabel ||
      suspendedTypesNeedingHydration.some(s => suspendedStageTypes.has(s));
    if (!requiresHydration || !execution || execution.hydrated) {
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

  private DefaultLabel = () => {
    const { stage, application, execution } = this.props;
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
  };

  private getExecutionWindowTemplate = () => {
    const { stage, application, execution } = this.props;
    const executionWindowStage = stage.stages.find(s => s.type === 'restrictExecutionDuringTimeWindow');
    return (
      <div>
        <div>
          <b>{stage.name}</b> (waiting for execution window)
        </div>
        <ExecutionWindowActions application={application} execution={execution} stage={executionWindowStage} />
      </div>
    );
  };

  private getWaitForConditionTemplate = () => {
    const { stage, application, execution } = this.props;
    const waitForConditionStage = stage.stages.find(s => s.type === 'waitForCondition');
    return (
      <div>
        <p>
          <b>{stage.name}</b> (waiting until conditions are met)
        </p>
        Conditions:
        <SkipConditionWait application={application} execution={execution} stage={waitForConditionStage} />
      </div>
    );
  };

  private getRenderableStageName(): string {
    const { stage } = this.props;
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
    return stageName;
  }

  public render() {
    const { stage, execution, executionMarker } = this.props;
    const { suspendedStageTypes } = stage;
    const { getExecutionWindowTemplate, getWaitForConditionTemplate, DefaultLabel } = this;
    if (executionMarker) {
      const suspendedTypesNeedingHydration = ['restrictExecutionDuringTimeWindow', 'waitForCondition'];
      const requiresHydration =
        stage.labelComponent !== ExecutionBarLabel ||
        suspendedTypesNeedingHydration.some(s => suspendedStageTypes.has(s));
      let template: JSX.Element = null;
      if (requiresHydration && !execution.hydrated) {
        template = <Spinner size="small" />;
      } else if (suspendedStageTypes.has('restrictExecutionDuringTimeWindow')) {
        template = getExecutionWindowTemplate();
      } else if (suspendedStageTypes.has('waitForCondition')) {
        template = getWaitForConditionTemplate();
      }
      if (template) {
        return (
          <span onMouseEnter={this.hydrate}>
            <HoverablePopover template={template}>{this.props.children}</HoverablePopover>
          </span>
        );
      } else {
        return <DefaultLabel />;
      }
    }

    return <span>{this.getRenderableStageName()}</span>;
  }
}
