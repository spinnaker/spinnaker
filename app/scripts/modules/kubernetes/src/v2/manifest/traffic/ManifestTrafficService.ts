import { IPromise } from 'angular';
import { get } from 'lodash';

import { IManifest, ITask, TaskExecutor, Application } from '@spinnaker/core';
import { IKubernetesServerGroup } from 'kubernetes/v2';

export class ManifestTrafficService {
  public static readonly ENABLE_MANIFEST_OPERATION = 'enableManifest';
  public static readonly DISABLE_MANIFEST_OPERATION = 'disableManifest';
  public static readonly TRAFFIC_ANNOTATION = 'traffic.spinnaker.io/load-balancers';

  public static enable = (manifest: IManifest, application: Application, reason?: string): IPromise<ITask> => {
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

  public static disable = (manifest: IManifest, application: Application, reason?: string): IPromise<ITask> => {
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
      ManifestTrafficService.hasLoadBalancers(serverGroup.manifest) &&
      !ManifestTrafficService.isManaged(serverGroup) &&
      !serverGroup.disabled
    );
  };

  public static canEnableServerGroup = (serverGroup: IKubernetesServerGroup): boolean => {
    return (
      ManifestTrafficService.hasLoadBalancers(serverGroup.manifest) &&
      !ManifestTrafficService.isManaged(serverGroup) &&
      serverGroup.disabled
    );
  };

  private static hasLoadBalancers = (manifest: IManifest): boolean => {
    const loadBalancers = JSON.parse(
      get(manifest, ['metadata', 'annotations', ManifestTrafficService.TRAFFIC_ANNOTATION], '[]'),
    );
    return loadBalancers && loadBalancers.length > 0;
  };

  private static isManaged = (serverGroup: IKubernetesServerGroup): boolean => {
    return serverGroup.serverGroupManagers && serverGroup.serverGroupManagers.length > 0;
  };
}
