import * as React from 'react';
import { get } from 'lodash';

import {
  Application,
  DeployInitializer,
  FirewallLabels,
  IModalComponentProps,
  IStage,
  ReactInjector,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
  noop,
  AccountTag,
} from '@spinnaker/core';

import { ServerGroupCapacity, ServerGroupLoadBalancers, ServerGroupSecurityGroups } from '@spinnaker/amazon';

import { ITitusServerGroupCommand } from '../serverGroupConfiguration.service';
import { TitusReactInjector } from '../../../reactShims';

import { ServerGroupBasicSettings, ServerGroupResources, ServerGroupParameters } from './pages';

export interface ITitusCloneServerGroupModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: ITitusServerGroupCommand;
}

export interface ITitusCloneServerGroupModalState {
  firewallsLabel: string;
  loadBalancerNote: React.ReactElement<any>;
  loaded: boolean;
  requiresTemplateSelection: boolean;
  securityGroupNote: React.ReactElement<any>;
  taskMonitor: TaskMonitor;
}

export class TitusCloneServerGroupModal extends React.Component<
  ITitusCloneServerGroupModalProps,
  ITitusCloneServerGroupModalState
> {
  public static defaultProps: Partial<ITitusCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;
  private refreshUnsubscribe: () => void;

  public static show(props: ITitusCloneServerGroupModalProps): Promise<ITitusServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(TitusCloneServerGroupModal, props, modalProps);
  }

  constructor(props: ITitusCloneServerGroupModalProps) {
    super(props);

    const requiresTemplateSelection = get(props, 'command.viewState.requiresTemplateSelection', false);
    if (!requiresTemplateSelection) {
      this.configureCommand();
    }

    this.state = {
      firewallsLabel: FirewallLabels.get('Firewalls'),
      loadBalancerNote: this.getLoadBalancerNote(props.command),
      securityGroupNote: this.getSecurityGroupNote(props.command),
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
          provider: 'titus',
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

  private configureCommand = () => {
    const { command } = this.props;
    TitusReactInjector.titusServerGroupConfigurationService.configureCommand(command).then(() => {
      command.registry = (command.backingData.credentialsKeyedByAccount[command.credentials] as any).registry;
      this.setState({ loaded: true, requiresTemplateSelection: false });
    });
  };

  public componentWillUnmount(): void {
    this._isUnmounted = true;
    if (this.refreshUnsubscribe) {
      this.refreshUnsubscribe();
    }
  }

  private submit = (command: ITitusServerGroupCommand): void => {
    const forPipelineConfig = command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline';
    if (forPipelineConfig) {
      this.props.closeModal && this.props.closeModal(command);
    } else {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    }
  };

  private getLoadBalancerNote = (command: ITitusServerGroupCommand) => {
    return (
      <div className="form-group small" style={{ marginTop: '20px' }}>
        <div className="col-md-8 col-md-offset-3">
          <p>
            Only target groups with target type <em>ip</em> are supported in Titus. It is not possible to re-use target
            groups of target type <em>instance</em> used by Amazon instances.{' '}
          </p>
          {command.backingData !== undefined &&
            command.backingData.credentialsKeyedByAccount !== undefined &&
            command.credentials !== undefined && (
              <p>
                Uses target groups from the Amazon account{' '}
                <AccountTag account={command.backingData.credentialsKeyedByAccount[command.credentials].awsAccount} />
              </p>
            )}
        </div>
      </div>
    );
  };

  private getSecurityGroupNote = (command: ITitusServerGroupCommand) => {
    const amazonAccount =
      command.backingData &&
      command.backingData.credentialsKeyedByAccount &&
      command.backingData.credentialsKeyedByAccount[command.credentials].awsAccount;
    if (!amazonAccount || command.credentials === undefined) {
      return null;
    }

    return (
      <div className="form-group small">
        <div className="col-md-9 col-md-offset-3">
          Uses {FirewallLabels.get('firewalls')} from the Amazon account <AccountTag account={amazonAccount} />
        </div>
      </div>
    );
  };

  public render() {
    const { application, command, dismissModal, title } = this.props;
    const { loadBalancerNote, loaded, securityGroupNote, taskMonitor, requiresTemplateSelection } = this.state;

    if (requiresTemplateSelection) {
      return (
        <DeployInitializer
          application={application}
          cloudProvider="titus"
          command={command}
          onDismiss={dismissModal}
          onTemplateSelected={this.templateSelected}
        />
      );
    }

    return (
      <WizardModal<ITitusServerGroupCommand>
        heading={title}
        initialValues={command}
        loading={!loaded}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={command.viewState.submitButtonLabel}
        validate={values => {
          this.setState({
            loadBalancerNote: this.getLoadBalancerNote(values),
            securityGroupNote: this.getSecurityGroupNote(values),
          });
        }}
        render={({ formik, nextIdx, wizard }) => (
          <>
            <WizardPage
              label="Basic Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupBasicSettings ref={innerRef} formik={formik} app={application} />}
            />

            <WizardPage
              label="Resources"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupResources ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Capacity"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <ServerGroupCapacity ref={innerRef} formik={formik} hideTargetHealthyDeployPercentage={true} />
              )}
            />

            <WizardPage
              label="Load Balancers"
              wizard={wizard}
              order={nextIdx()}
              note={loadBalancerNote}
              render={({ innerRef }) => (
                <ServerGroupLoadBalancers ref={innerRef} formik={formik as any} hideLoadBalancers={true} />
              )}
            />

            <WizardPage
              label={FirewallLabels.get('Firewalls')}
              wizard={wizard}
              order={nextIdx()}
              note={securityGroupNote}
              render={({ innerRef }) => <ServerGroupSecurityGroups ref={innerRef} formik={formik as any} />}
            />

            <WizardPage
              label="Advanced Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupParameters ref={innerRef} formik={formik} app={application} />}
            />
          </>
        )}
      />
    );
  }
}
