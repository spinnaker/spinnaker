import React from 'react';

import type { Application, IModalComponentProps, IServerGroup, IServerGroupJob, ITask } from '@spinnaker/core';
import {
  ModalClose,
  PlatformHealthOverride,
  ReactModal,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  ValidationMessage,
} from '@spinnaker/core';

import type { IGceAutoscalingPolicy } from '../../../autoscalingPolicy/IGceAutoscalingPolicy';

export interface IGceResizeValues {
  interestingHealthProviderNames?: string[];
  newMaxNumReplicas?: number;
  newMinNumReplicas?: number;
  newSize?: number;
  reason?: string;
}

export type GceResizeErrors = Partial<Record<keyof IGceResizeValues, string>>;

export interface IGceFixedResizeJob extends IServerGroupJob {
  interestingHealthProviderNames?: string[];
  reason?: string;
  targetSize: number;
}

export interface IGceAutoscalerResizeRequest {
  params: {
    interestingHealthProviderNames?: string[];
    reason?: string;
  };
  policy: IGceAutoscalingPolicy & {
    maxNumReplicas: number;
    minNumReplicas: number;
  };
}

export interface IGceAutoscalingPolicyWriter {
  upsertAutoscalingPolicy(
    application: Application,
    serverGroup: IServerGroup,
    policy: IGceAutoscalerResizeRequest['policy'],
    params: IGceAutoscalerResizeRequest['params'],
  ): PromiseLike<ITask>;
}

export interface IGceResizeServerGroupWriter {
  resizeServerGroup(
    serverGroup: IServerGroup,
    application: Application,
    command: IGceFixedResizeJob,
  ): PromiseLike<ITask>;
}

export interface IGceResizeServerGroupModalProps extends IModalComponentProps {
  application: Application;
  autoscalingPolicyWriter: IGceAutoscalingPolicyWriter;
  serverGroup: IServerGroup;
  serverGroupWriter: IGceResizeServerGroupWriter;
}

interface IGceResizeServerGroupModalState {
  taskMonitor: TaskMonitor;
  values: IGceResizeValues;
  verified: boolean;
}

export function buildGceAutoscalerResizeRequest(
  values: IGceResizeValues & { newMaxNumReplicas: number; newMinNumReplicas: number },
): IGceAutoscalerResizeRequest {
  return {
    params: {
      interestingHealthProviderNames: values.interestingHealthProviderNames,
      reason: values.reason,
    },
    policy: {
      maxNumReplicas: values.newMaxNumReplicas,
      minNumReplicas: values.newMinNumReplicas,
    },
  };
}

export function buildGceFixedResizeJob(
  serverGroup: IServerGroup,
  values: IGceResizeValues & { newSize: number },
): IGceFixedResizeJob {
  return {
    capacity: { desired: values.newSize, max: values.newSize, min: values.newSize },
    interestingHealthProviderNames: values.interestingHealthProviderNames,
    reason: values.reason,
    region: serverGroup.region,
    serverGroupName: serverGroup.name,
    targetSize: values.newSize,
  };
}

export function validateGceResizeValues(serverGroup: IServerGroup, values: IGceResizeValues): GceResizeErrors {
  if (!serverGroup.autoscalingPolicy && values.newSize === undefined) {
    return { newSize: 'Size is required' };
  }

  if (
    !serverGroup.autoscalingPolicy &&
    values.newSize !== undefined &&
    (!Number.isInteger(values.newSize) || values.newSize < 0)
  ) {
    return { newSize: 'Size must be a finite non-negative integer' };
  }

  if (serverGroup.autoscalingPolicy) {
    const errors: GceResizeErrors = {};
    if (values.newMinNumReplicas === undefined) {
      errors.newMinNumReplicas = 'Min is required';
    }
    if (values.newMaxNumReplicas === undefined) {
      errors.newMaxNumReplicas = 'Max is required';
    }
    if (Object.keys(errors).length) {
      return errors;
    }
    if (!Number.isInteger(values.newMinNumReplicas) || values.newMinNumReplicas < 0) {
      errors.newMinNumReplicas = 'Min must be a finite non-negative integer';
    }
    if (!Number.isInteger(values.newMaxNumReplicas) || values.newMaxNumReplicas < 0) {
      errors.newMaxNumReplicas = 'Max must be a finite non-negative integer';
    }
    if (Object.keys(errors).length) {
      return errors;
    }
  }

  if (
    serverGroup.autoscalingPolicy &&
    values.newMinNumReplicas !== undefined &&
    values.newMaxNumReplicas !== undefined &&
    values.newMinNumReplicas > values.newMaxNumReplicas
  ) {
    return {
      newMaxNumReplicas: 'Min cannot be larger than Max',
      newMinNumReplicas: 'Min cannot be larger than Max',
    };
  }

  return {};
}

export class GceResizeServerGroupModal extends React.Component<
  IGceResizeServerGroupModalProps,
  IGceResizeServerGroupModalState
> {
  public static show(props: IGceResizeServerGroupModalProps): Promise<void> {
    return ReactModal.show(GceResizeServerGroupModal, props);
  }

  constructor(props: IGceResizeServerGroupModalProps) {
    super(props);
    const { application } = props;
    this.state = {
      taskMonitor: new TaskMonitor({
        application,
        title: `Resizing ${props.serverGroup.name}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => application.serverGroups.refresh(),
      }),
      values: {
        interestingHealthProviderNames:
          application.attributes?.platformHealthOnlyShowOverride && application.attributes?.platformHealthOnly
            ? ['Google']
            : undefined,
      },
      verified: false,
    };
  }

  private isValid = (): boolean =>
    this.state.verified && Object.keys(validateGceResizeValues(this.props.serverGroup, this.state.values)).length === 0;

  private submit = (): void => {
    if (!this.isValid()) {
      return;
    }

    const { application, autoscalingPolicyWriter, serverGroup, serverGroupWriter } = this.props;
    if (serverGroup.autoscalingPolicy) {
      const { params, policy } = buildGceAutoscalerResizeRequest(
        this.state.values as IGceResizeValues & { newMaxNumReplicas: number; newMinNumReplicas: number },
      );
      this.state.taskMonitor.submit(() =>
        autoscalingPolicyWriter.upsertAutoscalingPolicy(application, serverGroup, policy, params),
      );
      return;
    }

    const command = buildGceFixedResizeJob(serverGroup, this.state.values as IGceResizeValues & { newSize: number });
    this.state.taskMonitor.submit(() => serverGroupWriter.resizeServerGroup(serverGroup, application, command));
  };

  public render(): JSX.Element {
    const { application, serverGroup } = this.props;
    const { values } = this.state;
    const currentSize = serverGroup.asg?.desiredCapacity ?? serverGroup.capacity?.desired;
    const errorMessage = Object.values(validateGceResizeValues(serverGroup, values))[0];

    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <ModalClose dismiss={this.props.dismissModal} />
        <div className="modal-header">
          <h4 className="modal-title">Resize {serverGroup.name}</h4>
        </div>
        <div className="modal-body">
          <form className="form-horizontal">
            {serverGroup.autoscalingPolicy ? (
              <>
                <p>Sets min and max instance counts for this server group's autoscaling policy.</p>
                <div className="form-group">
                  <div className="col-md-2 col-md-offset-3">
                    <b>Min</b>
                  </div>
                  <div className="col-md-2">
                    <b>Max</b>
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 sm-label-right">Current</label>
                  <div className="col-md-2">
                    <input
                      className="form-control input-sm"
                      disabled={true}
                      type="number"
                      value={serverGroup.autoscalingPolicy.minNumReplicas}
                    />
                  </div>
                  <div className="col-md-2">
                    <input
                      className="form-control input-sm"
                      disabled={true}
                      type="number"
                      value={serverGroup.autoscalingPolicy.maxNumReplicas}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 sm-label-right">Resize to</label>
                  <div className="col-md-2">
                    <input
                      className="form-control input-sm"
                      min={0}
                      name="newMinNumReplicas"
                      onChange={(event) =>
                        this.setState({
                          values: {
                            ...values,
                            newMinNumReplicas: event.target.value === '' ? undefined : Number(event.target.value),
                          },
                        })
                      }
                      type="number"
                      value={values.newMinNumReplicas ?? ''}
                    />
                  </div>
                  <div className="col-md-2">
                    <input
                      className="form-control input-sm"
                      min={0}
                      name="newMaxNumReplicas"
                      onChange={(event) =>
                        this.setState({
                          values: {
                            ...values,
                            newMaxNumReplicas: event.target.value === '' ? undefined : Number(event.target.value),
                          },
                        })
                      }
                      type="number"
                      value={values.newMaxNumReplicas ?? ''}
                    />
                  </div>
                </div>
              </>
            ) : (
              <>
                <p>Sets desired instance count for this server group.</p>
                <div className="form-group">
                  <label className="col-md-3 sm-label-right">Current size</label>
                  <div className="col-md-4">
                    <input className="form-control input-sm" disabled={true} type="number" value={currentSize} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="col-md-3 sm-label-right" htmlFor="gce-resize-new-size">
                    Resize to
                  </label>
                  <div className="col-md-4">
                    <input
                      className="form-control input-sm"
                      id="gce-resize-new-size"
                      min={0}
                      name="newSize"
                      onChange={(event) =>
                        this.setState({
                          values: {
                            ...values,
                            newSize: event.target.value === '' ? undefined : Number(event.target.value),
                          },
                        })
                      }
                      type="number"
                      value={values.newSize ?? ''}
                    />
                  </div>
                </div>
              </>
            )}
            {errorMessage && <ValidationMessage message={errorMessage} type="error" />}
            {application.attributes?.platformHealthOnlyShowOverride && (
              <PlatformHealthOverride
                interestingHealthProviderNames={values.interestingHealthProviderNames}
                onChange={(interestingHealthProviderNames) =>
                  this.setState({ values: { ...values, interestingHealthProviderNames } })
                }
                platformHealthType="Google"
                showHelpDetails={true}
              />
            )}
            <TaskReason
              reason={values.reason}
              onChange={(reason) => this.setState({ values: { ...values, reason } })}
            />
          </form>
        </div>
        <div className="modal-footer">
          <UserVerification account={serverGroup.account} onValidChange={(verified) => this.setState({ verified })} />
          <button className="btn btn-default" onClick={this.props.dismissModal} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" disabled={!this.isValid()} onClick={this.submit} type="button">
            Submit
          </button>
        </div>
      </>
    );
  }
}
