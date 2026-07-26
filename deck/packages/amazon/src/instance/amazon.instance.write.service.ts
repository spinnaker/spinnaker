import type {
  Application,
  IMultiInstanceGroup,
  IMultiInstanceJob,
  ITask,
  ProviderServiceDelegate,
} from '@spinnaker/core';
import { InstanceWriter, TaskExecutor } from '@spinnaker/core';

import type { IAmazonInstance } from '../domain';

export interface IAmazonMultiInstanceGroup extends IMultiInstanceGroup {
  targetGroups: string[];
}

export interface IAmazonMultiInstanceJob extends IMultiInstanceJob {
  targetGroupNames?: string[];
}

export class AmazonInstanceWriter extends InstanceWriter {
  public static deregisterInstancesFromTargetGroup(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    targetGroupNames: string[],
    providerServiceDelegate: ProviderServiceDelegate,
  ): Promise<ITask> {
    const jobs = super.buildMultiInstanceJob(
      instanceGroups,
      providerServiceDelegate,
      'deregisterInstancesFromLoadBalancer',
    ) as IAmazonMultiInstanceJob[];
    jobs.forEach((job) => (job.targetGroupNames = targetGroupNames));
    const descriptor = super.buildMultiInstanceDescriptor(jobs, 'Deregister', `from ${targetGroupNames.join(' and ')}`);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public static deregisterInstanceFromTargetGroup(
    instance: IAmazonInstance,
    application: Application,
    params: any = {},
  ): Promise<ITask> {
    params.type = 'deregisterInstancesFromLoadBalancer';
    params.instanceIds = [instance.id];
    params.targetGroupNames = instance.targetGroups;
    params.region = instance.region;
    params.credentials = instance.account;
    params.cloudProvider = instance.cloudProvider;
    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Deregister instance: ${instance.id}`,
    });
  }

  public static registerInstancesWithTargetGroup(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    targetGroupNames: string[],
    providerServiceDelegate: ProviderServiceDelegate,
  ) {
    const jobs = super.buildMultiInstanceJob(
      instanceGroups,
      providerServiceDelegate,
      'registerInstancesWithLoadBalancer',
    ) as IAmazonMultiInstanceJob[];
    jobs.forEach((job) => (job.targetGroupNames = targetGroupNames));
    const descriptor = super.buildMultiInstanceDescriptor(jobs, 'Register', `with ${targetGroupNames.join(' and ')}`);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public static registerInstanceWithTargetGroup(
    instance: IAmazonInstance,
    application: Application,
    params: any = {},
  ): Promise<ITask> {
    params.type = 'registerInstancesWithLoadBalancer';
    params.instanceIds = [instance.id];
    params.targetGroupNames = instance.targetGroups;
    params.region = instance.region;
    params.credentials = instance.account;
    params.cloudProvider = instance.cloudProvider;
    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Register instance: ${instance.id}`,
    });
  }
}
