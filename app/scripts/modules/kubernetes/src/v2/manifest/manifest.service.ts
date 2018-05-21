import { Application, IManifest, ManifestReader } from '@spinnaker/core';

export interface IManifestContainer {
  manifest: IManifest;
}

export interface IManifestParams {
  account: string;
  location: string;
  name: string;
}

export class KubernetesManifestService {
  public static makeManifestRefresher(
    app: Application,
    params: IManifestParams,
    container: IManifestContainer,
  ): () => void {
    KubernetesManifestService.updateManifest(params, container);
    return app.onRefresh(null, () => KubernetesManifestService.updateManifest(params, container));
  }

  private static updateManifest(params: IManifestParams, container: IManifestContainer) {
    ManifestReader.getManifest(params.account, params.location, params.name).then(
      (manifest: IManifest) => (container.manifest = manifest || container.manifest),
    );
  }
}
