import React from 'react';
import { Modal } from 'react-bootstrap';
import Select from 'react-select';

import {
  IModalComponentProps,
  ModalClose,
  noop,
  ReactInjector,
  ReactModal,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
} from '@spinnaker/core';

import { AzureModalFooter } from '../../../common/AzureModalFooter';

export interface IAzureRollbackServerGroupModalProps extends IModalComponentProps {
  application: any;
  serverGroup: any;
  disabledServerGroups: any;
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
  public static defaultProps: Partial<IAzureRollbackServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IAzureRollbackServerGroupModalProps) {
    const modalProps = {};
    return ReactModal.show(AzureRollbackServerGroupModal, props, modalProps);
  }

  constructor(props: IAzureRollbackServerGroupModalProps) {
    super(props);

    const { application, serverGroup } = props;

    this.state = {
      taskMonitor: new TaskMonitor({
        application: application,
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

    taskMonitor.submit(() => {
      return ReactInjector.serverGroupWriter.rollbackServerGroup(serverGroup, application, command);
    });
  };

  private filterServerGroups = (disabledServerGroups: any) => {
    const filteredDisabledServerGroups = disabledServerGroups
      .filter((disabledServerGroup: any) => disabledServerGroup.instanceCounts.total !== 0)
      .sort((a: any, b: any) => b.name.localeCompare(a.name));

    return filteredDisabledServerGroups;
  };

  private isValid = () => {
    const restoreServerGroupName = this.state.command.rollbackContext.restoreServerGroupName;
    return restoreServerGroupName !== undefined;
  };

  private handleServerGroupChange = (restoreServerGroupOption: any) => {
    const { disabledServerGroups } = this.props;
    const newCommand = { ...this.state.command };
    newCommand.rollbackContext.restoreServerGroupName = restoreServerGroupOption.value;
    const restoreServerGroup = this.filterServerGroups(disabledServerGroups).find(function (disabledServerGroup: any) {
      return disabledServerGroup.name === restoreServerGroupOption.value;
    });
    newCommand.targetSize = restoreServerGroup.capacity.max;
    this.setState({
      command: newCommand,
    });
  };

  private handleTaskReasonChange = (taskReason?: any) => {
    const newCommand = { ...this.state.command };
    newCommand.reason = taskReason;
    this.setState({
      command: newCommand,
    });
  };

  public render() {
    const { command, taskMonitor, submitting } = this.state;
    const { serverGroup, disabledServerGroups } = this.props;
    const isValidSG = this.isValid();
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
              isValid={isValidSG}
              account={serverGroup.account}
            />
          </form>
        )}
      </Modal>
    );
  }
}
