import type { Application, IInstance, IServerGroup, ITask } from '@spinnaker/core';
import { InstanceWriter, ServerGroupReader, TaskExecutor } from '@spinnaker/core';

const CLOUD_PROVIDER = 'azure';

/**
 * Azure cannot use core's InstanceWriter directly: core omits cloudProvider (orca then
 * defaults to aws), omits serverGroupName (clouddriver cannot locate the scale set, and
 * Azure instances are not searchable so orca's fallback silently reports success), and
 * reads shrink parameters off serverGroup.asg, which Azure server groups do not have.
 */
export class AzureInstanceWriter extends InstanceWriter {
  public static terminateInstance(instance: IInstance, application: Application, params: any = {}): PromiseLike<ITask> {
    params.type = 'terminateInstances';
    params.instanceIds = [instance.id];
    params.serverGroupName = instance.serverGroup;
    params.region = instance.region;
    params.zone = instance.zone;
    params.credentials = instance.account;
    params.cloudProvider = CLOUD_PROVIDER;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Terminate instance: ${instance.id}`,
    });
  }

  public static rebootInstance(instance: IInstance, application: Application, params: any = {}): PromiseLike<ITask> {
    params.type = 'rebootInstances';
    params.instanceIds = [instance.id];
    params.serverGroupName = instance.serverGroup;
    params.region = instance.region;
    params.zone = instance.zone;
    params.credentials = instance.account;
    params.cloudProvider = CLOUD_PROVIDER;
    params.application = application.name;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Reboot instance: ${instance.id}`,
    });
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
      const capacity = serverGroup.capacity || ({} as any);

      params.type = 'terminateInstanceAndDecrementServerGroup';
      params.instance = instance.id;
      params.serverGroupName = instance.serverGroup;
      params.asgName = instance.serverGroup; // still needed on the backend
      params.region = instance.region;
      params.credentials = instance.account;
      params.cloudProvider = CLOUD_PROVIDER;
      params.adjustMinIfNecessary = true;
      params.setMaxToNewDesired = capacity.min === capacity.max;

      return TaskExecutor.executeTask({
        job: [params],
        application,
        description: `Terminate instance ${instance.id} and shrink ${instance.serverGroup}`,
      });
    });
  }
}
