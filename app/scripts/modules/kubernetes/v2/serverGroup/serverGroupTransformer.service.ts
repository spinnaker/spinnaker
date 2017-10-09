import { IQService, module } from 'angular';

export class KubernetesV2ServerGroupTransformer {
  constructor(private $q: IQService) {
    'ngInject';
  }

  public normalizeServerGroup(serverGroup: any) {
    return this.$q.when(serverGroup);
  }
}

export const KUBERNETES_V2_SERVER_GROUP_TRANSFORMER = 'spinnaker.kubernetes.v2.serverGroup.transformer.service';

module(KUBERNETES_V2_SERVER_GROUP_TRANSFORMER, [
]).service('kubernetesV2ServerGroupTransformer', KubernetesV2ServerGroupTransformer);
