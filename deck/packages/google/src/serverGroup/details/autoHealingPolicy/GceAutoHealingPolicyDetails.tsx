import React from 'react';

import type { Application, IManagedResource } from '@spinnaker/core';
import { ConfirmationModalService, confirmNotManaged } from '@spinnaker/core';

import type { IGcePolicyServerGroup } from '../../../autoscalingPolicy';
import { GceAutoscalingPolicyWriter } from '../../../autoscalingPolicy';
import type { IGceAutoHealingPolicy } from '../../../domain';
import { GceUpsertAutoHealingPolicyModal } from './modal/GceUpsertAutoHealingPolicyModal';

export interface IGceAutoHealingPolicyDetailsProps {
  application: Application;
  mutationsEnabled: boolean;
  serverGroup: IGcePolicyServerGroup & IManagedResource;
  policy?: IGceAutoHealingPolicy;
}

export function GceAutoHealingPolicyDetails({
  application,
  mutationsEnabled,
  serverGroup,
  policy,
}: IGceAutoHealingPolicyDetailsProps): JSX.Element | null {
  const runIfNotManaged = (action: () => void): void => {
    confirmNotManaged(serverGroup, application).then((notManaged) => notManaged && action());
  };

  if (!policy) {
    return mutationsEnabled ? (
      <button
        className="btn btn-link"
        data-testid="add-auto-healing-policy"
        type="button"
        onClick={() => runIfNotManaged(() => GceUpsertAutoHealingPolicyModal.show({ application, serverGroup }))}
      >
        Add auto-healing policy
      </button>
    ) : null;
  }

  const maxUnavailable =
    typeof policy.maxUnavailable?.percent === 'number'
      ? `${policy.maxUnavailable.percent}%`
      : typeof policy.maxUnavailable?.fixed === 'number'
      ? `${policy.maxUnavailable.fixed} fixed`
      : '-';
  const deletePolicy = (): void => {
    ConfirmationModalService.confirm({
      header: `Really delete autohealer for ${serverGroup.name}?`,
      buttonText: 'Delete autohealer',
      account: serverGroup.account,
      taskMonitorConfig: { application, title: `Deleting autohealing policy for ${serverGroup.name}` },
      submitMethod: () => GceAutoscalingPolicyWriter.deleteAutoHealingPolicy(application, serverGroup),
    });
  };

  return (
    <div className="gce-auto-healing-policy-details">
      <dl className="horizontal-when-filters-collapsed">
        <dt>Health Check</dt>
        <dd>{policy.healthCheck || policy.healthCheckUrl || '-'}</dd>
        <dt>Initial Delay</dt>
        <dd>{policy.initialDelaySec ?? '-'} seconds</dd>
        <dt>Max Unavailable</dt>
        <dd>{maxUnavailable}</dd>
      </dl>
      {mutationsEnabled && (
        <div className="text-right">
          <button
            className="btn btn-link"
            data-testid="edit-auto-healing-policy"
            type="button"
            onClick={() =>
              runIfNotManaged(() => GceUpsertAutoHealingPolicyModal.show({ application, serverGroup, policy }))
            }
          >
            Edit
          </button>
          <button
            className="btn btn-link"
            data-testid="delete-auto-healing-policy"
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
