import { IScope, module } from 'angular';

import { Application, IManifest, IManifestStatus, MANIFEST_READER, ManifestReader } from '@spinnaker/core';

export interface IStatusContainer {
  status: IManifestStatus,
  state: any,
}

export interface IStatusParams {
  account: string,
  location: string,
  name: string,
}

export class KubernetesManifestStatusService {
  constructor(private manifestReader: ManifestReader) {
    'ngInject';
  }

  public makeStatusRefresher(app: Application, $scope: IScope, params: IStatusParams, container: IStatusContainer) {
    this.updateStatus(params, container);
    app.onRefresh($scope, () => this.updateStatus(params, container));
  }

  private updateStatus(params: IStatusParams, container: IStatusContainer) {
    this.manifestReader.getManifest(params.account, params.location, params.name)
      .then((manifest: IManifest) => container.status = manifest.status || container.status);
  }
}

export const KUBERNETES_MANIFEST_STATUS_SERVICE = 'spinnaker.kubernetes.v2.kubernetes.manifest.status.service';

module(KUBERNETES_MANIFEST_STATUS_SERVICE, [
  MANIFEST_READER,
]).service('kubernetesManifestStatusService', KubernetesManifestStatusService);
