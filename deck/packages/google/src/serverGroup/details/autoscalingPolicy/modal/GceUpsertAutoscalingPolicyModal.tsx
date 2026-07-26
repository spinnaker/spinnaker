import { cloneDeep } from 'lodash';
import React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { ModalClose, ReactModal, SubmitButton, TaskMonitor, TaskMonitorWrapper } from '@spinnaker/core';

import type { IGceAutoscalingPolicy, IGcePolicyServerGroup } from '../../../../autoscalingPolicy';
import {
  GceAutoscalingPolicyEditor,
  GceAutoscalingPolicyWriter,
  GcePredictiveMethod,
} from '../../../../autoscalingPolicy';
import { GCEProviderSettings } from '../../../../gce.settings';

const MAX_INT = 2147483647;
const MAX_SCHEDULE_DURATION_SECONDS = 14 * 24 * 60 * 60;

function isIntegerInRange(value: unknown, minimum: number, maximum = MAX_INT): value is number {
  return (
    typeof value === 'number' &&
    Number.isFinite(value) &&
    Number.isInteger(value) &&
    value >= minimum &&
    value <= maximum
  );
}

function isUtilizationTarget(value: unknown, maximum?: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 && (maximum === undefined || value < maximum);
}

function isSingleInstanceAssignment(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function isValidCustomMetric(metric: NonNullable<IGceAutoscalingPolicy['customMetricUtilizations']>[number]): boolean {
  if (!metric.metric?.trim() || !metric.metricExportScope) {
    return false;
  }
  if (
    metric.metricExportScope === 'SINGLE_TIME_SERIES_PER_GROUP' &&
    metric.scalingpolicy === 'SINGLE_INSTANCE_ASSIGNMENT'
  ) {
    return isSingleInstanceAssignment(metric.singleInstanceAssignment);
  }
  if (metric.metricExportScope === 'SINGLE_TIME_SERIES_PER_GROUP' && metric.scalingpolicy !== 'UTILIZATION_TARGET') {
    return false;
  }
  return isUtilizationTarget(metric.utilizationTarget) && Boolean(metric.utilizationTargetType);
}

function hasValidMetrics(policy: IGceAutoscalingPolicy): boolean {
  const cpuConfigured = Boolean(policy.cpuUtilization && Object.keys(policy.cpuUtilization).length);
  const loadBalancingConfigured = Boolean(
    policy.loadBalancingUtilization && Object.keys(policy.loadBalancingUtilization).length,
  );
  const customMetrics = policy.customMetricUtilizations || [];
  const cpuValid = !cpuConfigured || isUtilizationTarget(policy.cpuUtilization?.utilizationTarget, 1);
  const loadBalancingValid =
    !loadBalancingConfigured || isUtilizationTarget(policy.loadBalancingUtilization?.utilizationTarget, 1);
  const customMetricsValid = customMetrics.every(isValidCustomMetric);
  return (
    cpuValid &&
    loadBalancingValid &&
    customMetricsValid &&
    ((cpuConfigured && cpuValid) || (loadBalancingConfigured && loadBalancingValid) || customMetrics.length > 0)
  );
}

function hasValidSchedules(policy: IGceAutoscalingPolicy): boolean {
  return (policy.scalingSchedules || []).every(
    (schedule) =>
      Boolean(schedule.scheduleName?.trim()) &&
      isIntegerInRange(schedule.minimumRequiredInstances, 0, policy.maxNumReplicas) &&
      Boolean(schedule.scheduleCron?.trim()) &&
      Boolean(schedule.timezone?.trim()) &&
      isIntegerInRange(schedule.duration, 301, MAX_SCHEDULE_DURATION_SECONDS),
  );
}

function hasValidScaleInControl(policy: IGceAutoscalingPolicy): boolean {
  if (!policy.scaleInControl) {
    return true;
  }
  const { maxScaledInReplicas, timeWindowSec } = policy.scaleInControl;
  const hasFixed = maxScaledInReplicas?.fixed !== undefined;
  const hasPercent = maxScaledInReplicas?.percent !== undefined;
  if (hasFixed === hasPercent || !isIntegerInRange(timeWindowSec, 60, 3600)) {
    return false;
  }
  return hasPercent
    ? isIntegerInRange(maxScaledInReplicas?.percent, 0, 100)
    : isIntegerInRange(maxScaledInReplicas?.fixed, 0);
}

export interface IGceUpsertAutoscalingPolicyModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IGcePolicyServerGroup & { capacity?: { min?: number; max?: number } };
  policy?: IGceAutoscalingPolicy;
}

interface IGceUpsertAutoscalingPolicyModalState {
  policy: IGceAutoscalingPolicy;
  taskMonitor: TaskMonitor;
}

function initialPolicy(props: IGceUpsertAutoscalingPolicyModalProps): IGceAutoscalingPolicy {
  return cloneDeep(
    props.policy || {
      minNumReplicas: props.serverGroup.capacity?.min ?? 0,
      maxNumReplicas: props.serverGroup.capacity?.max ?? 1,
      coolDownPeriodSec: 60,
      mode: 'ON',
      cpuUtilization: { utilizationTarget: 0.6 },
    },
  );
}

export class GceUpsertAutoscalingPolicyModal extends React.Component<
  IGceUpsertAutoscalingPolicyModalProps,
  IGceUpsertAutoscalingPolicyModalState
> {
  public static show(props: IGceUpsertAutoscalingPolicyModalProps): Promise<void> {
    return ReactModal.show(GceUpsertAutoscalingPolicyModal, props, { dialogClassName: 'modal-lg' });
  }

  public constructor(props: IGceUpsertAutoscalingPolicyModalProps) {
    super(props);
    const action = props.policy ? 'Edit' : 'New';
    this.state = {
      policy: initialPolicy(props),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: `${action} scaling policy for ${props.serverGroup.name}`,
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
      GceAutoscalingPolicyWriter.upsertAutoscalingPolicy(
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
          <h4 className="modal-title">{isNew ? 'New' : 'Edit'} autoscaling policy</h4>
        </div>
        <div className="modal-body">
          <GceAutoscalingPolicyEditor policy={this.state.policy} onChange={(policy) => this.setState({ policy })} />
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" type="button" onClick={() => this.props.dismissModal?.()}>
            Cancel
          </button>
          <SubmitButton
            isDisabled={!this.isValid()}
            label={isNew ? 'Create' : 'Update'}
            onClick={this.submit}
            submitting={this.state.taskMonitor.submitting}
          />
        </div>
      </>
    );
  }

  private isValid(): boolean {
    const { policy } = this.state;
    const predictiveEnabled = policy.cpuUtilization?.predictiveMethod === GcePredictiveMethod.STANDARD;
    return (
      isIntegerInRange(policy.minNumReplicas, 0) &&
      isIntegerInRange(policy.maxNumReplicas, 0) &&
      policy.minNumReplicas <= policy.maxNumReplicas &&
      isIntegerInRange(policy.coolDownPeriodSec, 15) &&
      hasValidMetrics(policy) &&
      hasValidSchedules(policy) &&
      hasValidScaleInControl(policy) &&
      (!predictiveEnabled || Boolean(GCEProviderSettings.feature.predictiveAutoscaling)) &&
      (!predictiveEnabled || isUtilizationTarget(policy.cpuUtilization?.utilizationTarget, 1))
    );
  }
}
