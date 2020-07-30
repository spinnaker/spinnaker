import { IPromise, IQService, module } from 'angular';
import { IKubernetesServerGroup } from '../interfaces';

export class KubernetesV2ServerGroupTransformer {
  public static $inject = ['$q'];
  constructor(private $q: IQService) {}

  public normalizeServerGroup(serverGroup: IKubernetesServerGroup): IPromise<IKubernetesServerGroup> {
    // TODO(dpeach): this isn't great, but we need to assume it's a deployment so that we can click
    // into the details panel.
    (serverGroup.serverGroupManagers || []).forEach(manager => (manager.name = `deployment ${manager.name}`));
    return this.$q.when(serverGroup);
  }
}

export const KUBERNETES_SERVER_GROUP_TRANSFORMER = 'spinnaker.kubernetes.serverGroup.transformer.service';
module(KUBERNETES_SERVER_GROUP_TRANSFORMER, []).service(
  'kubernetesV2ServerGroupTransformer',
  KubernetesV2ServerGroupTransformer,
);
