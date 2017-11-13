import { IController, module } from 'angular';

import { IServerGroupManager, Application } from '@spinnaker/core';

interface IServerGroupManagerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesServerGroupManagerDetailsController implements IController {
  public serverGroupManager: IServerGroupManager;

  constructor(serverGroupManager: IServerGroupManagerFromStateParams,
              public app: Application) {
    'ngInject';
    this.app.ready()
      .then(() => this.extractServerGroupManager(serverGroupManager));
  }

  private extractServerGroupManager(stateParams: IServerGroupManagerFromStateParams): void {
    this.serverGroupManager = this.app.getDataSource('serverGroupManagers').data.find((manager: IServerGroupManager) =>
      manager.name === stateParams.name
        && manager.region === stateParams.region
        && manager.account === stateParams.accountId
    );
  }
}

export const KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL = 'spinnaker.kubernetes.v2.serverGroupManager.details.controller';
module(KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL, [])
  .controller('kubernetesV2ServerGroupManagerDetailsCtrl', KubernetesServerGroupManagerDetailsController)
