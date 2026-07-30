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

import { WarmPoolService } from './WarmPoolService';
import { AwsModalFooter } from '../../../common/AwsModalFooter';
import type { IAmazonServerGroup } from '../../../domain';

export interface IModifyWarmPoolModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IAmazonServerGroup;
}

interface IModifyWarmPoolModalState {
  enabled: boolean;
  minSize: number;
  maxGroupPreparedCapacity: number;
  poolState: string;
  reuseOnScaleIn: boolean;
  reason?: string;
  taskMonitor: TaskMonitor;
}

export function buildWarmPoolJob(state: IModifyWarmPoolModalState, serverGroup: IAmazonServerGroup): IJob {
  const base = {
    asgName: serverGroup.name,
    regions: [serverGroup.region],
    credentials: serverGroup.account,
    cloudProvider: 'aws',
    reason: state.reason,
  };

  if (!state.enabled) {
    return { ...base, type: 'modifyWarmPool', action: 'delete' };
  }

  return {
    ...base,
    type: 'modifyWarmPool',
    action: 'upsert',
    minSize: state.minSize,
    maxGroupPreparedCapacity: state.maxGroupPreparedCapacity,
    poolState: state.poolState,
    reuseOnScaleIn: state.reuseOnScaleIn,
  };
}

export class ModifyWarmPoolModal extends React.Component<IModifyWarmPoolModalProps, IModifyWarmPoolModalState> {
  public static show(props: IModifyWarmPoolModalProps) {
    return confirmNotManaged(props.serverGroup, props.application).then(
      (notManaged) => notManaged && ReactModal.show(ModifyWarmPoolModal, props),
    );
  }

  private initialConfiguration = WarmPoolService.getWarmPoolConfiguration(this.props.serverGroup);

  public state: IModifyWarmPoolModalState = {
    enabled: !!this.initialConfiguration,
    minSize: this.initialConfiguration?.minSize ?? 0,
    maxGroupPreparedCapacity: this.initialConfiguration?.maxGroupPreparedCapacity ?? -1,
    poolState: this.initialConfiguration?.poolState ?? 'Stopped',
    reuseOnScaleIn: this.initialConfiguration?.instanceReusePolicy?.reuseOnScaleIn ?? false,
    taskMonitor: new TaskMonitor({
      application: this.props.application,
      title: `Update Warm Pool for ${this.props.serverGroup.name}`,
      onDismiss: this.props.dismissModal,
      onTaskComplete: () => this.props.application.serverGroups.refresh(),
    }),
  };

  private submit = () => {
    const { application, serverGroup } = this.props;
    const job = buildWarmPoolJob(this.state, serverGroup);
    this.state.taskMonitor.submit(() =>
      TaskExecutor.executeTask({
        application,
        description: `Update Warm Pool for ${serverGroup.name}`,
        job: [job],
      }),
    );
  };

  public render() {
    const { dismissModal, serverGroup } = this.props;
    const { enabled, minSize, maxGroupPreparedCapacity, poolState, reuseOnScaleIn } = this.state;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Modify Warm Pool for {serverGroup.name}</Modal.Title>
        </Modal.Header>
        <Modal.Body className="container-fluid form-horizontal">
          <div className="form-group">
            <div className="col-sm-offset-2 col-sm-10">
              <div className="checkbox">
                <label>
                  <input
                    checked={enabled}
                    onChange={(event) => this.setState({ enabled: event.target.checked })}
                    type="checkbox"
                  />{' '}
                  Enable Warm Pool
                  <HelpField
                    content="Keeps a pool of pre-initialized instances alongside this Auto Scaling Group to speed up scale-out."
                    placement="right"
                  />
                </label>
              </div>
            </div>
          </div>
          {enabled && (
            <>
              <div className="form-group">
                <label className="col-sm-4 control-label">Min Size</label>
                <div className="col-sm-6">
                  <input
                    className="form-control"
                    type="number"
                    min={0}
                    value={minSize}
                    onChange={(event) => this.setState({ minSize: Number(event.target.value) })}
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="col-sm-4 control-label">
                  Max Group Prepared Capacity
                  <HelpField content="The maximum capacity of the Auto Scaling Group, including the warm pool. -1 means no limit beyond the group's own max size." />
                </label>
                <div className="col-sm-6">
                  <input
                    className="form-control"
                    type="number"
                    min={-1}
                    value={maxGroupPreparedCapacity}
                    onChange={(event) => this.setState({ maxGroupPreparedCapacity: Number(event.target.value) })}
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="col-sm-4 control-label">Instance State</label>
                <div className="col-sm-6">
                  <select
                    className="form-control"
                    value={poolState}
                    onChange={(event) => this.setState({ poolState: event.target.value })}
                  >
                    {WarmPoolService.poolStates().map((state) => (
                      <option key={state} value={state}>
                        {state}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <div className="col-sm-offset-2 col-sm-10">
                  <div className="checkbox">
                    <label>
                      <input
                        checked={reuseOnScaleIn}
                        onChange={(event) => this.setState({ reuseOnScaleIn: event.target.checked })}
                        type="checkbox"
                      />{' '}
                      Reuse instances on scale in
                      <HelpField content="If enabled, instances terminated by scale-in are returned to the warm pool instead of being terminated." />
                    </label>
                  </div>
                </div>
              </div>
            </>
          )}
          <TaskReason reason={this.state.reason} onChange={(reason) => this.setState({ reason })} />
        </Modal.Body>
        <AwsModalFooter account={serverGroup.account} isValid={true} onCancel={dismissModal} onSubmit={this.submit} />
      </>
    );
  }
}
