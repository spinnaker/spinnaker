import { get } from 'lodash';
import React from 'react';

import type {
  Application,
  DeckRuntimeServices,
  IModalComponentProps,
  IRouterInjectedProps,
  IStage,
} from '@spinnaker/core';
import {
  DeckRuntimeContext,
  FirewallLabels,
  noop,
  ReactModal,
  TaskMonitor,
  withRouter,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { ServerGroupTemplateSelection } from './ServerGroupTemplateSelection';
import {
  ServerGroupAdvancedSettings,
  ServerGroupBasicSettings,
  ServerGroupCapacity,
  ServerGroupInstanceType,
  ServerGroupLoadBalancers,
  ServerGroupSecurityGroups,
  ServerGroupZones,
} from './pages';
import type { IAmazonServerGroupCommand } from '../serverGroupConfiguration.service';
import type { AwsServerGroupConfigurationService } from '../serverGroupConfiguration.service';

export interface IAmazonCloneServerGroupModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: IAmazonServerGroupCommand;
}

export interface IAmazonCloneServerGroupModalState {
  firewallsLabel: string;
  loaded: boolean;
  requiresTemplateSelection: boolean;
  taskMonitor: TaskMonitor;
}

export class AmazonCloneServerGroupModalComponent extends React.Component<
  IAmazonCloneServerGroupModalProps & IRouterInjectedProps,
  IAmazonCloneServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IAmazonCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;
  private refreshUnsubscribe: () => void;

  public static show(
    props: IAmazonCloneServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<IAmazonServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(AmazonCloneServerGroupModal, props, modalProps, runtimeServices);
  }

  constructor(props: IAmazonCloneServerGroupModalProps & IRouterInjectedProps) {
    super(props);

    const requiresTemplateSelection = get(props, 'command.viewState.requiresTemplateSelection', false);
    this.state = {
      firewallsLabel: FirewallLabels.get('Firewalls'),
      loaded: false,
      requiresTemplateSelection,
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentDidMount(): void {
    if (!this.state.requiresTemplateSelection) {
      this.configureCommand();
    }
  }

  private get configurationService(): AwsServerGroupConfigurationService {
    return this.context.services.providerServiceDelegate.getDelegate(
      this.props.command.selectedProvider,
      'serverGroup.configurationService',
    );
  }

  private templateSelected = () => {
    this.setState({ requiresTemplateSelection: false });
    this.configureCommand();
  };

  private onTaskComplete = () => {
    this.props.application.serverGroups.refresh();
    this.props.application.serverGroups.onNextRefresh(null, this.onApplicationRefresh);
  };

  protected onApplicationRefresh = (): void => {
    if (this._isUnmounted) {
      return;
    }

    const { command } = this.props;
    const { taskMonitor } = this.state;
    const cloneStage = taskMonitor.task.execution.stages.find((stage: IStage) => stage.type === 'cloneServerGroup');
    if (cloneStage && cloneStage.context['deploy.server.groups']) {
      const newServerGroupName = cloneStage.context['deploy.server.groups'][command.region];
      if (newServerGroupName) {
        const newStateParams = {
          serverGroup: newServerGroupName,
          accountId: command.credentials,
          region: command.region,
          provider: 'aws',
        };
        let transitionTo = '^.^.^.clusters.serverGroup';
        if (this.props.stateService.includes('**.clusters.serverGroup')) {
          // clone via details, all view
          transitionTo = '^.serverGroup';
        }
        if (this.props.stateService.includes('**.clusters.cluster.serverGroup')) {
          // clone or create with details open
          transitionTo = '^.^.serverGroup';
        }
        if (this.props.stateService.includes('**.clusters')) {
          // create new, no details open
          transitionTo = '.serverGroup';
        }
        this.props.stateService.go(transitionTo, newStateParams);
      }
    }
  };

  private initializeCommand = () => {
    const { command } = this.props;

    command.credentialsChanged(command);
    command.regionChanged(command);
    this.configurationService.configureSubnetPurposes(command);
  };

  private configureCommand = () => {
    const { application, command } = this.props;
    this.configurationService.configureCommand(application, command).then(() => {
      this.initializeCommand();
      this.setState({ loaded: true, requiresTemplateSelection: false });
    });
  };

  private normalizeCommand = ({ tags }: IAmazonServerGroupCommand) => {
    if (!tags) {
      return;
    }
    Object.keys(tags).forEach((key) => {
      if (!key.length && !tags[key].length) {
        delete tags[key];
      }
    });
  };

  public componentWillUnmount(): void {
    this._isUnmounted = true;
    if (this.refreshUnsubscribe) {
      this.refreshUnsubscribe();
    }
  }

  private submit = (command: IAmazonServerGroupCommand): void => {
    this.normalizeCommand(command);
    const forPipelineConfig = command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline';
    if (forPipelineConfig) {
      this.props.closeModal && this.props.closeModal(command);
    } else {
      this.state.taskMonitor.submit(() =>
        this.context.services.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    }
  };

  public render() {
    const { application, command, dismissModal, title } = this.props;
    const { loaded, taskMonitor, requiresTemplateSelection } = this.state;

    if (requiresTemplateSelection) {
      return (
        <ServerGroupTemplateSelection
          app={application}
          command={command}
          onDismiss={dismissModal}
          onTemplateSelected={this.templateSelected}
        />
      );
    }

    return (
      <WizardModal<IAmazonServerGroupCommand>
        heading={title}
        initialValues={command}
        loading={!loaded}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={command.viewState.submitButtonLabel}
        render={({ formik, nextIdx, wizard }) => (
          <>
            <WizardPage
              label="Basic Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupBasicSettings ref={innerRef} formik={formik} app={application} />}
            />

            <WizardPage
              label="Load Balancers"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupLoadBalancers ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label={FirewallLabels.get('Firewalls')}
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupSecurityGroups ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label={command.viewState.useSimpleInstanceTypeSelector ? 'Instance Type' : 'Instance Types'}
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupInstanceType ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Capacity"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupCapacity ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Availability Zones"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupZones ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Advanced Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <ServerGroupAdvancedSettings ref={innerRef} formik={formik} app={application} />
              )}
            />
          </>
        )}
      />
    );
  }
}

export const AmazonCloneServerGroupModal = Object.assign(
  withRouter<IAmazonCloneServerGroupModalProps & IRouterInjectedProps>(AmazonCloneServerGroupModalComponent),
  { show: AmazonCloneServerGroupModalComponent.show },
);
