import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { orderBy } from 'lodash';

import {
  NameUtils,
  Application,
  IManifest,
  IServerGroupManager,
  IServerGroupManagerStateParams,
  ClusterTargetBuilder,
  IOwnerOption,
  IEntityTags,
} from '@spinnaker/core';
import { IKubernetesServerGroupManager } from '../IKubernetesServerGroupManager';
import { KubernetesManifestService } from 'kubernetes/v2/manifest/manifest.service';
import { KubernetesManifestCommandBuilder } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { ManifestWizard } from 'kubernetes/v2/manifest/wizard/ManifestWizard';

class KubernetesServerGroupManagerDetailsController implements IController {
  public serverGroupManager: IKubernetesServerGroupManager;
  public state = { loading: true };
  public manifest: IManifest;
  public entityTagTargets: IOwnerOption[];

  public static $inject = ['serverGroupManager', '$scope', '$uibModal', 'app'];
  constructor(
    serverGroupManager: IServerGroupManagerStateParams,
    private $scope: IScope,
    private $uibModal: IModalService,
    public app: Application,
  ) {
    const unsubscribe = KubernetesManifestService.makeManifestRefresher(
      this.app,
      {
        account: serverGroupManager.accountId,
        location: serverGroupManager.region,
        name: serverGroupManager.serverGroupManager,
      },
      this,
    );
    this.$scope.$on('$destroy', () => {
      unsubscribe();
    });

    this.app.ready().then(() => {
      this.extractServerGroupManager(serverGroupManager);
      this.state.loading = false;
    });
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
      templateUrl: require('kubernetes/v2/manifest/rollout/undo.html'),
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
        currentReplicas: this.serverGroupManager.manifest.spec.replicas,
        application: this.app,
      },
    });
  }

  public editServerGroupManager(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.serverGroupManager.manifest,
      this.serverGroupManager.moniker,
      this.serverGroupManager.account,
    ).then(builtCommand => {
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
    this.serverGroupManager = this.transformServerGroupManager(
      this.app
        .getDataSource('serverGroupManagers')
        .data.find(
          (manager: IServerGroupManager) =>
            manager.name === stateParams.serverGroupManager &&
            manager.region === stateParams.region &&
            manager.account === stateParams.accountId,
        ),
    );
    this.entityTagTargets = this.configureEntityTagTargets();
    this.serverGroupManager.entityTags = this.extractEntityTags();
  }

  private configureEntityTagTargets(): IOwnerOption[] {
    return ClusterTargetBuilder.buildManagerClusterTargets(this.serverGroupManager);
  }

  private extractEntityTags(): IEntityTags {
    for (const toCheck of this.app.serverGroupManagers.data) {
      if (
        toCheck.name === this.serverGroupManager.name &&
        toCheck.account === this.serverGroupManager.account &&
        toCheck.region === this.serverGroupManager.namespace
      ) {
        return toCheck.entityTags;
      }
    }
    return null;
  }
}

export const KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL =
  'spinnaker.kubernetes.v2.serverGroupManager.details.controller';
module(KUBERNETES_V2_SERVER_GROUP_MANAGER_DETAILS_CTRL, []).controller(
  'kubernetesV2ServerGroupManagerDetailsCtrl',
  KubernetesServerGroupManagerDetailsController,
);
