import React from 'react';
import { Modal } from 'react-bootstrap';

import type { Application, IJob, IModalComponentProps } from '@spinnaker/core';
import {
  confirmNotManaged,
  ModalClose,
  ReactModal,
  TaskExecutor,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

import { AwsModalFooter } from '../../../common/AwsModalFooter';
import type { IAmazonServerGroupView } from '../../../domain';

export interface IScheduledActionValues {
  recurrence?: string | number;
  minSize?: number;
  maxSize?: number;
  desiredCapacity?: number;
}

export interface IEditScheduledActionsModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IAmazonServerGroupView;
}

interface IEditScheduledActionsModalState {
  scheduledActions: IScheduledActionValues[];
  taskMonitor: TaskMonitor;
}

function projectScheduledAction(action: IScheduledActionValues): IScheduledActionValues {
  return {
    recurrence: action.recurrence,
    minSize: action.minSize,
    maxSize: action.maxSize,
    desiredCapacity: action.desiredCapacity,
  };
}

export function isScheduledActionValid(action: IScheduledActionValues): boolean {
  const capacities = [action.minSize, action.maxSize, action.desiredCapacity];
  if (!String(action.recurrence ?? '').trim() || capacities.every((capacity) => capacity === undefined)) {
    return false;
  }
  if (
    capacities.some(
      (capacity) =>
        capacity !== undefined && (!Number.isFinite(capacity) || !Number.isInteger(capacity) || capacity < 0),
    )
  ) {
    return false;
  }
  if (action.minSize !== undefined && action.maxSize !== undefined && action.minSize > action.maxSize) {
    return false;
  }
  if (action.desiredCapacity !== undefined && action.minSize !== undefined && action.desiredCapacity < action.minSize) {
    return false;
  }
  if (action.desiredCapacity !== undefined && action.maxSize !== undefined && action.desiredCapacity > action.maxSize) {
    return false;
  }
  return true;
}

export function buildScheduledActionsJob(
  serverGroup: IAmazonServerGroupView,
  scheduledActions: IScheduledActionValues[],
): IJob {
  return {
    type: 'upsertAsgScheduledActions',
    asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
    scheduledActions: scheduledActions.map(projectScheduledAction),
    credentials: serverGroup.account,
  };
}

export class EditScheduledActionsModal extends React.Component<
  IEditScheduledActionsModalProps,
  IEditScheduledActionsModalState
> {
  public static show(props: IEditScheduledActionsModalProps) {
    return confirmNotManaged(props.serverGroup, props.application).then(
      (notManaged) => notManaged && ReactModal.show(EditScheduledActionsModal, props, { dialogClassName: 'modal-lg' }),
    );
  }

  public state: IEditScheduledActionsModalState = {
    scheduledActions: (this.props.serverGroup.scheduledActions || []).map(projectScheduledAction),
    taskMonitor: new TaskMonitor({
      application: this.props.application,
      title: `Update Scheduled Actions for ${this.props.serverGroup.name}`,
      modalInstance: TaskMonitor.modalInstanceEmulation(this.props.closeModal, this.props.dismissModal),
      onTaskComplete: () => this.props.application.serverGroups.refresh(),
    }),
  };

  private addScheduledAction = () => {
    this.setState(({ scheduledActions }) => ({ scheduledActions: [...scheduledActions, {}] }));
  };

  private removeScheduledAction = (index: number) => {
    this.setState(({ scheduledActions }) => ({
      scheduledActions: scheduledActions.filter((_action, candidateIndex) => candidateIndex !== index),
    }));
  };

  private updateScheduledAction = (index: number, field: keyof IScheduledActionValues, value: string | number) => {
    this.setState(({ scheduledActions }) => ({
      scheduledActions: scheduledActions.map((action, candidateIndex) =>
        candidateIndex === index ? { ...action, [field]: value === '' ? undefined : value } : action,
      ),
    }));
  };

  private submit = () => {
    const { application, serverGroup } = this.props;
    this.state.taskMonitor.submit(() =>
      TaskExecutor.executeTask({
        application,
        description: `Update Scheduled Actions for ${serverGroup.name}`,
        job: [buildScheduledActionsJob(serverGroup, this.state.scheduledActions)],
      }),
    );
  };

  private renderNumberInput(index: number, field: keyof IScheduledActionValues, value?: number) {
    return (
      <input
        className="form-control input-sm"
        min={0}
        onChange={(event) =>
          this.updateScheduledAction(index, field, event.target.value === '' ? '' : Number(event.target.value))
        }
        type="number"
        value={value ?? ''}
      />
    );
  }

  public render() {
    const { dismissModal, serverGroup } = this.props;
    const valid = this.state.scheduledActions.every(isScheduledActionValid);
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Edit Scheduled Actions for {serverGroup.name}</Modal.Title>
        </Modal.Header>
        <Modal.Body className="container-fluid">
          <p>You must specify at least one of: Min Size, Max Size, Desired Capacity.</p>
          <p>
            <strong>Note:</strong> CRON expressions are evaluated in UTC.
          </p>
          <table className="table table-condensed packed">
            <thead>
              <tr>
                <th>Recurrence (CRON)</th>
                <th>Min Size</th>
                <th>Max Size</th>
                <th>Desired Capacity</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {this.state.scheduledActions.map((action, index) => (
                <tr key={index}>
                  <td>
                    <input
                      className="form-control input-sm no-spel"
                      onChange={(event) => this.updateScheduledAction(index, 'recurrence', event.target.value)}
                      required={true}
                      type="text"
                      value={action.recurrence ?? ''}
                    />
                  </td>
                  <td>{this.renderNumberInput(index, 'minSize', action.minSize)}</td>
                  <td>{this.renderNumberInput(index, 'maxSize', action.maxSize)}</td>
                  <td>{this.renderNumberInput(index, 'desiredCapacity', action.desiredCapacity)}</td>
                  <td>
                    <button
                      aria-label="Remove scheduled action"
                      className="btn btn-link"
                      onClick={() => this.removeScheduledAction(index)}
                      type="button"
                    >
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button className="add-new col-md-12" onClick={this.addScheduledAction} type="button">
            <span className="glyphicon glyphicon-plus-sign" /> Add new Scheduled Action
          </button>
        </Modal.Body>
        <AwsModalFooter account={serverGroup.account} isValid={valid} onCancel={dismissModal} onSubmit={this.submit} />
      </>
    );
  }
}
