import { IPromise, module } from 'angular';

import { Application } from '@spinnaker/core';
import {
  IKubernetesManifestCommandData,
  KUBERNETES_MANIFEST_COMMAND_BUILDER,
  KubernetesManifestCommandBuilder
} from '../manifest/manifestCommandBuilder.service';

export class KubernetesV2ServerGroupCommandBuilder {
  constructor(private kubernetesManifestCommandBuilder: KubernetesManifestCommandBuilder) {
    'ngInject';
  }

  public buildNewServerGroupCommand(app: Application): IPromise<IKubernetesManifestCommandData> {
    return this.kubernetesManifestCommandBuilder.buildNewManifestCommand(app);
  }
}

export const KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER = 'spinnaker.kubernetes.v2.serverGroup.commandBuilder.service';

module(KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER, [
  KUBERNETES_MANIFEST_COMMAND_BUILDER,
]).service('kubernetesV2ServerGroupCommandBuilder', KubernetesV2ServerGroupCommandBuilder);
