import React from 'react';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';
import { GceAutoHealingPolicyEditor } from '../../../../autoHealingPolicy';
import { GceAutoscalingPolicyEditor } from '../../../../autoscalingPolicy';
import type { IGceAutoscalingPolicy } from '../../../../autoscalingPolicy';
import { GcePredictiveMethod } from '../../../../autoscalingPolicy';
import type { IGceAutoHealingPolicy } from '../../../../domain';
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

function isValidCustomMetric(metric: NonNullable<IGceAutoscalingPolicy['customMetricUtilizations']>[number]): boolean {
  if (!metric.metric?.trim() || !metric.metricExportScope) {
    return false;
  }
  if (
    metric.metricExportScope === 'SINGLE_TIME_SERIES_PER_GROUP' &&
    metric.scalingpolicy === 'SINGLE_INSTANCE_ASSIGNMENT'
  ) {
    return (
      typeof metric.singleInstanceAssignment === 'number' &&
      Number.isFinite(metric.singleInstanceAssignment) &&
      metric.singleInstanceAssignment >= 0
    );
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
  return (
    cpuValid &&
    loadBalancingValid &&
    customMetrics.every(isValidCustomMetric) &&
    ((cpuConfigured && cpuValid) ||
      (loadBalancingConfigured && loadBalancingValid) ||
      (customMetrics.length > 0 && customMetrics.every(isValidCustomMetric)))
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

export class Policies extends GceServerGroupWizardPage {
  public validate(values: IGceServerGroupCommand): { [key: string]: any } {
    const errors: { [key: string]: any } = {};

    if (values.autoscalingPolicy) {
      const policy = values.autoscalingPolicy as IGceAutoscalingPolicy;
      const policyErrors: { [key: string]: string } = {};
      const validMinimum = isIntegerInRange(policy.minNumReplicas, 0);
      const validMaximum = isIntegerInRange(policy.maxNumReplicas, 0);
      if (!validMinimum) {
        policyErrors.minNumReplicas = 'Minimum capacity must be a nonnegative integer.';
      }
      if (!validMaximum) {
        policyErrors.maxNumReplicas = 'Maximum capacity must be a nonnegative integer.';
      } else if (validMinimum && policy.maxNumReplicas < policy.minNumReplicas) {
        policyErrors.maxNumReplicas = 'Maximum capacity must be at least the minimum capacity.';
      }
      if (!isIntegerInRange(policy.coolDownPeriodSec, 15)) {
        policyErrors.coolDownPeriodSec = 'Cool-down period must be an integer of at least 15 seconds.';
      }
      if (!hasValidMetrics(policy)) {
        policyErrors.metric = 'At least one complete autoscaling metric required.';
      }
      if (!hasValidSchedules(policy)) {
        policyErrors.scalingSchedules = 'Every scaling schedule must be complete and within supported bounds.';
      }
      if (!hasValidScaleInControl(policy)) {
        policyErrors.scaleInControl = 'Scale-in control values are outside supported bounds.';
      }
      const predictiveEnabled = policy.cpuUtilization?.predictiveMethod === GcePredictiveMethod.STANDARD;
      if (predictiveEnabled && !GCEProviderSettings.feature.predictiveAutoscaling) {
        policyErrors.predictiveAutoscaling = 'Predictive autoscaling is not enabled.';
      }
      if (Object.keys(policyErrors).length) {
        errors.autoscalingPolicy = policyErrors;
      }
    }

    if (values.enableAutoHealing) {
      const policy = (values.autoHealingPolicy || {}) as IGceAutoHealingPolicy;
      const policyErrors: { [key: string]: string } = {};
      if (!policy.healthCheck?.trim()) {
        policyErrors.healthCheck = 'Health check required.';
      }
      if (!policy.healthCheckKind) {
        policyErrors.healthCheckKind = 'Health check kind required.';
      }
      if (!isIntegerInRange(policy.initialDelaySec, 0)) {
        policyErrors.initialDelaySec = 'Initial delay must be an integer between 0 and 2147483647 seconds.';
      }
      if (Object.keys(policyErrors).length) {
        errors.autoHealingPolicy = policyErrors;
      }
    }

    return errors;
  }

  private setField = (field: string, value: any): void => {
    this.props.formik.setFieldValue(field, value);
  };

  private toggleAutoscaling = (enabled: boolean): void => {
    const { values } = this.props.formik;
    const persistedPolicy = values.autoscalingPolicy as IGceAutoscalingPolicy | undefined;
    this.setField(
      'overwriteAncestorAutoscalingPolicy',
      !enabled && values.viewState?.mode === 'clone' && Boolean(persistedPolicy),
    );
    if (enabled) {
      const desired = values.capacity?.desired ?? 1;
      this.setAutoscalingPolicy(
        persistedPolicy || {
          minNumReplicas: desired,
          maxNumReplicas: desired,
          coolDownPeriodSec: 60,
          cpuUtilization: { utilizationTarget: 0.5 },
        },
      );
      return;
    }

    this.setField('enableAutoScaling', false);
    this.setField('autoscalingPolicy', null);
    this.setField('source', { ...values.source, useSourceCapacity: false });
    this.setField('viewState', { ...values.viewState, useSimpleCapacity: true });
    const desired = values.capacity?.desired ?? 1;
    this.setField('capacity', { min: desired, max: desired, desired });
  };

  private setAutoscalingPolicy = (policy: IGceAutoscalingPolicy): void => {
    const { values } = this.props.formik;
    const min = policy.minNumReplicas;
    const max = policy.maxNumReplicas;
    const desired = coherentDesiredCapacity(values.capacity?.desired, min, max);

    this.setField('enableAutoScaling', true);
    this.setField('autoscalingPolicy', policy);
    this.setField('source', { ...values.source, useSourceCapacity: false });
    this.setField('viewState', { ...values.viewState, useSimpleCapacity: false });
    this.setField('capacity', { ...values.capacity, min, max, desired });
  };

  private toggleAutohealing = (enabled: boolean): void => {
    const { values } = this.props.formik;
    const persistedPolicy = values.autoHealingPolicy as IGceAutoHealingPolicy | undefined;
    this.setField('enableAutoHealing', enabled);
    this.setField(
      'overwriteAncestorAutoHealingPolicy',
      !enabled && values.viewState?.mode === 'clone' && Boolean(persistedPolicy),
    );
    if (enabled && !persistedPolicy) {
      this.setField('autoHealingPolicy', { initialDelaySec: 300 });
    }
  };

  private filteredHealthCheckReader(): any {
    const { values } = this.props.formik;
    const healthChecks = values.backingData?.filtered?.healthChecks || [];
    return {
      listHealthChecks: () =>
        Promise.resolve(
          healthChecks.map((healthCheck: any) => ({
            ...healthCheck,
            account: values.credentials,
          })),
        ),
    };
  }

  public render(): JSX.Element {
    const { values } = this.props.formik;
    const autoscalingEnabled = Boolean(values.autoscalingPolicy);
    const autohealingEnabled = values.enableAutoHealing ?? Boolean(values.autoHealingPolicy);

    return (
      <div className="container-fluid form-horizontal" data-testid="gce-policies-page">
        <section>
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Enable Autoscaling</b>
            </div>
            <div className="col-md-6 checkbox">
              <label>
                <input
                  checked={autoscalingEnabled}
                  data-testid="enable-autoscaling"
                  onChange={(event) => this.toggleAutoscaling(event.target.checked)}
                  type="checkbox"
                />
                <span className="sr-only">Enable Autoscaling</span>
              </label>
            </div>
          </div>
          {autoscalingEnabled && (
            <GceAutoscalingPolicyEditor
              policy={(values.autoscalingPolicy || {}) as IGceAutoscalingPolicy}
              onChange={this.setAutoscalingPolicy}
            />
          )}
        </section>

        <section>
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Enable Autohealing</b>
            </div>
            <div className="col-md-6 checkbox">
              <label>
                <input
                  checked={autohealingEnabled}
                  data-testid="enable-autohealing"
                  onChange={(event) => this.toggleAutohealing(event.target.checked)}
                  type="checkbox"
                />
                <span className="sr-only">Enable Autohealing</span>
              </label>
            </div>
          </div>
          {autohealingEnabled && (
            <GceAutoHealingPolicyEditor
              key={values.credentials || ''}
              account={values.credentials || ''}
              policy={(values.autoHealingPolicy || {}) as IGceAutoHealingPolicy}
              reader={this.filteredHealthCheckReader()}
              onChange={(policy) => this.setField('autoHealingPolicy', policy)}
            />
          )}
        </section>
      </div>
    );
  }
}

function coherentDesiredCapacity(desired: any, min: any, max: any): any {
  if (typeof desired === 'number' && typeof min === 'number' && typeof max === 'number') {
    return Math.min(max, Math.max(min, desired));
  }
  return desired ?? min ?? max ?? 1;
}
