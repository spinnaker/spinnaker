import React from 'react';

import type {
  GceMetricExportScope,
  GceScalingPolicyType,
  GceUtilizationTargetType,
  IGceAutoscalingCustomMetric,
  IGceAutoscalingPolicy,
  IGceScalingSchedule,
} from './IGceAutoscalingPolicy';
import { GcePredictiveMethod } from './IGceAutoscalingPolicy';
// @ts-ignore JSON imports are bundled by Rollup's JSON plugin.
import timezoneData from './components/scalingSchedules/standardTimezone.json';
import { GCEProviderSettings } from '../gce.settings';

const timezones = timezoneData as string[];

export interface IGceAutoscalingPolicyEditorProps {
  policy: IGceAutoscalingPolicy;
  onChange: (policy: IGceAutoscalingPolicy) => void;
  predictiveAutoscalingEnabled?: boolean;
}

function numberValue(value: string): number | undefined {
  return value === '' ? undefined : Number(value);
}

function percentValue(value?: number): number | '' {
  return value === undefined ? '' : value * 100;
}

export function GceAutoscalingPolicyEditor({
  policy,
  onChange,
  predictiveAutoscalingEnabled = Boolean(GCEProviderSettings.feature.predictiveAutoscaling),
}: IGceAutoscalingPolicyEditorProps): JSX.Element {
  const update = <K extends keyof IGceAutoscalingPolicy>(key: K, value: IGceAutoscalingPolicy[K]): void => {
    onChange({ ...policy, [key]: value });
  };
  const customMetrics = policy.customMetricUtilizations || [];
  const schedules = policy.scalingSchedules || [];
  const cpuMetricConfigured = Boolean(policy.cpuUtilization && Object.keys(policy.cpuUtilization).length);
  const loadBalancingMetricConfigured = Boolean(
    policy.loadBalancingUtilization && Object.keys(policy.loadBalancingUtilization).length,
  );
  const scaleInUnit = typeof policy.scaleInControl?.maxScaledInReplicas?.percent === 'number' ? 'percent' : 'fixed';
  const scaleInMaximum = policy.scaleInControl?.maxScaledInReplicas?.[scaleInUnit];

  const updateCustomMetric = (index: number, changes: Partial<IGceAutoscalingCustomMetric>): void => {
    update(
      'customMetricUtilizations',
      customMetrics.map((metric, metricIndex) => (metricIndex === index ? { ...metric, ...changes } : metric)),
    );
  };
  const updateSchedule = (index: number, changes: Partial<IGceScalingSchedule>): void => {
    update(
      'scalingSchedules',
      schedules.map((schedule, scheduleIndex) => (scheduleIndex === index ? { ...schedule, ...changes } : schedule)),
    );
  };
  const updateCustomMetricScalingPolicy = (index: number, scalingpolicy: GceScalingPolicyType): void => {
    const metric = customMetrics[index];
    let compatibleMetric: IGceAutoscalingCustomMetric;
    if (scalingpolicy === 'SINGLE_INSTANCE_ASSIGNMENT') {
      const { utilizationTarget: _target, utilizationTargetType: _type, ...assignmentMetric } = metric;
      compatibleMetric = assignmentMetric;
    } else {
      const { singleInstanceAssignment: _assignment, ...utilizationMetric } = metric;
      compatibleMetric = utilizationMetric;
    }
    update(
      'customMetricUtilizations',
      customMetrics.map((customMetric, metricIndex) =>
        metricIndex === index ? { ...compatibleMetric, scalingpolicy } : customMetric,
      ),
    );
  };

  return (
    <div className="form-horizontal gce-autoscaling-policy-editor">
      <h4>Capacity</h4>
      <div className="form-group">
        <label className="col-md-4 control-label">Minimum number of instances</label>
        <div className="col-md-4">
          <input
            className="form-control input-sm"
            data-testid="minimum-replicas"
            min={0}
            type="number"
            value={policy.minNumReplicas ?? ''}
            onChange={(event) => update('minNumReplicas', numberValue(event.target.value))}
          />
        </div>
      </div>
      <div className="form-group">
        <label className="col-md-4 control-label">Maximum number of instances</label>
        <div className="col-md-4">
          <input
            className="form-control input-sm"
            data-testid="maximum-replicas"
            min={policy.minNumReplicas ?? 0}
            type="number"
            value={policy.maxNumReplicas ?? ''}
            onChange={(event) => update('maxNumReplicas', numberValue(event.target.value))}
          />
        </div>
      </div>
      <div className="form-group">
        <label className="col-md-4 control-label">Cool-down period (seconds)</label>
        <div className="col-md-4">
          <input
            className="form-control input-sm"
            data-testid="cooldown"
            min={15}
            type="number"
            value={policy.coolDownPeriodSec ?? ''}
            onChange={(event) => update('coolDownPeriodSec', numberValue(event.target.value))}
          />
        </div>
      </div>
      <div className="form-group">
        <label className="col-md-4 control-label">Mode</label>
        <div className="col-md-4">
          <select
            className="form-control input-sm"
            data-testid="mode"
            value={policy.mode || 'ON'}
            onChange={(event) => update('mode', event.target.value as IGceAutoscalingPolicy['mode'])}
          >
            <option value="ON">ON</option>
            <option value="OFF">OFF</option>
            <option value="ONLY_SCALE_OUT">ONLY_SCALE_OUT</option>
          </select>
        </div>
      </div>

      <h4>Metrics</h4>
      {cpuMetricConfigured ? (
        <div className="form-group">
          <label className="col-md-4 control-label">CPU utilization target (%)</label>
          <div className="col-md-4">
            <input
              className="form-control input-sm"
              data-testid="cpu-target"
              max={99}
              min={0}
              type="number"
              value={percentValue(policy.cpuUtilization.utilizationTarget)}
              onChange={(event) =>
                update('cpuUtilization', {
                  ...policy.cpuUtilization,
                  utilizationTarget:
                    event.target.value === '' ? undefined : (numberValue(event.target.value) as number) / 100,
                })
              }
            />
            <button className="btn btn-link" type="button" onClick={() => update('cpuUtilization', {})}>
              Delete CPU metric
            </button>
          </div>
          {predictiveAutoscalingEnabled && (
            <div className="col-md-4 checkbox">
              <label>
                <input
                  data-testid="predictive-autoscaling"
                  type="checkbox"
                  checked={policy.cpuUtilization.predictiveMethod === GcePredictiveMethod.STANDARD}
                  onChange={(event) =>
                    update('cpuUtilization', {
                      ...policy.cpuUtilization,
                      predictiveMethod: event.target.checked ? GcePredictiveMethod.STANDARD : GcePredictiveMethod.NONE,
                    })
                  }
                />{' '}
                Enable predictive autoscaling
              </label>
            </div>
          )}
        </div>
      ) : (
        <button
          className="btn btn-link"
          type="button"
          onClick={() => update('cpuUtilization', { utilizationTarget: undefined })}
        >
          Add CPU utilization metric
        </button>
      )}

      {loadBalancingMetricConfigured ? (
        <div className="form-group">
          <label className="col-md-4 control-label">HTTP load-balancing target (%)</label>
          <div className="col-md-4">
            <input
              className="form-control input-sm"
              data-testid="http-lb-target"
              max={99}
              min={0}
              type="number"
              value={percentValue(policy.loadBalancingUtilization.utilizationTarget)}
              onChange={(event) =>
                update('loadBalancingUtilization', {
                  utilizationTarget:
                    event.target.value === '' ? undefined : (numberValue(event.target.value) as number) / 100,
                })
              }
            />
            <button className="btn btn-link" type="button" onClick={() => update('loadBalancingUtilization', {})}>
              Delete HTTP load-balancing metric
            </button>
          </div>
        </div>
      ) : (
        <button
          className="btn btn-link"
          type="button"
          onClick={() => update('loadBalancingUtilization', { utilizationTarget: undefined })}
        >
          Add HTTP load-balancing metric
        </button>
      )}

      {customMetrics.map((metric, index) => (
        <fieldset key={index} className="well well-sm">
          <legend>Custom metric {index + 1}</legend>
          <input
            aria-label="Metric identifier"
            className="form-control input-sm"
            data-testid={`custom-metric-name-${index}`}
            placeholder="Metric identifier"
            value={metric.metric || ''}
            onChange={(event) => updateCustomMetric(index, { metric: event.target.value })}
          />
          <input
            aria-label="Additional filter expression"
            className="form-control input-sm"
            placeholder="Additional filter expression"
            value={metric.filter || ''}
            onChange={(event) => updateCustomMetric(index, { filter: event.target.value })}
          />
          <select
            aria-label="Metric export scope"
            className="form-control input-sm"
            value={metric.metricExportScope || ''}
            onChange={(event) => {
              const metricExportScope = event.target.value as GceMetricExportScope;
              const {
                scalingpolicy: _scalingpolicy,
                singleInstanceAssignment: _assignment,
                ...compatibleMetric
              } = metric;
              update(
                'customMetricUtilizations',
                customMetrics.map((customMetric, metricIndex) =>
                  metricIndex === index
                    ? metricExportScope === 'TIME_SERIES_PER_INSTANCE'
                      ? { ...compatibleMetric, metricExportScope }
                      : { ...customMetric, metricExportScope, scalingpolicy: 'UTILIZATION_TARGET' }
                    : customMetric,
                ),
              );
            }}
          >
            <option value="">Select export scope</option>
            <option value="TIME_SERIES_PER_INSTANCE">Time series per instance</option>
            <option value="SINGLE_TIME_SERIES_PER_GROUP">Single time series per group</option>
          </select>
          {metric.metricExportScope === 'SINGLE_TIME_SERIES_PER_GROUP' && (
            <select
              aria-label="Scaling policy"
              className="form-control input-sm"
              value={metric.scalingpolicy || ''}
              onChange={(event) => updateCustomMetricScalingPolicy(index, event.target.value as GceScalingPolicyType)}
            >
              <option value="UTILIZATION_TARGET">Utilization target</option>
              <option value="SINGLE_INSTANCE_ASSIGNMENT">Single instance assignment</option>
            </select>
          )}
          {metric.scalingpolicy === 'SINGLE_INSTANCE_ASSIGNMENT' ? (
            <input
              aria-label="Single instance assignment"
              className="form-control input-sm"
              min={0}
              type="number"
              value={metric.singleInstanceAssignment ?? ''}
              onChange={(event) =>
                updateCustomMetric(index, { singleInstanceAssignment: numberValue(event.target.value) })
              }
            />
          ) : (
            <>
              <input
                aria-label="Utilization target"
                className="form-control input-sm"
                data-testid={`custom-metric-target-${index}`}
                type="number"
                value={metric.utilizationTarget ?? ''}
                onChange={(event) => updateCustomMetric(index, { utilizationTarget: numberValue(event.target.value) })}
              />
              <select
                aria-label="Utilization target type"
                className="form-control input-sm"
                value={metric.utilizationTargetType || ''}
                onChange={(event) =>
                  updateCustomMetric(index, { utilizationTargetType: event.target.value as GceUtilizationTargetType })
                }
              >
                <option value="">Select target type</option>
                <option value="GAUGE">Gauge</option>
                <option value="DELTA_PER_SECOND">Delta / second</option>
                <option value="DELTA_PER_MINUTE">Delta / minute</option>
              </select>
            </>
          )}
          <button
            className="btn btn-link"
            type="button"
            onClick={() =>
              update(
                'customMetricUtilizations',
                customMetrics.filter((_, metricIndex) => metricIndex !== index),
              )
            }
          >
            Delete custom metric
          </button>
        </fieldset>
      ))}
      <button
        className="btn btn-link"
        type="button"
        onClick={() => update('customMetricUtilizations', [...customMetrics, {}])}
      >
        Add custom metric
      </button>

      <h4>Scale-in controls</h4>
      <label className="checkbox-inline">
        <input
          data-testid="scale-in-enabled"
          type="checkbox"
          checked={Boolean(policy.scaleInControl)}
          onChange={(event) =>
            update(
              'scaleInControl',
              event.target.checked ? { maxScaledInReplicas: { percent: 0 }, timeWindowSec: 60 } : undefined,
            )
          }
        />{' '}
        Enable scale-in controls
      </label>
      {policy.scaleInControl && (
        <div className="form-group">
          <label className="col-md-4 control-label">Max scaled-in replicas</label>
          <div className="col-md-4">
            <input
              className="form-control input-sm"
              data-testid="scale-in-maximum"
              min={0}
              type="number"
              value={scaleInMaximum ?? ''}
              onChange={(event) =>
                update('scaleInControl', {
                  ...policy.scaleInControl,
                  maxScaledInReplicas: { [scaleInUnit]: numberValue(event.target.value) },
                })
              }
            />
            <select
              className="form-control input-sm"
              data-testid="scale-in-unit"
              value={scaleInUnit}
              onChange={(event) =>
                update('scaleInControl', {
                  ...policy.scaleInControl,
                  maxScaledInReplicas: { [event.target.value]: scaleInMaximum },
                })
              }
            >
              <option value="percent">percent</option>
              <option value="fixed">fixed</option>
            </select>
            <input
              aria-label="Scale-in time window in seconds"
              className="form-control input-sm"
              max={3600}
              min={60}
              type="number"
              value={policy.scaleInControl.timeWindowSec ?? ''}
              onChange={(event) =>
                update('scaleInControl', { ...policy.scaleInControl, timeWindowSec: numberValue(event.target.value) })
              }
            />
          </div>
        </div>
      )}

      <h4>Scaling schedules</h4>
      {schedules.map((schedule, index) => (
        <fieldset key={index} className="well well-sm">
          <legend>Schedule {index + 1}</legend>
          <input
            aria-label="Schedule name"
            className="form-control input-sm"
            value={schedule.scheduleName || ''}
            onChange={(event) => updateSchedule(index, { scheduleName: event.target.value })}
          />
          <input
            aria-label="Schedule description"
            className="form-control input-sm"
            value={schedule.scheduleDescription || ''}
            onChange={(event) => updateSchedule(index, { scheduleDescription: event.target.value })}
          />
          <label className="checkbox-inline">
            <input
              data-testid={`schedule-enabled-${index}`}
              type="checkbox"
              checked={schedule.enabled ?? false}
              onChange={(event) => updateSchedule(index, { enabled: event.target.checked })}
            />{' '}
            Enabled
          </label>
          <input
            aria-label="Minimum required instances"
            className="form-control input-sm"
            data-testid={`schedule-minimum-${index}`}
            min={0}
            type="number"
            value={schedule.minimumRequiredInstances ?? ''}
            onChange={(event) => updateSchedule(index, { minimumRequiredInstances: numberValue(event.target.value) })}
          />
          <input
            aria-label="CRON expression"
            className="form-control input-sm"
            value={schedule.scheduleCron || ''}
            onChange={(event) => updateSchedule(index, { scheduleCron: event.target.value })}
          />
          <select
            aria-label="Time zone"
            className="form-control input-sm"
            data-testid={`schedule-timezone-${index}`}
            value={schedule.timezone || ''}
            onChange={(event) => updateSchedule(index, { timezone: event.target.value })}
          >
            <option value="">Select time zone</option>
            {timezones.map((timezone) => (
              <option key={timezone} value={timezone}>
                {timezone}
              </option>
            ))}
          </select>
          <input
            aria-label="Duration in seconds"
            className="form-control input-sm"
            min={301}
            type="number"
            value={schedule.duration ?? ''}
            onChange={(event) => updateSchedule(index, { duration: numberValue(event.target.value) })}
          />
          <button
            className="btn btn-link"
            type="button"
            onClick={() =>
              update(
                'scalingSchedules',
                schedules.filter((_, scheduleIndex) => scheduleIndex !== index),
              )
            }
          >
            Delete schedule
          </button>
        </fieldset>
      ))}
      <button className="btn btn-link" type="button" onClick={() => update('scalingSchedules', [...schedules, {}])}>
        Add scaling schedule
      </button>
    </div>
  );
}
