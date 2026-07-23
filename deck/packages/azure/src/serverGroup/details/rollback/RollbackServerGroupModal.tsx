import React from 'react';
import { Modal } from 'react-bootstrap';
import Select from 'react-select';

import type { DeckRuntimeServices, IModalComponentProps } from '@spinnaker/core';
import {
  DeckRuntimeContext,
  ModalClose,
  noop,
  ReactModal,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
} from '@spinnaker/core';

import { AzureModalFooter } from '../../../common/AzureModalFooter';

export interface IAzureRollbackServerGroupModalProps extends IModalComponentProps {
  application: any;
  serverGroup: any;
  disabledServerGroups: any[];
}

export interface IAzureRollbackServerGroupModalState {
  taskMonitor: TaskMonitor;
  submitting: boolean;
  command: any;
}

export class AzureRollbackServerGroupModal extends React.Component<
  IAzureRollbackServerGroupModalProps,
  IAzureRollbackServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IAzureRollbackServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IAzureRollbackServerGroupModalProps, runtimeServices: DeckRuntimeServices) {
    const modalProps = {};
    return ReactModal.show(AzureRollbackServerGroupModal, props, modalProps, runtimeServices);
  }

  constructor(props: IAzureRollbackServerGroupModalProps) {
    super(props);

    const { application, serverGroup } = props;

    this.state = {
      taskMonitor: new TaskMonitor({
        application,
        title: 'Rolling back your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      }),
      submitting: true,
      command: {
        interestingHealthProviderNames: [],
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name,
          enableAndDisableOnly: true,
        },
      },
    };
  }

  private close = (args?: any) => {
    this.props.dismissModal.apply(null, args);
  };

  private submit = () => {
    const { command, taskMonitor } = this.state;
    const { serverGroup, application } = this.props;

    taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.rollbackServerGroup(serverGroup, application, command),
    );
  };

  private filterServerGroups = (disabledServerGroups: any[]) => {
    return disabledServerGroups
      .filter((disabledServerGroup: any) => disabledServerGroup.instanceCounts.total !== 0)
      .sort((a: any, b: any) => b.name.localeCompare(a.name));
  };

  private isValid = () => {
    return this.state.command.rollbackContext.restoreServerGroupName !== undefined;
  };

  private handleServerGroupChange = (restoreServerGroupOption: any) => {
    const { disabledServerGroups } = this.props;
    const command = { ...this.state.command };
    command.rollbackContext.restoreServerGroupName = restoreServerGroupOption.value;
    const restoreServerGroup = this.filterServerGroups(disabledServerGroups).find((disabledServerGroup: any) => {
      return disabledServerGroup.name === restoreServerGroupOption.value;
    });
    command.targetSize = restoreServerGroup.capacity.max;
    this.setState({ command });
  };

  private handleTaskReasonChange = (taskReason?: any) => {
    const command = { ...this.state.command, reason: taskReason };
    this.setState({ command });
  };

  public render() {
    const { command, taskMonitor, submitting } = this.state;
    const { serverGroup, disabledServerGroups } = this.props;
    const disabledServerGroupOptions = this.filterServerGroups(disabledServerGroups).map(
      (disabledServerGroup: any) => ({
        label: disabledServerGroup.name,
        value: disabledServerGroup.name,
      }),
    );

    return (
      <Modal onHide={this.close}>
        <TaskMonitorWrapper monitor={taskMonitor} />
        {submitting && (
          <form role="form">
            <ModalClose dismiss={this.close} />
            <Modal.Header>
              <Modal.Title>Rollback {serverGroup.name}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <div className="row">
                <div className="col-sm-3 sm-label-right">Restore to</div>
                <div className="col-sm-6">
                  <Select
                    value={command.rollbackContext.restoreServerGroupName}
                    onChange={this.handleServerGroupChange}
                    options={disabledServerGroupOptions}
                  />
                </div>
              </div>
              <TaskReason reason={command.taskReason} onChange={this.handleTaskReasonChange} />
            </Modal.Body>
            <AzureModalFooter
              onSubmit={this.submit}
              onCancel={this.close}
              isValid={this.isValid()}
              account={serverGroup.account}
            />
          </form>
        )}
      </Modal>
    );
  }
}
