import { StateService } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { orderBy } from 'lodash';

import {
  Application,
  ClusterTargetBuilder,
  IManifest,
  IOwnerOption,
  IServerGroupManager,
  IServerGroupManagerStateParams,
  ManifestReader,
  NameUtils,
  SETTINGS,
} from '@spinnaker/core';

import { IKubernetesServerGroupManager } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

class KubernetesServerGroupManagerDetailsController implements IController {
  public serverGroupManager: IKubernetesServerGroupManager;
  public state = { loading: true };
  public manifest: IManifest;
  public entityTagTargets: IOwnerOption[];

  public static $inject = ['serverGroupManager', '$scope', '$uibModal', 'app', '$state'];
  constructor(
    serverGroupManager: IServerGroupManagerStateParams,
    private $scope: IScope,
    private $uibModal: IModalService,
    public app: Application,
    private $state: StateService,
  ) {
    const dataSource = this.app.getDataSource('serverGroupManagers');
    dataSource
      .ready()
      .then(() => {
        this.extractServerGroupManager(serverGroupManager);
        this.$scope.isDisabled = !SETTINGS.kubernetesAdHocInfraWritesEnabled;
        dataSource.onRefresh(this.$scope, () => this.extractServerGroupManager(serverGroupManager));
      })
      .catch(() => this.autoClose());
    this.$scope.isDisabled = !SETTINGS.kubernetesAdHocInfraWritesEnabled;
  }

  public pauseRolloutServerGroupManager(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/rollout/pause.html'),
      controller: 'kubernetesV2ManifestPauseRolloutCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroupManager.name,
          namespace: this.serverGroupManager.namespace,
          account: this.serverGroupManager.account,
        },
        application: this.app,
      },
    });
  }

  public resumeRolloutServerGroupManager(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/rollout/resume.html'),
      controller: 'kubernetesV2ManifestResumeRolloutCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroupManager.name,
          namespace: this.serverGroupManager.namespace,
          account: this.serverGroupManager.account,
        },
        application: this.app,
      },
    });
  }

  public canUndoRolloutServerGroupManager(): boolean {
    return (
      this.serverGroupManager && this.serverGroupManager.serverGroups && this.serverGroupManager.serverGroups.length > 0
    );
  }

  public undoRolloutServerGroupManager(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/rollout/undo.html'),
      controller: 'kubernetesV2ManifestUndoRolloutCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroupManager.name,
          namespace: this.serverGroupManager.namespace,
          account: this.serverGroupManager.account,
        },
        revisions: () => {
          const [, ...rest] = orderBy(this.serverGroupManager.serverGroups, ['moniker.sequence'], ['desc']);
          return rest.map((serverGroup, index) => ({
            label: `${NameUtils.getSequence(serverGroup.moniker.sequence)}${index > 0 ? '' : ' - previous revision'}`,
            revision: serverGroup.moniker.sequence,
          }));
        },
        application: this.app,
      },
    });
  }

  public scaleServerGroupManager(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/scale/scale.html'),
      controller: 'kubernetesV2ManifestScaleCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.serverGroupManager.name,
          namespace: this.serverGroupManager.namespace,
          account: this.serverGroupManager.account,
        },
        currentReplicas: this.manifest.manifest.spec.replicas,
        application: this.app,
      },
    });
  }

  public editServerGroupManager(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.manifest.manifest,
      this.serverGroupManager.moniker,
      this.serverGroupManager.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
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
          account: this.serverGroupManager.account,
        },
        application: this.app,
        manifestController: (): string => null,
      },
    });
  }

  private extractServerGroupManager({ accountId, region, serverGroupManager }: IServerGroupManagerStateParams): void {
    const serverGroupManagerDetails = this.app
      .getDataSource('serverGroupManagers')
      .data.find(
        (manager: IServerGroupManager) =>
          manager.name === serverGroupManager && manager.region === region && manager.account === accountId,
      );

    if (!serverGroupManagerDetails) {
      return this.autoClose();
    }

    ManifestReader.getManifest(accountId, region, serverGroupManager).then((manifest: IManifest) => {
      this.manifest = manifest;
      this.serverGroupManager = serverGroupManagerDetails;
      this.entityTagTargets = this.configureEntityTagTargets();
      this.state.loading = false;
    });
  }

  private configureEntityTagTargets(): IOwnerOption[] {
    return ClusterTargetBuilder.buildManagerClusterTargets(this.serverGroupManager);
  }

  private autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$state.params.allowModalToStayOpen = true;
      this.$state.go('^', null, { location: 'replace' });
    }
  }
}

export const KUBERNETES_SERVER_GROUP_MANAGER_DETAILS_CTRL =
  'spinnaker.kubernetes.serverGroupManager.details.controller';
module(KUBERNETES_SERVER_GROUP_MANAGER_DETAILS_CTRL, []).controller(
  'kubernetesV2ServerGroupManagerDetailsCtrl',
  KubernetesServerGroupManagerDetailsController,
);
