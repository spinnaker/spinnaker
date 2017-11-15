import { IController, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { Application, IServerGroupManager, IServerGroupManagerStateParams } from '@spinnaker/core';
import { IKubernetesServerGroupManager } from '../IKubernetesServerGroupManager';

class KubernetesServerGroupManagerDetailsController implements IController {
  public serverGroupManager: IServerGroupManager;
  public state = { loading: true };

  constructor(serverGroupManager: IServerGroupManagerStateParams,
              private $uibModal: IModalService,
              public app: Application) {
    'ngInject';
    this.app.ready()
      .then(() => {
        this.extractServerGroupManager(serverGroupManager);
        this.state.loading = false;
      });
  }

  public editServerGroupManager(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/wizard/manifestWizard.html'),
      size: 'lg',
      controller: 'kubernetesV2ManifestEditCtrl',
      controllerAs: 'ctrl',
      resolve: {
        sourceManifest: this.serverGroupManager.manifest,
        sourceMoniker: this.serverGroupManager.moniker,
        application: this.app
      }
    });
  }

  public deleteServerGroupManager(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroupManager.name,
          namespace: this.serverGroupManager.namespace,
          account: this.serverGroupManager.account
        },
        application: this.app
      }
    });
  }

  private transformServerGroupManager(serverGroupManagerDetails: IServerGroupManager): IKubernetesServerGroupManager {
    if (!serverGroupManagerDetails) {
      return null;
    }

    const serverGroupManager = serverGroupManagerDetails as IKubernetesServerGroupManager;
    const [kind, name] = serverGroupManager.name.split(' ');
    serverGroupManager.displayName = name;
    serverGroupManager.kind = kind;
    serverGroupManager.namespace = serverGroupManagerDetails.region;
    return serverGroupManager;
  }

  private extractServerGroupManager(stateParams: IServerGroupManagerStateParams): void {
    this.serverGroupManager = this.transformServerGroupManager(this.app.getDataSource('serverGroupManagers').data.find((manager: IServerGroupManager) =>
      manager.name === stateParams.serverGroupManager
        && manager.region === stateParams.region
        && manager.account === stateParams.accountId
    ));
  }
}

export const KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL = 'spinnaker.kubernetes.v2.serverGroupManager.details.controller';
module(KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL, [])
  .controller('kubernetesV2ServerGroupManagerDetailsCtrl', KubernetesServerGroupManagerDetailsController)
