import { IController, module } from 'angular';

import {
  Application,
  CONFIRMATION_MODAL_SERVICE,
  IServerGroup,
  SERVER_GROUP_READER,
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  SERVER_GROUP_WRITER,
  ServerGroupReader
} from '@spinnaker/core';

import { IKubernetesServerGroup } from './IKubernetesServerGroup';

interface IServerGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesServerGroupDetailsController implements IController {
  public state = { loading: true };
  public serverGroup: IKubernetesServerGroup;

  constructor(serverGroup: IServerGroupFromStateParams,
              public app: Application,
              private serverGroupReader: ServerGroupReader) {
    'ngInject';

    this.app
      .ready()
      .then(() => this.extractServerGroup(serverGroup))
      .catch(() => this.autoClose());
  }

  public canResizeServerGroup(): boolean {
    return false; // will change with daemon set
  }

  private autoClose(): void {
    return;
  }

  private transformServerGroup(serverGroupDetails: IServerGroup): IKubernetesServerGroup {
    const serverGroup = serverGroupDetails as IKubernetesServerGroup;
    const [apiVersion, kind, name] = serverGroup.name.split('|');
    serverGroup.displayName = name;
    serverGroup.kind = kind;
    serverGroup.apiVersion = apiVersion.replace(/\./g, '/');
    return serverGroup;
  }

  private extractServerGroup(fromParams: IServerGroupFromStateParams): ng.IPromise<void> {
    return this.serverGroupReader
      .getServerGroup(this.app.name, fromParams.accountId, fromParams.region, fromParams.name)
      .then((serverGroupDetails: IServerGroup) => {
        this.serverGroup = this.transformServerGroup(serverGroupDetails);
        this.state.loading = false;
      });
  }
}

export const KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL = 'spinnaker.kubernetes.v2.serverGroup.details.controller';

module(KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL, [
    CONFIRMATION_MODAL_SERVICE,
    SERVER_GROUP_WARNING_MESSAGE_SERVICE,
    SERVER_GROUP_READER,
    SERVER_GROUP_WRITER,
  ])
  .controller('kubernetesV2ServerGroupDetailsCtrl', KubernetesServerGroupDetailsController);
