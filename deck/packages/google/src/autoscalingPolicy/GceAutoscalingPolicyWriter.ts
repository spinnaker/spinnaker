import type { Application, IJob, ITask } from '@spinnaker/core';
import { TaskExecutor } from '@spinnaker/core';

import type { IGceAutoscalingPolicy } from './IGceAutoscalingPolicy';
import type { IGceAutoHealingPolicy } from '../domain';

interface IGcePolicyServerGroupIdentity {
  account: string;
  name: string;
}

export type IGcePolicyServerGroup = IGcePolicyServerGroupIdentity &
  ({ region: string; zone?: string } | { region?: undefined; zone: string });

export interface IGcePolicyTaskParams {
  interestingHealthProviderNames?: string[];
  reason?: string;
}

interface IGcePolicyJob extends IJob {
  cloudProvider: 'gce';
  credentials: string;
  region: string;
  serverGroupName: string;
  type: 'upsertScalingPolicy' | 'deleteScalingPolicy';
  autoscalingPolicy?: IGceAutoscalingPolicy;
  autoHealingPolicy?: IGceAutoHealingPolicy;
  deleteAutoHealingPolicy?: true;
  interestingHealthProviderNames?: string[];
  reason?: string;
}

function policyJob(serverGroup: IGcePolicyServerGroup, type: IGcePolicyJob['type']): IGcePolicyJob {
  return {
    type,
    cloudProvider: 'gce',
    credentials: serverGroup.account,
    region: serverGroup.region ?? serverGroup.zone,
    serverGroupName: serverGroup.name,
  };
}

function supportedAutoHealingPolicy(policy: IGceAutoHealingPolicy): IGceAutoHealingPolicy {
  // This is the final task boundary: legacy server responses can still carry fields that
  // stable Compute v1 no longer accepts, so assemble the outbound contract explicitly.
  const supported: IGceAutoHealingPolicy = {};
  (['healthCheck', 'healthCheckKind', 'healthCheckUrl', 'initialDelaySec'] as const).forEach((field) => {
    if (policy[field] !== undefined) {
      (supported as any)[field] = policy[field];
    }
  });
  return supported;
}

export class GceAutoscalingPolicyWriter {
  public static upsertAutoscalingPolicy(
    application: Application,
    serverGroup: IGcePolicyServerGroup,
    autoscalingPolicy: IGceAutoscalingPolicy,
    params: IGcePolicyTaskParams = {},
  ): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      application,
      description: `Upsert scaling policy ${serverGroup.name}`,
      job: [{ ...policyJob(serverGroup, 'upsertScalingPolicy'), autoscalingPolicy, ...params }],
    });
  }

  public static deleteAutoscalingPolicy(
    application: Application,
    serverGroup: IGcePolicyServerGroup,
  ): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      application,
      description: `Delete scaling policy ${serverGroup.name}`,
      job: [policyJob(serverGroup, 'deleteScalingPolicy')],
    });
  }

  public static upsertAutoHealingPolicy(
    application: Application,
    serverGroup: IGcePolicyServerGroup,
    autoHealingPolicy: IGceAutoHealingPolicy,
  ): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      application,
      description: `Upsert autohealing policy ${serverGroup.name}`,
      job: [
        {
          ...policyJob(serverGroup, 'upsertScalingPolicy'),
          autoHealingPolicy: supportedAutoHealingPolicy(autoHealingPolicy),
        },
      ],
    });
  }

  public static deleteAutoHealingPolicy(
    application: Application,
    serverGroup: IGcePolicyServerGroup,
  ): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      application,
      description: `Delete autohealing policy ${serverGroup.name}`,
      job: [{ ...policyJob(serverGroup, 'deleteScalingPolicy'), deleteAutoHealingPolicy: true }],
    });
  }
}
