import React from 'react';
import { Modal } from 'react-bootstrap';

import type { Application, IJob, IModalComponentProps } from '@spinnaker/core';
import {
  confirmNotManaged,
  HelpField,
  ModalClose,
  ReactModal,
  TaskExecutor,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
} from '@spinnaker/core';

import { AutoScalingProcessService } from './AutoScalingProcessService';
import { AwsModalFooter } from '../../../common/AwsModalFooter';
import type { IAmazonServerGroup, IScalingProcess } from '../../../domain';

export interface IModifyScalingProcessesModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IAmazonServerGroup;
}

interface IModifyScalingProcessesModalState {
  processes: IScalingProcess[];
  reason?: string;
  taskMonitor: TaskMonitor;
}

export function buildScalingProcessJobs(
  initial: IScalingProcess[],
  edited: IScalingProcess[],
  serverGroup: IAmazonServerGroup,
  reason?: string,
): IJob[] {
  const changed = edited.filter(
    (process) => initial.find((candidate) => candidate.name === process.name)?.enabled !== process.enabled,
  );
  const resumed = changed.filter((process) => process.enabled).map((process) => process.name);
  const suspended = changed.filter((process) => !process.enabled).map((process) => process.name);
  const job = (action: 'resume' | 'suspend', processes: string[]): IJob => ({
    type: 'modifyScalingProcess',
    action,
    processes,
    asgName: serverGroup.name,
    regions: [serverGroup.region],
    credentials: serverGroup.account,
    cloudProvider: 'aws',
    reason,
  });

  return [resumed.length && job('resume', resumed), suspended.length && job('suspend', suspended)].filter(
    Boolean,
  ) as IJob[];
}

export class ModifyScalingProcessesModal extends React.Component<
  IModifyScalingProcessesModalProps,
  IModifyScalingProcessesModalState
> {
  public static show(props: IModifyScalingProcessesModalProps) {
    return confirmNotManaged(props.serverGroup, props.application).then(
      (notManaged) => notManaged && ReactModal.show(ModifyScalingProcessesModal, props),
    );
  }

  private initialProcesses = AutoScalingProcessService.normalizeScalingProcesses(this.props.serverGroup);

  public state: IModifyScalingProcessesModalState = {
    processes: this.initialProcesses.map((process) => ({ ...process })),
    taskMonitor: new TaskMonitor({
      application: this.props.application,
      title: `Update Auto Scaling Processes for ${this.props.serverGroup.name}`,
      modalInstance: TaskMonitor.modalInstanceEmulation(this.props.closeModal, this.props.dismissModal),
      onTaskComplete: () => this.props.application.serverGroups.refresh(),
    }),
  };

  private setProcessEnabled = (index: number, enabled: boolean) => {
    const processes = this.state.processes.map((process, candidateIndex) =>
      candidateIndex === index ? { ...process, enabled } : process,
    );
    this.setState({ processes });
  };

  private getJobs = () =>
    buildScalingProcessJobs(this.initialProcesses, this.state.processes, this.props.serverGroup, this.state.reason);

  private submit = () => {
    const { application, serverGroup } = this.props;
    const job = this.getJobs();
    this.state.taskMonitor.submit(() =>
      TaskExecutor.executeTask({
        application,
        description: `Update Auto Scaling Processes for ${serverGroup.name}`,
        job,
      }),
    );
  };

  public render() {
    const { dismissModal, serverGroup } = this.props;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Modify Scaling Processes for {serverGroup.name}</Modal.Title>
        </Modal.Header>
        <Modal.Body className="container-fluid form-horizontal">
          <div className="form-group">
            <div className="col-sm-offset-2 col-sm-10">
              {this.state.processes.map((process, index) => (
                <div className="checkbox" key={process.name}>
                  <label>
                    <input
                      checked={process.enabled}
                      onChange={(event) => this.setProcessEnabled(index, event.target.checked)}
                      type="checkbox"
                    />{' '}
                    {process.name}
                  </label>{' '}
                  <HelpField content={process.description} placement="right" />
                </div>
              ))}
            </div>
          </div>
          <TaskReason reason={this.state.reason} onChange={(reason) => this.setState({ reason })} />
        </Modal.Body>
        <AwsModalFooter
          account={serverGroup.account}
          isValid={this.getJobs().length > 0}
          onCancel={dismissModal}
          onSubmit={this.submit}
        />
      </>
    );
  }
}
