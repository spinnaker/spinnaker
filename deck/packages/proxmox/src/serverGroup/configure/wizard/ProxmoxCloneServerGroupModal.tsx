import React from 'react';

import type { Application, IModalComponentProps, IStage } from '@spinnaker/core';
import { noop, ReactInjector, ReactModal, TaskMonitor, WizardModal, WizardPage } from '@spinnaker/core';

import { ProxmoxServerGroupBasicSettings } from './ProxmoxServerGroupBasicSettings';
import { ProxmoxServerGroupVMSettings } from './ProxmoxServerGroupVMSettings';
import type { IProxmoxServerGroupCommand } from '../proxmoxServerGroupCommandBuilder';
import { ProxmoxServerGroupCommandBuilder } from '../proxmoxServerGroupCommandBuilder';

export interface IProxmoxCloneServerGroupModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: IProxmoxServerGroupCommand;
  isNew?: boolean;
}

export interface IProxmoxCloneServerGroupModalState {
  command: IProxmoxServerGroupCommand;
  loaded: boolean;
  taskMonitor: TaskMonitor;
}

export class ProxmoxCloneServerGroupModal extends React.Component<
  IProxmoxCloneServerGroupModalProps,
  IProxmoxCloneServerGroupModalState
> {
  public static defaultProps: Partial<IProxmoxCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;

  public static show(props: IProxmoxCloneServerGroupModalProps): Promise<IProxmoxServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(ProxmoxCloneServerGroupModal, props, modalProps);
  }

  constructor(props: IProxmoxCloneServerGroupModalProps) {
    super(props);

    if (!props.command) {
      new ProxmoxServerGroupCommandBuilder().buildNewServerGroupCommand(props.application).then((command) => {
        Object.assign(this.state.command, command);
        this.setState({ loaded: true });
      });
    }

    this.state = {
      loaded: !!props.command,
      command: props.command ?? ({ viewState: { mode: 'create', submitButtonLabel: 'Create' } } as any),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentWillUnmount(): void {
    this._isUnmounted = true;
  }

  private onTaskComplete = () => {
    this.props.application.serverGroups.refresh();
    this.props.application.serverGroups.onNextRefresh(null, this.onApplicationRefresh);
  };

  private onApplicationRefresh = (): void => {
    if (this._isUnmounted) {
      return;
    }

    const { command, taskMonitor } = this.state;
    const createStage = taskMonitor.task.execution?.stages?.find((stage: IStage) => stage.type === 'createServerGroup');
    if (createStage && createStage.context['deploy.server.groups']) {
      const newServerGroupName = createStage.context['deploy.server.groups'][command.region];
      if (newServerGroupName) {
        const newStateParams = {
          serverGroup: newServerGroupName,
          accountId: command.credentials,
          region: command.region,
          provider: 'proxmox',
        };
        let transitionTo = '^.^.^.clusters.serverGroup';
        if (ReactInjector.$state.includes('**.clusters.serverGroup')) {
          transitionTo = '^.serverGroup';
        }
        if (ReactInjector.$state.includes('**.clusters.cluster.serverGroup')) {
          transitionTo = '^.^.serverGroup';
        }
        if (ReactInjector.$state.includes('**.clusters')) {
          transitionTo = '.serverGroup';
        }
        ReactInjector.$state.go(transitionTo, newStateParams);
      }
    }
  };

  private submit = (command: IProxmoxServerGroupCommand): void => {
    const forPipelineConfig = command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline';
    if (forPipelineConfig) {
      const { backingData, ...cleanCommand } = command;
      this.props.closeModal?.(cleanCommand);
    } else {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(command as any, this.props.application),
      );
    }
  };

  public render() {
    const { dismissModal, application } = this.props;
    const { loaded, taskMonitor, command } = this.state;

    return (
      <WizardModal<IProxmoxServerGroupCommand>
        heading={command.viewState.submitButtonLabel === 'Create' ? 'Create New Server Group' : 'Configure Cluster'}
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
              render={({ innerRef }) => (
                <ProxmoxServerGroupBasicSettings ref={innerRef} formik={formik} app={application} />
              )}
            />

            <WizardPage
              label="VM Configuration"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ProxmoxServerGroupVMSettings ref={innerRef} formik={formik} />}
            />
          </>
        )}
      />
    );
  }
}
