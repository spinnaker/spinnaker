import { get } from 'lodash';
import React from 'react';

import {
  Application,
  FirewallLabels,
  IModalComponentProps,
  IStage,
  noop,
  ReactInjector,
  ReactModal,
  TaskMonitor,
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
import { AwsReactInjector } from '../../../reactShims';
import { IAmazonServerGroupCommand } from '../serverGroupConfiguration.service';

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

export class AmazonCloneServerGroupModal extends React.Component<
  IAmazonCloneServerGroupModalProps,
  IAmazonCloneServerGroupModalState
> {
  public static defaultProps: Partial<IAmazonCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;
  private refreshUnsubscribe: () => void;

  public static show(props: IAmazonCloneServerGroupModalProps): Promise<IAmazonServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(AmazonCloneServerGroupModal, props, modalProps);
  }

  constructor(props: IAmazonCloneServerGroupModalProps) {
    super(props);

    const requiresTemplateSelection = get(props, 'command.viewState.requiresTemplateSelection', false);
    if (!requiresTemplateSelection) {
      this.configureCommand();
    }

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
        if (ReactInjector.$state.includes('**.clusters.serverGroup')) {
          // clone via details, all view
          transitionTo = '^.serverGroup';
        }
        if (ReactInjector.$state.includes('**.clusters.cluster.serverGroup')) {
          // clone or create with details open
          transitionTo = '^.^.serverGroup';
        }
        if (ReactInjector.$state.includes('**.clusters')) {
          // create new, no details open
          transitionTo = '.serverGroup';
        }
        ReactInjector.$state.go(transitionTo, newStateParams);
      }
    }
  };

  private initializeCommand = () => {
    const { command } = this.props;

    command.credentialsChanged(command);
    command.regionChanged(command);
    AwsReactInjector.awsServerGroupConfigurationService.configureSubnetPurposes(command);
  };

  private configureCommand = () => {
    const { application, command } = this.props;
    AwsReactInjector.awsServerGroupConfigurationService.configureCommand(application, command).then(() => {
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
        ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application),
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
              label="Instance Type"
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
