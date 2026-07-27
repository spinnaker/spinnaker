import React from 'react';

import type { Application, IManagedResource } from '@spinnaker/core';
import { ConfirmationModalService, confirmNotManaged } from '@spinnaker/core';

import type { IGceAutoscalingPolicy, IGcePolicyServerGroup } from '../../../autoscalingPolicy';
import { GceAutoscalingPolicyWriter } from '../../../autoscalingPolicy';
import { GCEProviderSettings } from '../../../gce.settings';
import { GceUpsertAutoscalingPolicyModal } from './modal/GceUpsertAutoscalingPolicyModal';

export interface IGceAutoscalingPolicyDetailsProps {
  application: Application;
  mutationsEnabled: boolean;
  serverGroup: IGcePolicyServerGroup & IManagedResource & { autoscalingMessages?: string[] };
  policy?: IGceAutoscalingPolicy;
}

function percentage(value?: number): string {
  return value === undefined ? '-' : `${Math.round(value * 100)}%`;
}

export function GceAutoscalingPolicyDetails({
  application,
  mutationsEnabled,
  serverGroup,
  policy,
}: IGceAutoscalingPolicyDetailsProps): JSX.Element | null {
  const runIfNotManaged = (action: () => void): void => {
    confirmNotManaged(serverGroup, application).then((notManaged) => notManaged && action());
  };

  if (!policy) {
    return mutationsEnabled ? (
      <button
        className="btn btn-link"
        data-testid="add-autoscaling-policy"
        type="button"
        onClick={() => runIfNotManaged(() => GceUpsertAutoscalingPolicyModal.show({ application, serverGroup }))}
      >
        Add autoscaling policy
      </button>
    ) : null;
  }

  const maxScaledInReplicas = policy.scaleInControl?.maxScaledInReplicas;
  const maxScaledIn =
    typeof maxScaledInReplicas?.percent === 'number'
      ? `${maxScaledInReplicas.percent}%`
      : typeof maxScaledInReplicas?.fixed === 'number'
      ? `${maxScaledInReplicas.fixed}`
      : undefined;

  const deletePolicy = (): void => {
    ConfirmationModalService.confirm({
      header: `Really delete autoscaler for ${serverGroup.name}?`,
      buttonText: 'Delete autoscaler',
      account: serverGroup.account,
      taskMonitorConfig: { application, title: `Deleting autoscaler for ${serverGroup.name}` },
      submitMethod: () => GceAutoscalingPolicyWriter.deleteAutoscalingPolicy(application, serverGroup),
    });
  };

  return (
    <div className="gce-autoscaling-policy-details">
      <dl className="horizontal-when-filters-collapsed">
        {policy.cpuUtilization && (
          <>
            <dt>Target CPU Usage</dt>
            <dd>CPU Usage: {percentage(policy.cpuUtilization.utilizationTarget)}</dd>
          </>
        )}
        {GCEProviderSettings.feature.predictiveAutoscaling && policy.cpuUtilization?.predictiveMethod !== undefined && (
          <>
            <dt>Predictive Autoscaling</dt>
            <dd>{policy.cpuUtilization.predictiveMethod}</dd>
          </>
        )}
        {policy.loadBalancingUtilization && (
          <>
            <dt>Target HTTP Load Balancing Usage</dt>
            <dd>{percentage(policy.loadBalancingUtilization.utilizationTarget)}</dd>
          </>
        )}
        {(policy.customMetricUtilizations || []).map((metric, index) => (
          <React.Fragment key={`${metric.metric}-${index}`}>
            <dt>Monitoring Metric</dt>
            <dd>
              {metric.metric || '-'}: {metric.utilizationTarget ?? metric.singleInstanceAssignment ?? '-'}
              {metric.utilizationTargetType === 'DELTA_PER_SECOND'
                ? '/sec'
                : metric.utilizationTargetType === 'DELTA_PER_MINUTE'
                ? '/min'
                : ''}
            </dd>
          </React.Fragment>
        ))}
        <dt>Min # VMs</dt>
        <dd>{policy.minNumReplicas ?? '-'}</dd>
        <dt>Max # VMs</dt>
        <dd>{policy.maxNumReplicas ?? '-'}</dd>
        <dt>Cool-down Period</dt>
        <dd>{policy.coolDownPeriodSec ?? '-'} sec</dd>
        <dt>Mode</dt>
        <dd>{policy.mode || '-'}</dd>
        {policy.scaleInControl && (
          <>
            <dt>Max Scaled-in Replicas</dt>
            <dd>{maxScaledIn ?? '-'}</dd>
            <dt>Scale-in Time Window</dt>
            <dd>{policy.scaleInControl.timeWindowSec ?? '-'} seconds</dd>
          </>
        )}
        {(policy.scalingSchedules || []).map((schedule, index) => (
          <React.Fragment key={`${schedule.scheduleName}-${index}`}>
            <dt>Scaling Schedule</dt>
            <dd>
              {schedule.scheduleName || '-'} ({schedule.enabled ? 'enabled' : 'disabled'})
              {schedule.timezone ? `, ${schedule.timezone}` : ''}
            </dd>
          </React.Fragment>
        ))}
        {(serverGroup.autoscalingMessages || []).map((message, index) => (
          <React.Fragment key={index}>
            <dt>Message</dt>
            <dd>{message}</dd>
          </React.Fragment>
        ))}
      </dl>
      {mutationsEnabled && (
        <div className="text-right">
          <button
            className="btn btn-link"
            data-testid="edit-autoscaling-policy"
            type="button"
            onClick={() =>
              runIfNotManaged(() => GceUpsertAutoscalingPolicyModal.show({ application, serverGroup, policy }))
            }
          >
            Edit
          </button>
          <button
            className="btn btn-link"
            data-testid="delete-autoscaling-policy"
            type="button"
            onClick={() => runIfNotManaged(deletePolicy)}
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
