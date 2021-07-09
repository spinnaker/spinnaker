import { Application } from '../application/application.model';
import { IInstance, IServerGroup, ITask } from '../domain';
import { ReactInjector } from '../reactShims';
import { ServerGroupReader } from '../serverGroup/serverGroupReader.service';
import { IJob, TaskExecutor } from '../task/taskExecutor';

export interface IMultiInstanceGroup {
  account: string;
  cloudProvider: string;
  region: string;
  serverGroup: string;
  instanceIds: string[];
  loadBalancers?: string[];
  instances: IInstance[];
  selectAll?: boolean;
}

export interface IMultiInstanceJob {
  type: string;
  cloudProvider: string;
  instanceIds: string[];
  credentials: string;
  region: string;
  serverGroupName: string;
  asgName?: string; // still needed on backend for some operations
  loadBalancerNames?: string[];
}

export class InstanceWriter {
  public static terminateInstance(
    instance: IInstance,
    application: Application,
    params: IJob = {},
  ): PromiseLike<ITask> {
    params.type = 'terminateInstances';
    params['instanceIds'] = [instance.id];
    params['region'] = instance.region;
    params['zone'] = instance.zone;
    params['credentials'] = instance.account;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Terminate instance: ${instance.id}`,
    });
  }

  public static terminateInstances(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
  ): PromiseLike<ITask> {
    return InstanceWriter.executeMultiInstanceTask(instanceGroups, application, 'terminateInstances', 'Terminate');
  }

  private static executeMultiInstanceTask(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    type: string,
    baseDescriptor: string,
    descriptorSuffix?: string,
    additionalJobProperties: any = {},
  ): PromiseLike<ITask> {
    const jobs = InstanceWriter.buildMultiInstanceJob(instanceGroups, type, additionalJobProperties);
    const descriptor = InstanceWriter.buildMultiInstanceDescriptor(jobs, baseDescriptor, descriptorSuffix);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public static rebootInstances(instanceGroups: IMultiInstanceGroup[], application: Application): PromiseLike<ITask> {
    return InstanceWriter.executeMultiInstanceTask(instanceGroups, application, 'rebootInstances', 'Reboot');
  }

  public static rebootInstance(instance: IInstance, application: Application, params: any = {}): PromiseLike<ITask> {
    params.type = 'rebootInstances';
    params.instanceIds = [instance.id];
    params.region = instance.region;
    params.zone = instance.zone;
    params.credentials = instance.account;
    params.cloudProvider = instance.cloudProvider;
    params.application = application.name;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Reboot instance: ${instance.id}`,
    });
  }

  public static deregisterInstancesFromLoadBalancer(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    loadBalancerNames: string[],
  ): PromiseLike<ITask> {
    const jobs = InstanceWriter.buildMultiInstanceJob(instanceGroups, 'deregisterInstancesFromLoadBalancer');
    jobs.forEach((job) => (job.loadBalancerNames = loadBalancerNames));
    const descriptor = InstanceWriter.buildMultiInstanceDescriptor(
      jobs,
      'Deregister',
      `from ${loadBalancerNames.join(' and ')}`,
    );
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public static deregisterInstanceFromLoadBalancer(
    instance: IInstance,
    application: Application,
    params: any = {},
  ): PromiseLike<ITask> {
    params.type = 'deregisterInstancesFromLoadBalancer';
    params.instanceIds = [instance.id];
    params.loadBalancerNames = instance.loadBalancers;
    params.region = instance.region;
    params.credentials = instance.account;
    params.cloudProvider = instance.cloudProvider;
    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Deregister instance: ${instance.id}`,
    });
  }

  public static registerInstancesWithLoadBalancer(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
    loadBalancerNames: string[],
  ): PromiseLike<ITask> {
    const jobs = InstanceWriter.buildMultiInstanceJob(instanceGroups, 'registerInstancesWithLoadBalancer');
    jobs.forEach((job) => (job.loadBalancerNames = loadBalancerNames));
    const descriptor = InstanceWriter.buildMultiInstanceDescriptor(
      jobs,
      'Register',
      `with ${loadBalancerNames.join(' and ')}`,
    );
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: descriptor,
    });
  }

  public static registerInstanceWithLoadBalancer(
    instance: IInstance,
    application: Application,
    params: any = {},
  ): PromiseLike<ITask> {
    params.type = 'registerInstancesWithLoadBalancer';
    params.instanceIds = [instance.id];
    params.loadBalancerNames = instance.loadBalancers;
    params.region = instance.region;
    params.credentials = instance.account;
    params.cloudProvider = instance.cloudProvider;
    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Register instance: ${instance.id}`,
    });
  }

  public static enableInstancesInDiscovery(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
  ): PromiseLike<ITask> {
    return InstanceWriter.executeMultiInstanceTask(
      instanceGroups,
      application,
      'enableInstancesInDiscovery',
      'Enable',
      'in discovery',
    );
  }

  public static enableInstanceInDiscovery(instance: IInstance, application: Application): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      job: [
        {
          type: 'enableInstancesInDiscovery',
          instanceIds: [instance.id],
          region: instance.region,
          credentials: instance.account,
          cloudProvider: instance.cloudProvider,
          serverGroupName: instance.serverGroup,
          asgName: instance.serverGroup,
        },
      ],
      application,
      description: `Enable instance: ${instance.id}`,
    });
  }

  public static disableInstancesInDiscovery(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
  ): PromiseLike<ITask> {
    return InstanceWriter.executeMultiInstanceTask(
      instanceGroups,
      application,
      'disableInstancesInDiscovery',
      'Disable',
      'in discovery',
    );
  }

  public static disableInstanceInDiscovery(instance: IInstance, application: Application): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      job: [
        {
          type: 'disableInstancesInDiscovery',
          instanceIds: [instance.id],
          region: instance.region,
          credentials: instance.account,
          cloudProvider: instance.cloudProvider,
          serverGroupName: instance.serverGroup,
          asgName: instance.serverGroup,
        },
      ],
      application,
      description: `Disable instance: ${instance.id}`,
    });
  }

  public static terminateInstancesAndShrinkServerGroups(
    instanceGroups: IMultiInstanceGroup[],
    application: Application,
  ): PromiseLike<ITask> {
    return InstanceWriter.executeMultiInstanceTask(
      instanceGroups,
      application,
      'detachInstances',
      'Terminate',
      'and shrink server groups',
      {
        terminateDetachedInstances: true,
        decrementDesiredCapacity: true,
        adjustMinIfNecessary: true,
      },
    );
  }

  public static terminateInstanceAndShrinkServerGroup(
    instance: IInstance,
    application: Application,
    params: any = {},
  ): PromiseLike<ITask> {
    return ServerGroupReader.getServerGroup(
      application.name,
      instance.account,
      instance.region,
      instance.serverGroup,
    ).then((serverGroup: IServerGroup) => {
      params.type = 'terminateInstanceAndDecrementServerGroup';
      params.instance = instance.id;
      params.serverGroupName = instance.serverGroup;
      params.asgName = instance.serverGroup; // still needed on the backend
      params.region = instance.region;
      params.credentials = instance.account;
      params.cloudProvider = instance.cloudProvider;
      params.adjustMinIfNecessary = true;
      params.setMaxToNewDesired = serverGroup.asg.minSize === serverGroup.asg.maxSize;

      return TaskExecutor.executeTask({
        job: [params],
        application,
        description: `Terminate instance ${instance.id} and shrink ${instance.serverGroup}`,
      });
    });
  }

  protected static buildMultiInstanceJob(
    instanceGroups: IMultiInstanceGroup[],
    type: string,
    additionalJobProperties = {},
  ) {
    return instanceGroups
      .filter((instanceGroup) => instanceGroup.instances.length > 0)
      .map((instanceGroup) => InstanceWriter.convertGroupToJob(instanceGroup, type, additionalJobProperties));
  }

  protected static buildMultiInstanceDescriptor(jobs: IMultiInstanceJob[], base: string, suffix: string): string {
    let totalInstances = 0;
    jobs.forEach((job: IMultiInstanceJob) => (totalInstances += job.instanceIds.length));
    let descriptor = `${base} ${totalInstances} instance`;
    if (totalInstances > 1) {
      descriptor += 's';
    }
    if (suffix) {
      descriptor += ' ' + suffix;
    }
    return descriptor;
  }

  private static convertGroupToJob(
    instanceGroup: IMultiInstanceGroup,
    type: string,
    additionalJobProperties: any = {},
  ): IMultiInstanceJob {
    const job: IMultiInstanceJob = {
      type,
      cloudProvider: instanceGroup.cloudProvider,
      instanceIds: instanceGroup.instanceIds,
      credentials: instanceGroup.account,
      region: instanceGroup.region,
      serverGroupName: instanceGroup.serverGroup,
      asgName: instanceGroup.serverGroup,
    };

    Object.assign(job, additionalJobProperties);

    InstanceWriter.transform(instanceGroup, job);

    return job;
  }

  private static transform(instanceGroup: IMultiInstanceGroup, job: IMultiInstanceJob) {
    const serviceKey = 'instance.multiInstanceTaskTransformer';
    const { providerServiceDelegate } = ReactInjector;
    if (providerServiceDelegate.hasDelegate(instanceGroup.cloudProvider, serviceKey)) {
      const transformer: any = providerServiceDelegate.getDelegate(instanceGroup.cloudProvider, serviceKey);
      transformer.transform(instanceGroup, job);
    }
  }
}
