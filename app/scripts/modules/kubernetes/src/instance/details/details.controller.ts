import { StateService } from '@uirouter/angularjs';
import { IController, IQService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { flattenDeep } from 'lodash';

import {
  Application,
  ILoadBalancer,
  IManifest,
  InstanceReader,
  ManifestReader,
  RecentHistoryService,
} from '@spinnaker/core';

import { IKubernetesInstance } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

interface InstanceFromStateParams {
  instanceId: string;
}

interface InstanceManager {
  account: string;
  region: string;
  category: string; // e.g., serverGroup, loadBalancer.
  name: string; // Parent resource name, not instance name.
  instances: IKubernetesInstance[];
}

interface IConsoleOutputInstance {
  account: string;
  region: string;
  id: string;
  provider: string;
}

interface InstanceIdentifier {
  account: string;
  id: string;
  name: string;
  namespace: string;
}

class KubernetesInstanceDetailsController implements IController {
  public state = { loading: true };
  public instance: IKubernetesInstance;
  public manifest: IManifest;
  public consoleOutputInstance: IConsoleOutputInstance;

  public static $inject = ['instance', '$uibModal', '$q', '$scope', 'app', '$state'];
  constructor(
    instance: InstanceFromStateParams,
    private $uibModal: IModalService,
    private $q: IQService,
    private $scope: IScope,
    private app: Application,
    private $state: StateService,
  ) {
    this.app
      .ready()
      .then(() => {
        this.extractInstance(instance);
        this.app.onRefresh(this.$scope, () => this.extractInstance(instance));
      })
      .catch(() => this.autoClose());
  }

  public deleteInstance(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.instance.name,
          namespace: this.instance.namespace,
          account: this.instance.account,
        },
        application: this.app,
        manifestController: (): string => null,
      },
    });
  }

  public editInstance(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.manifest.manifest,
      this.instance.moniker,
      this.instance.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
    });
  }

  private extractInstance(instanceFromState: InstanceFromStateParams): void {
    const instanceId = this.retrieveInstance(instanceFromState);
    if (!instanceId) {
      return this.autoClose();
    }
    this.$q
      .all([
        this.fetchInstance(instanceId).then((instance: IKubernetesInstance) => {
          this.instance = instance;
          this.consoleOutputInstance = {
            account: instance.account,
            region: instance.zone,
            id: instance.humanReadableName,
            provider: instance.provider,
          };
        }),
        ManifestReader.getManifest(instanceId.account, instanceId.namespace, instanceId.name).then(
          (manifest: IManifest) => {
            this.manifest = manifest;
          },
        ),
      ])
      .then(() => {
        this.state.loading = false;
      });
  }

  private fetchInstance(instance: InstanceIdentifier): PromiseLike<IKubernetesInstance> {
    return InstanceReader.getInstanceDetails(instance.account, instance.namespace, instance.name).then(
      (instanceDetails: IKubernetesInstance) => {
        instanceDetails.id = instance.id;
        instanceDetails.name = instance.name;
        instanceDetails.provider = 'kubernetes';
        return instanceDetails;
      },
    );
  }

  private retrieveInstance(instanceFromState: InstanceFromStateParams): InstanceIdentifier {
    const instanceLocatorPredicate = (dataSource: InstanceManager) => {
      return dataSource.instances.some((possibleMatch) => possibleMatch.id === instanceFromState.instanceId);
    };

    const dataSources: InstanceManager[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map((loadBalancer: ILoadBalancer) => loadBalancer.serverGroups),
    ]);

    const instanceManager = dataSources.find(instanceLocatorPredicate);
    if (!instanceManager) {
      return null;
    }
    const recentHistoryExtraData: { [key: string]: string } = {
      region: instanceManager.region,
      account: instanceManager.account,
    };

    if (instanceManager.category === 'serverGroup') {
      recentHistoryExtraData.serverGroup = instanceManager.name;
    }

    const instance = instanceManager.instances.find((i) => i.id === instanceFromState.instanceId);
    if (!instance) {
      return null;
    }

    RecentHistoryService.addExtraDataToLatest('instances', recentHistoryExtraData);

    return {
      id: instance.id,
      name: instance.name,
      namespace: instanceManager.region,
      account: instanceManager.account,
    };
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

export const KUBERNETES_INSTANCE_DETAILS_CTRL = 'spinnaker.kubernetes.instanceDetails.controller';

module(KUBERNETES_INSTANCE_DETAILS_CTRL, []).controller(
  'kubernetesV2InstanceDetailsCtrl',
  KubernetesInstanceDetailsController,
);
