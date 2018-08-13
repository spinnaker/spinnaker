import { copy, IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';

import { Application, ILoadBalancer } from '@spinnaker/core';

import { IKubernetesLoadBalancer } from './IKubernetesLoadBalancer';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class KubernetesLoadBalancerDetailsController implements IController {
  public state = { loading: true };
  private loadBalancerFromParams: ILoadBalancerFromStateParams;
  public loadBalancer: IKubernetesLoadBalancer;

  constructor(
    private $uibModal: IModalService,
    private $state: StateService,
    private $scope: IScope,
    loadBalancer: ILoadBalancerFromStateParams,
    private app: Application,
  ) {
    'ngInject';
    this.loadBalancerFromParams = loadBalancer;
    this.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  public deleteLoadBalancer(): void {
    this.$uibModal.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl',
      controllerAs: 'ctrl',
      resolve: {
        coordinates: {
          name: this.loadBalancerFromParams.name,
          namespace: this.loadBalancerFromParams.region,
          account: this.loadBalancerFromParams.accountId,
        },
        application: this.app,
        manifestController: (): string => null,
      },
    });
  }

  public editLoadBalancer(): void {
    this.$uibModal.open({
      templateUrl: require('kubernetes/v2/manifest/wizard/manifestWizard.html'),
      size: 'lg',
      controller: 'kubernetesV2ManifestEditCtrl',
      controllerAs: 'ctrl',
      resolve: {
        sourceManifest: () => this.loadBalancer.manifest,
        sourceMoniker: () => this.loadBalancer.moniker,
        application: () => this.app,
        account: () => this.loadBalancer.account,
      },
    });
  }

  private extractLoadBalancer(): void {
    const rawLoadBalancer = this.app.getDataSource('loadBalancers').data.find((test: ILoadBalancer) => {
      return (
        test.name === this.loadBalancerFromParams.name &&
        test.account === this.loadBalancerFromParams.accountId &&
        test.region === this.loadBalancerFromParams.region
      );
    });

    if (rawLoadBalancer) {
      this.state.loading = false;
      this.loadBalancer = copy(rawLoadBalancer) as IKubernetesLoadBalancer;
      this.loadBalancer.namespace = rawLoadBalancer.region;
      this.loadBalancer.displayName = rawLoadBalancer.manifest.metadata.name;
      this.loadBalancer.kind = rawLoadBalancer.manifest.kind;
      this.loadBalancer.apiVersion = rawLoadBalancer.manifest.apiVersion;

      this.app.getDataSource('loadBalancers').onRefresh(this.$scope, () => this.extractLoadBalancer());
    } else {
      this.autoClose();
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

export const KUBERNETES_V2_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.kubernetes.v2.loadBalancerDetails.controller';
module(KUBERNETES_V2_LOAD_BALANCER_DETAILS_CTRL, []).controller(
  'kubernetesV2LoadBalancerDetailsCtrl',
  KubernetesLoadBalancerDetailsController,
);
