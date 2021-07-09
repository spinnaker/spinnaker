import { module } from 'angular';

import { Application } from '@spinnaker/core';
import {
  IKubernetesManifestCommandData,
  KubernetesManifestCommandBuilder,
} from '../manifest/manifestCommandBuilder.service';

export class KubernetesV2ServerGroupCommandBuilder {
  public buildNewServerGroupCommand(app: Application): PromiseLike<IKubernetesManifestCommandData> {
    return KubernetesManifestCommandBuilder.buildNewManifestCommand(app);
  }
}

export const KUBERNETES_SERVER_GROUP_COMMAND_BUILDER = 'spinnaker.kubernetes.serverGroup.commandBuilder.service';

module(KUBERNETES_SERVER_GROUP_COMMAND_BUILDER, []).service(
  'kubernetesV2ServerGroupCommandBuilder',
  KubernetesV2ServerGroupCommandBuilder,
);
