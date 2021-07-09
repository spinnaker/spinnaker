import { get, isEqual } from 'lodash';
import React from 'react';

import { ServerGroupCapacity, ServerGroupLoadBalancers, ServerGroupSecurityGroups } from '@spinnaker/amazon';
import {
  AccountTag,
  Application,
  DeployInitializer,
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

import { ServerGroupBasicSettings, ServerGroupParameters, ServerGroupResources } from './pages';
import { JobDisruptionBudget } from './pages/disruptionBudget/JobDisruptionBudget';
import { TitusReactInjector } from '../../../reactShims';
import { getDefaultJobDisruptionBudgetForApp, ITitusServerGroupCommand } from '../serverGroupConfiguration.service';

export interface ITitusCloneServerGroupModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: ITitusServerGroupCommand;
}

export interface ITitusCloneServerGroupModalState {
  firewallsLabel: string;
  loaded: boolean;
  requiresTemplateSelection: boolean;
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
  };

  private configureCommand = () => {
    const { command } = this.props;
    TitusReactInjector.titusServerGroupConfigurationService.configureCommand(command).then(() => {
      TitusReactInjector.titusServerGroupConfigurationService.configureSubnets(command);
      if (!command.credentials.includes('${')) {
        // so as to not erase registry when account is a spel expression
        command.registry = ((command.backingData.credentialsKeyedByAccount[command.credentials] as any) || {}).registry;
      }
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
    // never persist the default budget
    let toSubmit = command;
    // TODO: see if this is needed
    if (command.disruptionBudget.timeWindows && !command.disruptionBudget.timeWindows.length) {
      delete command.disruptionBudget.timeWindows;
    }
    if (isEqual(getDefaultJobDisruptionBudgetForApp(this.props.application), command.disruptionBudget)) {
      toSubmit = { ...command, disruptionBudget: undefined };
    }
    if (forPipelineConfig) {
      this.props.closeModal && this.props.closeModal(toSubmit);
    } else {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(toSubmit, this.props.application),
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
                <AccountTag
                  account={
                    command.backingData.credentialsKeyedByAccount[command.credentials] &&
                    command.backingData.credentialsKeyedByAccount[command.credentials].awsAccount
                  }
                />
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
      command.backingData.credentialsKeyedByAccount[command.credentials] &&
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
    const { loaded, taskMonitor, requiresTemplateSelection } = this.state;

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
              render={({ innerRef }) => <ServerGroupCapacity ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Load Balancers"
              wizard={wizard}
              order={nextIdx()}
              note={this.getLoadBalancerNote(formik.values)}
              render={({ innerRef }) => (
                <ServerGroupLoadBalancers ref={innerRef} formik={formik as any} hideLoadBalancers={true} />
              )}
            />

            <WizardPage
              label={FirewallLabels.get('Firewalls')}
              wizard={wizard}
              order={nextIdx()}
              note={this.getSecurityGroupNote(formik.values)}
              render={({ innerRef }) => <ServerGroupSecurityGroups ref={innerRef} formik={formik as any} />}
            />

            <WizardPage
              label="Job Disruption Budget"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <JobDisruptionBudget ref={innerRef} formik={formik} app={application} />}
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
