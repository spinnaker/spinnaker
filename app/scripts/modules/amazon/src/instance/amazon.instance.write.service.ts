import { IPromise, module } from 'angular';

import {
  Application,
  IMultiInstanceGroup,
  IMultiInstanceJob,
  INSTANCE_WRITE_SERVICE,
  InstanceWriter,
  ITask,
  PROVIDER_SERVICE_DELEGATE,
  ProviderServiceDelegate,
  TaskExecutor,
} from '@spinnaker/core';

import { IAmazonInstance } from 'amazon/domain';

export interface IAmazonMultiInstanceGroup extends IMultiInstanceGroup {
  targetGroups: string[];
}

export interface IAmazonMultiInstanceJob extends IMultiInstanceJob {
  targetGroupNames?: string[];
}

export class AmazonInstanceWriter extends InstanceWriter {
  public static $inject = ['providerServiceDelegate'];
  public constructor(protected providerServiceDelegate: ProviderServiceDelegate) {
    'ngInject';
    super(providerServiceDelegate);
  }

  public deregisterInstancesFromTargetGroup(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    targetGroupNames: string[],
  ): IPromise<ITask> {
    const jobs = this.buildMultiInstanceJob(
      instanceGroups,
      'deregisterInstancesFromLoadBalancer',
    ) as IAmazonMultiInstanceJob[];
    jobs.forEach(job => (job.targetGroupNames = targetGroupNames));
    const descriptor = this.buildMultiInstanceDescriptor(jobs, 'Deregister', `from ${targetGroupNames.join(' and ')}`);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public deregisterInstanceFromTargetGroup(
    instance: IAmazonInstance,
    application: Application,
    params: any = {},
  ): IPromise<ITask> {
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

  public registerInstancesWithTargetGroup(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    targetGroupNames: string[],
  ) {
    const jobs = this.buildMultiInstanceJob(
      instanceGroups,
      'registerInstancesWithLoadBalancer',
    ) as IAmazonMultiInstanceJob[];
    jobs.forEach(job => (job.targetGroupNames = targetGroupNames));
    const descriptor = this.buildMultiInstanceDescriptor(jobs, 'Register', `with ${targetGroupNames.join(' and ')}`);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public registerInstanceWithTargetGroup(
    instance: IAmazonInstance,
    application: Application,
    params: any = {},
  ): IPromise<ITask> {
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

export const AMAZON_INSTANCE_WRITE_SERVICE = 'spinnaker.amazon.instance.write.service';
module(AMAZON_INSTANCE_WRITE_SERVICE, [INSTANCE_WRITE_SERVICE, PROVIDER_SERVICE_DELEGATE]).service(
  'amazonInstanceWriter',
  AmazonInstanceWriter,
);
