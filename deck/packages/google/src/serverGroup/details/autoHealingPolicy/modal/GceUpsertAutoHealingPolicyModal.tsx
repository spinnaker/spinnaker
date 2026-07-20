import { cloneDeep } from 'lodash';
import React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { ModalClose, ReactModal, SubmitButton, TaskMonitor, TaskMonitorWrapper } from '@spinnaker/core';

import { GceAutoHealingPolicyEditor } from '../../../../autoHealingPolicy';
import type { IGcePolicyServerGroup } from '../../../../autoscalingPolicy';
import { GceAutoscalingPolicyWriter } from '../../../../autoscalingPolicy';
import type { IGceAutoHealingPolicy } from '../../../../domain';

export interface IGceUpsertAutoHealingPolicyModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IGcePolicyServerGroup;
  policy?: IGceAutoHealingPolicy;
}

interface IGceUpsertAutoHealingPolicyModalState {
  policy: IGceAutoHealingPolicy;
  taskMonitor: TaskMonitor;
}

const MAX_INT = 2147483647;

function isIntegerInRange(value: unknown, maximum = MAX_INT): value is number {
  return (
    typeof value === 'number' && Number.isFinite(value) && Number.isInteger(value) && value >= 0 && value <= maximum
  );
}

function isValidPolicy(policy: IGceAutoHealingPolicy): boolean {
  return Boolean(policy.healthCheck?.trim() && policy.healthCheckKind && isIntegerInRange(policy.initialDelaySec));
}

export class GceUpsertAutoHealingPolicyModal extends React.Component<
  IGceUpsertAutoHealingPolicyModalProps,
  IGceUpsertAutoHealingPolicyModalState
> {
  public static show(props: IGceUpsertAutoHealingPolicyModalProps): Promise<void> {
    return ReactModal.show(GceUpsertAutoHealingPolicyModal, props, { dialogClassName: 'modal-md' });
  }

  public constructor(props: IGceUpsertAutoHealingPolicyModalProps) {
    super(props);
    const action = props.policy ? 'Edit' : 'New';
    const policy = cloneDeep(props.policy || { initialDelaySec: 300 });
    Reflect.deleteProperty(policy, 'maxUnavailable');
    this.state = {
      policy,
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: `${action} autohealing policy for ${props.serverGroup.name}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal?.(),
          () => props.dismissModal?.(),
        ),
        onTaskComplete: () => {
          props.application.serverGroups?.refresh?.();
          props.closeModal?.();
        },
      }),
    };
  }

  private submit = (): void => {
    this.state.taskMonitor.submit(() =>
      GceAutoscalingPolicyWriter.upsertAutoHealingPolicy(
        this.props.application,
        this.props.serverGroup,
        this.state.policy,
      ),
    );
  };

  public render(): JSX.Element {
    const isNew = !this.props.policy;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <ModalClose dismiss={this.props.dismissModal} />
        <div className="modal-header">
          <h4 className="modal-title">{isNew ? 'New' : 'Edit'} autohealing policy</h4>
        </div>
        <div className="modal-body">
          <GceAutoHealingPolicyEditor
            account={this.props.serverGroup.account}
            policy={this.state.policy}
            onChange={(policy) => this.setState({ policy })}
          />
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" type="button" onClick={() => this.props.dismissModal?.()}>
            Cancel
          </button>
          <SubmitButton
            isDisabled={!isValidPolicy(this.state.policy)}
            label={isNew ? 'Create' : 'Update'}
            onClick={this.submit}
            submitting={this.state.taskMonitor.submitting}
          />
        </div>
      </>
    );
  }
}
