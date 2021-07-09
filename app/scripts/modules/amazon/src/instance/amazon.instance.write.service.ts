import {
  Application,
  IMultiInstanceGroup,
  IMultiInstanceJob,
  InstanceWriter,
  ITask,
  TaskExecutor,
} from '@spinnaker/core';
import { IAmazonInstance } from '../domain';

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
  ): PromiseLike<ITask> {
    const jobs = super.buildMultiInstanceJob(
      instanceGroups,
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
  ): PromiseLike<ITask> {
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
  ) {
    const jobs = super.buildMultiInstanceJob(
      instanceGroups,
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
  ): PromiseLike<ITask> {
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
