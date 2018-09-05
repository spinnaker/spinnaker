import * as React from 'react';
import { get } from 'lodash';
import { FormikErrors, FormikValues } from 'formik';

import {
  Application,
  IStage,
  ReactInjector,
  TaskMonitor,
  WizardModal,
  FirewallLabels,
  IModalComponentProps,
  noop,
  ReactModal,
} from '@spinnaker/core';

import { AwsReactInjector } from 'amazon/reactShims';

import { IAmazonServerGroupCommand } from '../serverGroupConfiguration.service';

import {
  ServerGroupBasicSettings,
  ServerGroupCapacity,
  ServerGroupInstanceType,
  ServerGroupZones,
  ServerGroupLoadBalancers,
  ServerGroupSecurityGroups,
  ServerGroupAdvancedSettings,
} from './pages';
import { ServerGroupTemplateSelection } from './ServerGroupTemplateSelection';

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

type CloneServerGroupModal = new () => WizardModal<IAmazonServerGroupCommand>;
const CloneServerGroupModal = WizardModal as CloneServerGroupModal;

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

  private onTaskComplete() {
    this.props.application.serverGroups.refresh();
    this.props.application.serverGroups.onNextRefresh(null, this.onApplicationRefresh);
  }

  protected onApplicationRefresh(): void {
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
  }

  private initializeCommand = () => {
    const { command } = this.props;
    if (command.viewState.imageId) {
      const foundImage = command.backingData.packageImages.filter(image => {
        return image.amis[command.region] && image.amis[command.region].includes(command.viewState.imageId);
      });
      if (foundImage.length) {
        command.amiName = foundImage[0].imageName;
      }
    }

    command.credentialsChanged(command);
    command.regionChanged(command);
    AwsReactInjector.awsServerGroupConfigurationService.configureSubnetPurposes(command);
  };

  private configureCommand = () => {
    const { application, command } = this.props;
    AwsReactInjector.awsServerGroupConfigurationService.configureCommand(application, command).then(() => {
      if (['clone', 'create'].includes(command.viewState.mode)) {
        if (!command.backingData.packageImages.length) {
          command.viewState.useAllImageSelection = true;
        }
      }

      this.initializeCommand();
      this.setState({ loaded: true, requiresTemplateSelection: false });
    });
  };

  public componentWillUnmount(): void {
    this._isUnmounted = true;
    if (this.refreshUnsubscribe) {
      this.refreshUnsubscribe();
    }
  }

  private submit = (command: IAmazonServerGroupCommand): void => {
    const forPipelineConfig = command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline';
    if (forPipelineConfig) {
      this.props.closeModal && this.props.closeModal(command);
    } else {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    }
  };

  private validate = (_values: FormikValues): FormikErrors<IAmazonServerGroupCommand> => {
    const errors = {} as FormikErrors<IAmazonServerGroupCommand>;
    return errors;
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
      <CloneServerGroupModal
        heading={title}
        initialValues={command}
        loading={!loaded}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={command.viewState.submitButtonLabel}
        validate={this.validate}
      >
        {/* <ServerGroupTemplateSelection /> */}
        <ServerGroupBasicSettings app={application} done={true} />
        <ServerGroupLoadBalancers done={true} />
        <ServerGroupSecurityGroups done={true} />
        <ServerGroupInstanceType done={true} />
        <ServerGroupCapacity done={true} />
        <ServerGroupZones done={true} />
        <ServerGroupAdvancedSettings app={application} done={true} />
      </CloneServerGroupModal>
    );
  }
}
