import { StateService } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { Application, ILoadBalancer, IManifest, ManifestReader, SETTINGS } from '@spinnaker/core';

import { IKubernetesLoadBalancer } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesLoadBalancerDetailsController implements IController {
  public state = { loading: true };
  public manifest: IManifest;
  public loadBalancer: IKubernetesLoadBalancer;

  public static $inject = ['$uibModal', '$state', '$scope', 'loadBalancer', 'app'];
  constructor(
    private $uibModal: IModalService,
    private $state: StateService,
    private $scope: IScope,
    loadBalancer: ILoadBalancerFromStateParams,
    private app: Application,
  ) {
    const dataSource = this.app.getDataSource('loadBalancers');
    dataSource
      .ready()
      .then(() => {
        this.extractLoadBalancer(loadBalancer);
        this.$scope.isDisabled = !SETTINGS.kubernetesAdHocInfraWritesEnabled;
        dataSource.onRefresh(this.$scope, () => this.extractLoadBalancer(loadBalancer));
      })
      .catch(() => this.autoClose());
  }

  public deleteLoadBalancer(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.loadBalancer.name,
          namespace: this.loadBalancer.namespace,
          account: this.loadBalancer.account,
        },
        application: this.app,
        manifestController: (): string => null,
      },
    });
  }

  public editLoadBalancer(): void {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.app,
      this.manifest.manifest,
      this.loadBalancer.moniker,
      this.loadBalancer.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: this.app, command: builtCommand });
    });
  }

  private extractLoadBalancer({ accountId, name, region }: ILoadBalancerFromStateParams): void {
    const loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: ILoadBalancer) => {
      return test.name === name && test.account === accountId && test.region === region;
    });

    if (!loadBalancer) {
      return this.autoClose();
    }

    ManifestReader.getManifest(accountId, region, name).then((manifest: IManifest) => {
      this.manifest = manifest;
      this.loadBalancer = loadBalancer;
      this.state.loading = false;
    });
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

export const KUBERNETES_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.kubernetes.loadBalancerDetails.controller';
module(KUBERNETES_LOAD_BALANCER_DETAILS_CTRL, []).controller(
  'kubernetesV2LoadBalancerDetailsCtrl',
  KubernetesLoadBalancerDetailsController,
);
