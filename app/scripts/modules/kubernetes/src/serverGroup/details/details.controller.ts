import { StateService } from '@uirouter/angularjs';
import { IController, IQService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import {
  Application,
  ClusterTargetBuilder,
  ConfirmationModalService,
  IManifest,
  IOwnerOption,
  IServerGroup,
  ManifestReader,
  SERVER_GROUP_WRITER,
  ServerGroupReader,
  SETTINGS,
} from '@spinnaker/core';

import { IKubernetesServerGroup } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestTrafficService } from '../../manifest/traffic/ManifestTrafficService';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

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

  public static $inject = ['serverGroup', 'app', '$uibModal', '$scope', '$state', '$q'];
  constructor(
    serverGroup: IServerGroupFromStateParams,
    public app: Application,
    private $uibModal: IModalService,
    private $scope: IScope,
    private $state: StateService,
    private $q: IQService,
  ) {
    const dataSource = this.app.getDataSource('serverGroups');
    dataSource
      .ready()
      .then(() => {
        this.extractServerGroup(serverGroup);
        this.$scope.isDisabled = !SETTINGS.kubernetesAdHocInfraWritesEnabled;
        dataSource.onRefresh(this.$scope, () => this.extractServerGroup(serverGroup));
      })
      .catch(() => this.autoClose());
  }

  private ownerReferences(): any[] {
    const manifest = this.manifest.manifest;
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
        currentReplicas: this.manifest.manifest.spec.replicas,
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
      this.manifest.manifest,
      this.serverGroup.moniker,
      this.serverGroup.account,
    ).then((builtCommand) => {
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
    ConfirmationModalService.confirm({
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
    ConfirmationModalService.confirm({
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
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$state.params.allowModalToStayOpen = true;
      this.$state.go('^', null, { location: 'replace' });
    }
  }

  private extractServerGroup({ accountId, name, region }: IServerGroupFromStateParams): void {
    this.$q
      .all([
        ServerGroupReader.getServerGroup(this.app.name, accountId, region, name),
        ManifestReader.getManifest(accountId, region, name),
      ])
      .then(([serverGroupDetails, manifest]: [IServerGroup, IManifest]) => {
        if (!serverGroupDetails) {
          return this.autoClose();
        }
        this.serverGroup = serverGroupDetails as IKubernetesServerGroup;
        this.entityTagTargets = this.configureEntityTagTargets();
        this.manifest = manifest;
        this.state.loading = false;
      });
  }

  private configureEntityTagTargets(): IOwnerOption[] {
    return ClusterTargetBuilder.buildClusterTargets(this.serverGroup);
  }
}

export const KUBERNETES_SERVER_GROUP_DETAILS_CTRL = 'spinnaker.kubernetes.serverGroup.details.controller';

module(KUBERNETES_SERVER_GROUP_DETAILS_CTRL, [SERVER_GROUP_WRITER]).controller(
  'kubernetesV2ServerGroupDetailsCtrl',
  KubernetesServerGroupDetailsController,
);
