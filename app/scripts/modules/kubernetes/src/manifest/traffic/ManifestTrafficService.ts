import { Application, IManifest, ITask, TaskExecutor } from '@spinnaker/core';

import { IKubernetesServerGroup } from '../../interfaces';

export class ManifestTrafficService {
  public static readonly ENABLE_MANIFEST_OPERATION = 'enableManifest';
  public static readonly DISABLE_MANIFEST_OPERATION = 'disableManifest';

  public static enable = (manifest: IManifest, application: Application, reason?: string): PromiseLike<ITask> => {
    return TaskExecutor.executeTask({
      job: [
        {
          account: manifest.account,
          cloudProvider: 'kubernetes',
          location: manifest.location,
          manifestName: manifest.name,
          type: ManifestTrafficService.ENABLE_MANIFEST_OPERATION,
          reason,
        },
      ],
      application,
      description: `Enable Manifest: ${manifest.name}`,
    });
  };

  public static disable = (manifest: IManifest, application: Application, reason?: string): PromiseLike<ITask> => {
    return TaskExecutor.executeTask({
      job: [
        {
          account: manifest.account,
          cloudProvider: 'kubernetes',
          location: manifest.location,
          manifestName: manifest.name,
          type: ManifestTrafficService.DISABLE_MANIFEST_OPERATION,
          reason,
        },
      ],
      application,
      description: `Disable Manifest: ${manifest.name}`,
    });
  };

  public static canDisableServerGroup = (serverGroup: IKubernetesServerGroup): boolean => {
    return (
      ManifestTrafficService.hasLoadBalancers(serverGroup) &&
      !ManifestTrafficService.isManaged(serverGroup) &&
      !serverGroup.disabled
    );
  };

  public static canEnableServerGroup = (serverGroup: IKubernetesServerGroup): boolean => {
    return (
      ManifestTrafficService.hasLoadBalancers(serverGroup) &&
      !ManifestTrafficService.isManaged(serverGroup) &&
      serverGroup.disabled
    );
  };

  private static hasLoadBalancers = (serverGroup: IKubernetesServerGroup): boolean => {
    return serverGroup.loadBalancers?.length > 0;
  };

  private static isManaged = (serverGroup: IKubernetesServerGroup): boolean => {
    return serverGroup.serverGroupManagers && serverGroup.serverGroupManagers.length > 0;
  };
}
