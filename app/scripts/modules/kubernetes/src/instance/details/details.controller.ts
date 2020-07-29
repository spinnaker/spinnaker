import { IController, IPromise, IQService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { flattenDeep } from 'lodash';
import { StateService } from '@uirouter/angularjs';

import {
  Application,
  InstanceReader,
  RecentHistoryService,
  IManifest,
  ILoadBalancer,
  ManifestReader,
} from '@spinnaker/core';

import { IKubernetesInstance } from './IKubernetesInstance';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';

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
    ).then(builtCommand => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
    });
  }

  private extractInstance(instanceFromState: InstanceFromStateParams): void {
    this.retrieveInstance(instanceFromState).then((instance: IKubernetesInstance) => {
      if (!instance) {
        return this.autoClose();
      }
      ManifestReader.getManifest(instance.account, instance.namespace, instance.name).then((manifest: IManifest) => {
        this.instance = {
          ...instance,
          apiVersion: manifest.manifest.apiVersion,
          displayName: manifest.manifest.metadata.name,
        };
        this.manifest = manifest;
        this.consoleOutputInstance = {
          account: this.instance.account,
          region: this.instance.region,
          id: this.instance.humanReadableName,
          provider: this.instance.provider,
        };
        this.state.loading = false;
      });
    });
  }

  private retrieveInstance(instanceFromState: InstanceFromStateParams): IPromise<IKubernetesInstance> {
    const instanceLocatorPredicate = (dataSource: InstanceManager) => {
      return dataSource.instances.some(possibleMatch => possibleMatch.id === instanceFromState.instanceId);
    };

    const dataSources: InstanceManager[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map((loadBalancer: ILoadBalancer) => loadBalancer.serverGroups),
    ]);

    const instanceManager = dataSources.find(instanceLocatorPredicate);
    if (instanceManager) {
      const recentHistoryExtraData: { [key: string]: string } = {
        region: instanceManager.region,
        account: instanceManager.account,
      };

      if (instanceManager.category === 'serverGroup') {
        recentHistoryExtraData.serverGroup = instanceManager.name;
      }

      const instance = instanceManager.instances.find(i => i.id === instanceFromState.instanceId);

      if (!instance) {
        return this.$q.reject();
      }

      RecentHistoryService.addExtraDataToLatest('instances', recentHistoryExtraData);
      return InstanceReader.getInstanceDetails(instanceManager.account, instanceManager.region, instance.name).then(
        (instanceDetails: IKubernetesInstance) => {
          instanceDetails.namespace = instanceDetails.region;
          instanceDetails.id = instance.id;
          instanceDetails.name = instance.name;
          instanceDetails.provider = 'kubernetes';
          return instanceDetails;
        },
      );
    } else {
      return this.$q.reject();
    }
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
