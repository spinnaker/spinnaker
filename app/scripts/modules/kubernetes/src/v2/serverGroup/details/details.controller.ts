import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import {
  Application,
  CONFIRMATION_MODAL_SERVICE,
  IManifestStatus,
  IServerGroup,
  SERVER_GROUP_READER,
  SERVER_GROUP_WARNING_MESSAGE_SERVICE,
  SERVER_GROUP_WRITER,
  ServerGroupReader
} from '@spinnaker/core';

import { IKubernetesServerGroup } from './IKubernetesServerGroup';
import { KubernetesManifestStatusService } from '../../manifest/status/status.service';

interface IServerGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesServerGroupDetailsController implements IController {
  public state = { loading: true };
  public serverGroup: IKubernetesServerGroup;
  public status: IManifestStatus = { stable: true };

  constructor(serverGroup: IServerGroupFromStateParams,
              public app: Application,
              private $uibModal: IModalService,
              private $scope: IScope,
              private kubernetesManifestStatusService: KubernetesManifestStatusService,
              private serverGroupReader: ServerGroupReader) {
    'ngInject';

    this.kubernetesManifestStatusService.makeStatusRefresher(this.app, this.$scope, {
      account: serverGroup.accountId,
      location: serverGroup.region,
      name: serverGroup.name,
    }, this);

    this.app
      .ready()
      .then(() => this.extractServerGroup(serverGroup))
      .catch(() => this.autoClose());
  }

  public canScaleServerGroup(): boolean {
    return this.serverGroup.kind !== 'DaemonSet';
  }

  public scaleServerGroup(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/scale/scale.html'),
      controller: 'kubernetesV2ManifestScaleCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroup.name,
          namespace: this.serverGroup.namespace,
          account: this.serverGroup.account
        },
        currentReplicas: this.serverGroup.manifest.spec.replicas,
        application: this.app
      }
    });
  }

  public editServerGroup(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/wizard/manifestWizard.html'),
      size: 'lg',
      controller: 'kubernetesV2ManifestEditCtrl',
      controllerAs: 'ctrl',
      resolve: {
        sourceManifest: this.serverGroup.manifest,
        sourceMoniker: this.serverGroup.moniker,
        application: this.app
      }
    });
  }

  public deleteServerGroup(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroup.name,
          namespace: this.serverGroup.namespace,
          account: this.serverGroup.account
        },
        application: this.app
      }
    });
  }

  private autoClose(): void {
    return;
  }

  private transformServerGroup(serverGroupDetails: IServerGroup): IKubernetesServerGroup {
    const serverGroup = serverGroupDetails as IKubernetesServerGroup;
    const [kind, name] = serverGroup.name.split(' ');
    serverGroup.displayName = name;
    serverGroup.kind = kind;
    serverGroup.namespace = serverGroupDetails.region;
    return serverGroup;
  }

  private extractServerGroup(fromParams: IServerGroupFromStateParams): ng.IPromise<void> {
    return this.serverGroupReader
      .getServerGroup(this.app.name, fromParams.accountId, fromParams.region, fromParams.name)
      .then((serverGroupDetails: IServerGroup) => {
        this.serverGroup = this.transformServerGroup(serverGroupDetails);
        this.serverGroup.account = fromParams.accountId;
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
