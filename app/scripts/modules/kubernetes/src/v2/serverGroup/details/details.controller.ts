import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import {
  Application,
  CONFIRMATION_MODAL_SERVICE,
  ClusterTargetBuilder,
  IEntityTags,
  IManifest,
  IOwnerOption,
  IServerGroup,
  SERVER_GROUP_WRITER,
  ServerGroupReader,
  ConfirmationModalService,
} from '@spinnaker/core';

import { IKubernetesServerGroup } from './IKubernetesServerGroup';
import { KubernetesManifestService } from 'kubernetes/v2/manifest/manifest.service';
import { KubernetesManifestCommandBuilder } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { ManifestWizard } from 'kubernetes/v2/manifest/wizard/ManifestWizard';
import { ManifestTrafficService } from 'kubernetes/v2/manifest/traffic/ManifestTrafficService';

interface IServerGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesServerGroupDetailsController implements IController {
  public state = { loading: true };
  public serverGroup: IKubernetesServerGroup;
  public manifest: IManifest;
  public entityTagTargets: IOwnerOption[];

  public static $inject = ['serverGroup', 'app', '$uibModal', '$scope', 'confirmationModalService'];
  constructor(
    serverGroup: IServerGroupFromStateParams,
    public app: Application,
    private $uibModal: IModalService,
    private $scope: IScope,
    private confirmationModalService: ConfirmationModalService,
  ) {
    const unsubscribe = KubernetesManifestService.makeManifestRefresher(
      this.app,
      {
        account: serverGroup.accountId,
        location: serverGroup.region,
        name: serverGroup.name,
      },
      this,
    );
    this.$scope.$on('$destroy', () => {
      unsubscribe();
    });

    this.app
      .ready()
      .then(() => this.extractServerGroup(serverGroup))
      .catch(() => this.autoClose());

    this.app.getDataSource('serverGroups').onRefresh(this.$scope, () => this.extractServerGroup(serverGroup));
  }

  private ownerReferences(): any[] {
    const manifest = this.serverGroup.manifest;
    if (
      manifest != null &&
      manifest.hasOwnProperty('metadata') &&
      manifest.metadata.hasOwnProperty('ownerReferences') &&
      Array.isArray(manifest.metadata.ownerReferences)
    ) {
      return manifest.metadata.ownerReferences;
    } else {
      return [] as any[];
    }
  }

  private ownerIsController(ownerReference: any): boolean {
    return ownerReference.hasOwnProperty('controller') && ownerReference.controller === true;
  }

  private lowerCaseFirstLetter(s: string): string {
    return s.charAt(0).toLowerCase() + s.slice(1);
  }

  public manifestController(): string {
    const controller = this.ownerReferences().find(this.ownerIsController);
    if (typeof controller === 'undefined') {
      return null;
    } else {
      return this.lowerCaseFirstLetter(controller.kind) + ' ' + controller.name;
    }
  }

  public canScaleServerGroup(): boolean {
    return this.serverGroup.kind !== 'DaemonSet' && this.manifestController() === null;
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
          account: this.serverGroup.account,
        },
        currentReplicas: this.serverGroup.manifest.spec.replicas,
        application: this.app,
      },
    });
  }

  public canEditServerGroup(): boolean {
    return this.manifestController() === null;
  }

  public editServerGroup(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.serverGroup.manifest,
      this.serverGroup.moniker,
      this.serverGroup.account,
    ).then(builtCommand => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
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
          account: this.serverGroup.account,
        },
        manifestController: () => this.manifestController(),
        application: this.app,
      },
    });
  }

  public canDisable = () => ManifestTrafficService.canDisableServerGroup(this.serverGroup);

  public disableServerGroup = (): void => {
    this.confirmationModalService.confirm({
      header: `Really disable ${this.manifest.name}?`,
      buttonText: 'Disable',
      askForReason: true,
      submitJustWithReason: true,
      submitMethod: ({ reason }: { reason: string }) => ManifestTrafficService.disable(this.manifest, this.app, reason),
      taskMonitorConfig: {
        application: this.app,
        title: `Disabling ${this.manifest.name}`,
        onTaskComplete: () => this.app.getDataSource('serverGroups').refresh(),
      },
    });
  };

  public canEnable = () => ManifestTrafficService.canEnableServerGroup(this.serverGroup);

  public enableServerGroup = (): void => {
    this.confirmationModalService.confirm({
      header: `Really enable ${this.manifest.name}?`,
      buttonText: 'Enable',
      askForReason: true,
      submitJustWithReason: true,
      submitMethod: ({ reason }: { reason: string }) => ManifestTrafficService.enable(this.manifest, this.app, reason),
      taskMonitorConfig: {
        application: this.app,
        title: `Enabling ${this.manifest.name}`,
        onTaskComplete: () => this.app.getDataSource('serverGroups').refresh(),
      },
    });
  };

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
    return ServerGroupReader.getServerGroup(
      this.app.name,
      fromParams.accountId,
      fromParams.region,
      fromParams.name,
    ).then((serverGroupDetails: IServerGroup) => {
      this.serverGroup = this.transformServerGroup(serverGroupDetails);
      this.serverGroup.account = fromParams.accountId;
      this.state.loading = false;
      this.serverGroup.entityTags = this.extractEntityTags();
      this.entityTagTargets = this.configureEntityTagTargets();
    });
  }

  private extractEntityTags(): IEntityTags {
    for (const toCheck of this.app.serverGroups.data) {
      if (
        toCheck.name === this.serverGroup.name &&
        toCheck.account === this.serverGroup.account &&
        toCheck.region === this.serverGroup.region
      ) {
        return toCheck.entityTags;
      }
    }
    return null;
  }

  private configureEntityTagTargets(): IOwnerOption[] {
    return ClusterTargetBuilder.buildClusterTargets(this.serverGroup);
  }
}

export const KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL = 'spinnaker.kubernetes.v2.serverGroup.details.controller';

module(KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL, [CONFIRMATION_MODAL_SERVICE, SERVER_GROUP_WRITER]).controller(
  'kubernetesV2ServerGroupDetailsCtrl',
  KubernetesServerGroupDetailsController,
);
