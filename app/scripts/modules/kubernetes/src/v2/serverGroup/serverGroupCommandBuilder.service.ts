import { module, IPromise } from 'angular';

import { Application } from '@spinnaker/core';
import {
  IKubernetesManifestCommandData,
  KubernetesManifestCommandBuilder,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export class KubernetesV2ServerGroupCommandBuilder {
  public buildNewServerGroupCommand(app: Application): IPromise<IKubernetesManifestCommandData> {
    return KubernetesManifestCommandBuilder.buildNewManifestCommand(app);
  }
}

export const KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER = 'spinnaker.kubernetes.v2.serverGroup.commandBuilder.service';

module(KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER, []).service(
  'kubernetesV2ServerGroupCommandBuilder',
  KubernetesV2ServerGroupCommandBuilder,
);
