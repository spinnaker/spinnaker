import React from 'react';
import { Modal } from 'react-bootstrap';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { ModalClose, noop, ReactModal, TaskMonitor, TaskMonitorWrapper } from '@spinnaker/core';

import type { IAppengineServerGroupCommand } from '../serverGroupCommandBuilder.service';
import { AppengineSourceType } from '../serverGroupCommandBuilder.service';
import { AppengineServerGroupWriter } from '../../writer/serverGroup.write.service';

export interface IAppengineCloneServerGroupModalProps extends Partial<IModalComponentProps> {
  application: Application;
  command: IAppengineServerGroupCommand;
  title: string;
}

interface IAppengineCloneServerGroupModalState {
  taskMonitor: TaskMonitor;
}

const writer = new AppengineServerGroupWriter();

export function normalizeAppengineServerGroupCommandForSubmit(
  command: IAppengineServerGroupCommand,
): IAppengineServerGroupCommand {
  return {
    ...command,
    configArtifacts: command.configArtifacts || [],
    configFilepaths: command.configFilepaths || [],
    configFiles: command.configFiles || [],
    interestingHealthProviderNames: command.interestingHealthProviderNames || [],
    fromArtifact: command.fromArtifact || false,
    sourceType: command.sourceType || AppengineSourceType.GIT,
  };
}

export class AppengineCloneServerGroupModal extends React.Component<
  IAppengineCloneServerGroupModalProps,
  IAppengineCloneServerGroupModalState
> {
  public static defaultProps: Partial<IAppengineCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IAppengineCloneServerGroupModalProps): Promise<IAppengineServerGroupCommand> {
    return ReactModal.show(AppengineCloneServerGroupModal, props, { dialogClassName: 'modal-lg' });
  }

  public state: IAppengineCloneServerGroupModalState = {
    taskMonitor: new TaskMonitor({
      application: this.props.application,
      title: 'Creating your server group',
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      onTaskComplete: () => this.props.application.serverGroups.refresh(),
    }),
  };

  private update = (field: keyof IAppengineServerGroupCommand, value: any) => {
    (this.props.command as any)[field] = value;
    this.forceUpdate();
  };

  private submit = () => {
    const { command } = this.props;
    if (command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline') {
      this.props.closeModal(command);
      return;
    }

    this.state.taskMonitor.submit(() =>
      writer.cloneServerGroup(normalizeAppengineServerGroupCommandForSubmit(command), this.props.application),
    );
  };

  public render() {
    const { command, dismissModal, title } = this.props;

    return (
      <div>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="form-horizontal">
            <label>Account</label>
            <input
              className="form-control input-sm"
              onChange={(event) => this.update('credentials', event.target.value)}
              value={command.credentials || ''}
            />
            <label>Region</label>
            <input
              className="form-control input-sm"
              onChange={(event) => this.update('region', event.target.value)}
              value={command.region || ''}
            />
            <label>Branch</label>
            <input
              className="form-control input-sm"
              onChange={(event) => this.update('branch', event.target.value)}
              value={command.branch || ''}
            />
          </div>
        </Modal.Body>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <Modal.Footer>
          <button className="btn btn-default" onClick={() => dismissModal()} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" onClick={this.submit} type="button">
            {command.viewState?.submitButtonLabel || 'Clone'}
          </button>
        </Modal.Footer>
      </div>
    );
  }
}
