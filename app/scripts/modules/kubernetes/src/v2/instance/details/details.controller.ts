import { IController, IPromise, IQService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { flattenDeep } from 'lodash';

import {
  Application,
  CONFIRMATION_MODAL_SERVICE,
  INSTANCE_READ_SERVICE,
  InstanceReader,
  RECENT_HISTORY_SERVICE,
  RecentHistoryService,
  IManifest,
} from '@spinnaker/core';

import { IKubernetesInstance } from './IKubernetesInstance';
import { KubernetesManifestService } from '../../manifest/manifest.service';

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

class KubernetesInstanceDetailsController implements IController {
  public state = { loading: true };
  public instance: IKubernetesInstance;
  public manifest: IManifest;

  constructor(
    instance: InstanceFromStateParams,
    private $uibModal: IModalService,
    private $q: IQService,
    private $scope: IScope,
    private app: Application,
    private kubernetesManifestService: KubernetesManifestService,
    private instanceReader: InstanceReader,
    private recentHistoryService: RecentHistoryService,
  ) {
    'ngInject';

    this.app
      .ready()
      .then(() => this.retrieveInstance(instance))
      .then(instanceDetails => {
        this.instance = instanceDetails;

        this.kubernetesManifestService.makeManifestRefresher(
          this.app,
          this.$scope,
          {
            account: this.instance.account,
            location: this.instance.namespace,
            name: this.instance.name,
          },
          this,
        );
        this.state.loading = false;
      })
      .catch(() => {
        this.state.loading = false;
      });
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
    this.$uibModal.open({
      templateUrl: require('../../manifest/wizard/manifestWizard.html'),
      size: 'lg',
      controller: 'kubernetesV2ManifestEditCtrl',
      controllerAs: 'ctrl',
      resolve: {
        sourceManifest: this.instance.manifest,
        sourceMoniker: this.instance.moniker,
        application: this.app,
      },
    });
  }

  private retrieveInstance(instance: InstanceFromStateParams): IPromise<IKubernetesInstance> {
    const instanceLocatorPredicate = (dataSource: InstanceManager) => {
      return dataSource.instances.some(possibleMatch => possibleMatch.id === instance.instanceId);
    };

    const dataSources: InstanceManager[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map(loadBalancer => loadBalancer.serverGroups),
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

      this.recentHistoryService.addExtraDataToLatest('instances', recentHistoryExtraData);
      return this.instanceReader
        .getInstanceDetails(instanceManager.account, instanceManager.region, instance.instanceId)
        .then((instanceDetails: IKubernetesInstance) => {
          instanceDetails.account = instanceManager.account;
          instanceDetails.namespace = instanceDetails.manifest.metadata.namespace;
          instanceDetails.displayName = instanceDetails.manifest.metadata.name;
          instanceDetails.kind = instanceDetails.manifest.kind;
          instanceDetails.apiVersion = instanceDetails.manifest.apiVersion;
          instanceDetails.id = instanceDetails.name;
          instanceDetails.provider = 'kubernetes';
          return instanceDetails;
        });
    } else {
      return this.$q.reject();
    }
  }
}

export const KUBERNETES_V2_INSTANCE_DETAILS_CTRL = 'spinnaker.kubernetes.v2.instanceDetails.controller';

module(KUBERNETES_V2_INSTANCE_DETAILS_CTRL, [
  CONFIRMATION_MODAL_SERVICE,
  INSTANCE_READ_SERVICE,
  RECENT_HISTORY_SERVICE,
]).controller('kubernetesV2InstanceDetailsCtrl', KubernetesInstanceDetailsController);
