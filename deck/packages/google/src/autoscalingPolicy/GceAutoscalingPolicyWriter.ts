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
      job: [{ ...policyJob(serverGroup, 'upsertScalingPolicy'), autoHealingPolicy }],
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
