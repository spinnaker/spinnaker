import { IController, IPromise, IQService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { flattenDeep } from 'lodash';

import {
  Application,
  CONFIRMATION_MODAL_SERVICE,
  InstanceReader,
  RecentHistoryService,
  IManifest,
} from '@spinnaker/core';

import { IKubernetesInstance } from './IKubernetesInstance';
import { KubernetesManifestService } from 'kubernetes/v2/manifest/manifest.service';
import { ManifestWizard } from 'kubernetes/v2/manifest/wizard/ManifestWizard';
import { KubernetesManifestCommandBuilder } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

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

  public static $inject = ['instance', '$uibModal', '$q', '$scope', 'app'];
  constructor(
    instance: InstanceFromStateParams,
    private $uibModal: IModalService,
    private $q: IQService,
    private $scope: IScope,
    private app: Application,
  ) {
    this.app
      .ready()
      .then(() => this.retrieveInstance(instance))
      .then(instanceDetails => {
        this.instance = instanceDetails;
        this.consoleOutputInstance = {
          account: instanceDetails.account,
          region: instanceDetails.region,
          id: instanceDetails.humanReadableName,
          provider: instanceDetails.provider,
        };

        const unsubscribe = KubernetesManifestService.makeManifestRefresher(
          this.app,
          {
            account: this.instance.account,
            location: this.instance.namespace,
            name: this.instance.name,
          },
          this,
        );
        this.$scope.$on('$destroy', () => {
          unsubscribe();
        });
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
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.instance.manifest,
      this.instance.moniker,
      this.instance.account,
    ).then(builtCommand => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
    });
  }

  private retrieveInstance(instanceFromState: InstanceFromStateParams): IPromise<IKubernetesInstance> {
    const instanceLocatorPredicate = (dataSource: InstanceManager) => {
      return dataSource.instances.some(possibleMatch => possibleMatch.id === instanceFromState.instanceId);
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

      const instance = instanceManager.instances.find(i => i.id === instanceFromState.instanceId);

      if (!instance) {
        return this.$q.reject();
      }

      RecentHistoryService.addExtraDataToLatest('instances', recentHistoryExtraData);
      return InstanceReader.getInstanceDetails(instanceManager.account, instanceManager.region, instance.name).then(
        (instanceDetails: IKubernetesInstance) => {
          instanceDetails.account = instanceManager.account;
          instanceDetails.namespace = instanceDetails.manifest.metadata.namespace;
          instanceDetails.displayName = instanceDetails.manifest.metadata.name;
          instanceDetails.kind = instanceDetails.manifest.kind;
          instanceDetails.apiVersion = instanceDetails.manifest.apiVersion;
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
}

export const KUBERNETES_V2_INSTANCE_DETAILS_CTRL = 'spinnaker.kubernetes.v2.instanceDetails.controller';

module(KUBERNETES_V2_INSTANCE_DETAILS_CTRL, [CONFIRMATION_MODAL_SERVICE]).controller(
  'kubernetesV2InstanceDetailsCtrl',
  KubernetesInstanceDetailsController,
);
