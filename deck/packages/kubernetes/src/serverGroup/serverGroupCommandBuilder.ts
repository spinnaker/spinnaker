import type { Application } from '@spinnaker/core';
import type { IKubernetesManifestCommandData } from '../manifest/manifestCommandBuilder.service';
import { KubernetesManifestCommandBuilder } from '../manifest/manifestCommandBuilder.service';

export class KubernetesV2ServerGroupCommandBuilder {
  public buildNewServerGroupCommand(app: Application): PromiseLike<IKubernetesManifestCommandData> {
    return KubernetesManifestCommandBuilder.buildNewManifestCommand(app);
  }
}
