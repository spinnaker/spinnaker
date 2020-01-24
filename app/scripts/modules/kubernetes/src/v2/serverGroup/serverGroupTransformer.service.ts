import { IPromise, IQService, module } from 'angular';
import { IKubernetesServerGroup } from './details/IKubernetesServerGroup';

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

export const KUBERNETES_V2_SERVER_GROUP_TRANSFORMER = 'spinnaker.kubernetes.v2.serverGroup.transformer.service';
module(KUBERNETES_V2_SERVER_GROUP_TRANSFORMER, []).service(
  'kubernetesV2ServerGroupTransformer',
  KubernetesV2ServerGroupTransformer,
);
