import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import type { IDeckRuntimeServicesInjectedProps } from '../../../../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../../../../bootstrap/DeckRuntimeContext';
import type { IExecutionStageLabelProps } from '../../../../domain';
import { ExecutionWindowActions } from '../executionWindows/ExecutionWindowActions';
import type { IRouterInjectedProps } from '../../../../navigation/routerContext';
import { withRouter } from '../../../../navigation/routerContext';
import { HoverablePopover } from '../../../../presentation/HoverablePopover';
import { SkipConditionWait } from '../waitForCondition/SkipConditionWait';
import { Spinner } from '../../../../widgets/spinners/Spinner';

export interface IExecutionBarLabelProps extends IExecutionStageLabelProps {
  tooltip?: JSX.Element;
}

export interface IExecutionBarLabelState {
  // We could get this off the execution itself; however, hydration can occur by mousing over any stage in the pipeline,
  // and the execution prop itself does not get replaced on hydration (only the 'hydrated' field on the execution), so
  // there's not a clean way to notify stages that hydration has occurred
  hydrated: boolean;
}

export class ExecutionBarLabelComponent extends React.Component<
  IExecutionBarLabelProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps,
  IExecutionBarLabelState
> {
  private mounted = false;

  constructor(props: IExecutionBarLabelProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps) {
    super(props);
    this.state = {
      hydrated: props.execution && props.execution.hydrated,
    };
  }

  private hydrate = (): void => {
    const { application, execution } = this.props;
    if (!this.requiresHydration() || !execution) {
      return;
    }
    this.props.deckRuntimeServices.executionService.hydrate(application, execution).then(() => {
      if (this.mounted && !this.state.hydrated) {
        this.setState({ hydrated: true });
      }
    });
  };

  private requiresHydration = (): boolean => {
    const { stage } = this.props;
    const { suspendedStageTypes } = stage;
    const requireHydration = ['restrictExecutionDuringTimeWindow', 'waitForCondition'];
    return stage.labelComponent !== ExecutionBarLabel || requireHydration.some((s) => suspendedStageTypes.has(s));
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
    const label =
      LabelComponent === ExecutionBarLabel ? (
        <span>{this.getRenderableStageName()}</span>
      ) : (
        <LabelComponent application={application} execution={execution} stage={stage} />
      );
    const tooltip = <Tooltip id={stage.id}>{label}</Tooltip>;
    return (
      <OverlayTrigger placement="top" overlay={tooltip}>
        {this.props.children}
      </OverlayTrigger>
    );
  };

  private getExecutionWindowTemplate = () => {
    const { stage, application, execution } = this.props;
    const executionWindowStage = stage.stages.find((s) => s.type === 'restrictExecutionDuringTimeWindow');
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
    const waitForConditionStage = stage.stages.find((s) => s.type === 'waitForCondition');
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
    const { stage, stateParams } = this.props;
    let stageName = stage.name ? stage.name : stage.type;
    if (stage.type === 'group' && stage.groupStages && stage.index === Number(stateParams.stage)) {
      const subStageIndex = Number(stateParams.subStage);
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
    const { stage, executionMarker } = this.props;
    const { suspendedStageTypes } = stage;
    const { getExecutionWindowTemplate, getWaitForConditionTemplate, DefaultLabel } = this;
    if (executionMarker) {
      const requiresHydration = this.requiresHydration();
      let template: JSX.Element = null;
      if (requiresHydration && !this.state.hydrated) {
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

export const ExecutionBarLabel = withDeckRuntimeServices(withRouter(ExecutionBarLabelComponent));
ExecutionBarLabel.displayName = 'ExecutionBarLabel';
