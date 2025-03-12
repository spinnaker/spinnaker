import type { StateService } from '@uirouter/angularjs';
import type { IController, IScope } from 'angular';
import { module } from 'angular';
import type { IModalService } from 'angular-ui-bootstrap';
import { cloneDeep } from 'lodash';

import type { Application, ILoadBalancer, ILoadBalancerDeleteCommand } from '@spinnaker/core';
import { ConfirmationModalService, LoadBalancerWriter } from '@spinnaker/core';
import type { ICloudrunLoadBalancer } from '../../common/domain/index';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class CloudrunLoadBalancerDetailsController implements IController {
  public state = { loading: true };
  private loadBalancerFromParams: ILoadBalancerFromStateParams;
  public loadBalancer: ICloudrunLoadBalancer;

  public static $inject = ['$uibModal', '$state', '$scope', 'loadBalancer', 'app'];
  constructor(
    private $uibModal: IModalService,
    private $state: StateService,
    private $scope: IScope,
    loadBalancer: ILoadBalancerFromStateParams,
    private app: Application,
  ) {
    this.loadBalancerFromParams = loadBalancer;
    this.app
      .getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  // edit loadbalancer to change traffic

  public editLoadBalancer(): void {
    this.$uibModal.open({
      templateUrl: require('../configure/wizard/wizard.html'),
      controller: 'cloudrunLoadBalancerWizardCtrl as ctrl',
      size: 'lg',
      resolve: {
        application: () => this.app,
        loadBalancer: () => cloneDeep(this.loadBalancer),
        isNew: () => false,
        forPipelineConfig: () => false,
      },
    });
  }

  private extractLoadBalancer(): void {
    this.loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: ILoadBalancer) => {
      return test.name === this.loadBalancerFromParams.name && test.account === this.loadBalancerFromParams.accountId;
    }) as ICloudrunLoadBalancer;

    if (this.loadBalancer) {
      this.state.loading = false;
      this.app.getDataSource('loadBalancers').onRefresh(this.$scope, () => this.extractLoadBalancer());
    } else {
      this.autoClose();
    }
  }

  public deleteLoadBalancer(): void {
    const taskMonitor = {
      application: this.app,
      title: 'Deleting ' + this.loadBalancer.name,
    };

    const submitMethod = () => {
      const loadBalancer: ILoadBalancerDeleteCommand = {
        cloudProvider: this.loadBalancer.cloudProvider,
        loadBalancerName: this.loadBalancer.name,
        credentials: this.loadBalancer.account,
      };
      return LoadBalancerWriter.deleteLoadBalancer(loadBalancer, this.app);
    };

    ConfirmationModalService.confirm({
      header: 'Really delete ' + this.loadBalancer.name + '?',
      buttonText: 'Delete ' + this.loadBalancer.name,
      body: this.getConfirmationModalBodyHtml(),
      account: this.loadBalancer.account,
      taskMonitorConfig: taskMonitor,
      submitMethod,
    });
  }

  public canDeleteLoadBalancer(): boolean {
    return this.loadBalancer.name !== 'default';
  }

  private getConfirmationModalBodyHtml(): string {
    const serverGroupNames = this.loadBalancer.serverGroups.map((serverGroup) => serverGroup.name);
    const hasAny = serverGroupNames ? serverGroupNames.length > 0 : false;
    const hasMoreThanOne = serverGroupNames ? serverGroupNames.length > 1 : false;

    // HTML accepted by the confirmationModalService is static (i.e., not managed by angular).
    if (hasAny) {
      if (hasMoreThanOne) {
        const listOfServerGroupNames = serverGroupNames.map((name) => `<li>${name}</li>`).join('');
        return `<div class="alert alert-warning">
            <p>
              Deleting <b>${this.loadBalancer.name}</b> will destroy the following server groups:
              <ul>
                ${listOfServerGroupNames}
              </ul>
            </p>
          </div>
        `;
      } else {
        return `<div class="alert alert-warning">
            <p>
              Deleting <b>${this.loadBalancer.name}</b> will destroy <b>${serverGroupNames[0]}</b>.
            </p>
          </div>
        `;
      }
    } else {
      return null;
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

export const CLOUDRUN_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.cloudrun.loadBalancerDetails.controller';
module(CLOUDRUN_LOAD_BALANCER_DETAILS_CTRL, []).controller(
  'cloudrunLoadBalancerDetailsCtrl',
  CloudrunLoadBalancerDetailsController,
);
