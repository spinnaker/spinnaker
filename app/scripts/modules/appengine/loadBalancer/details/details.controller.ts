import {module} from 'angular';
import {cloneDeep} from 'lodash';

import {Application} from 'core/application/application.model';
import {CONFIRMATION_MODAL_SERVICE, ConfirmationModalService} from 'core/confirmationModal/confirmationModal.service';
import {IAppengineLoadBalancer} from 'appengine/domain/index';
import {LoadBalancer} from 'core/domain/index';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class AppengineLoadBalancerDetailsController {
  public state = { loading: true };
  public loadBalancer: IAppengineLoadBalancer;

  static get $inject() {
    return ['$uibModal', '$state', '$scope', 'loadBalancer', 'app', 'loadBalancerWriter', 'confirmationModalService'];
  }

  constructor(private $uibModal: any,
              private $state: any,
              private $scope: any,
              private loadBalancerFromParams: ILoadBalancerFromStateParams,
              private app: Application,
              private loadBalancerWriter: any,
              private confirmationModalService: ConfirmationModalService) {
    this.app.getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  public editLoadBalancer(): void {
    this.$uibModal.open({
      templateUrl: require('../configure/wizard/wizard.html'),
      controller: 'appengineLoadBalancerWizardCtrl as ctrl',
      size: 'lg',
      resolve: {
        application: () => this.app,
        loadBalancer: () => cloneDeep(this.loadBalancer),
        isNew: () => false,
        forPipelineConfig: () => false,
      }
    });
  }

  public deleteLoadBalancer(): void {
    let taskMonitor = {
      application: this.app,
      title: 'Deleting ' + this.loadBalancer.name,
      forceRefreshMessage: 'Refreshing application...',
      forceRefreshEnabled: true
    };

    let submitMethod = () => {
      let loadBalancer = cloneDeep(this.loadBalancer) as any;
      loadBalancer.providerType = loadBalancer.provider;
      loadBalancer.accountId = loadBalancer.account;
      return this.loadBalancerWriter.deleteLoadBalancer(loadBalancer, this.app, {
        loadBalancerName: loadBalancer.name,
      });
    };

    this.confirmationModalService.confirm({
      header: 'Really delete ' + this.loadBalancer.name + '?',
      buttonText: 'Delete ' + this.loadBalancer.name,
      body: this.getConfirmationModalBodyHtml(),
      account: this.loadBalancer.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
    });
  }

  public canDeleteLoadBalancer(): boolean {
    return this.loadBalancer.name !== 'default';
  }

  private extractLoadBalancer(): void {
    this.loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: LoadBalancer) => {
      return test.name === this.loadBalancerFromParams.name &&
        test.account === this.loadBalancerFromParams.accountId;
    }) as IAppengineLoadBalancer;

    if (this.loadBalancer) {
      this.state.loading = false;
      this.app.getDataSource('loadBalancers').onRefresh(this.$scope, () => this.extractLoadBalancer());
    } else {
      this.autoClose();
    }
  }

  private getConfirmationModalBodyHtml(): string {
    let serverGroupNames = this.loadBalancer.serverGroups.map(serverGroup => serverGroup.name);
    let hasAny = serverGroupNames ? serverGroupNames.length > 0 : false;
    let hasMoreThanOne = serverGroupNames ? serverGroupNames.length > 1 : false;

    // HTML accepted by the confirmationModalService is static (i.e., not managed by angular).
    if (hasAny) {
      if (hasMoreThanOne) {
        let listOfServerGroupNames = serverGroupNames.map(name => `<li>${name}</li>`).join('');
        return `
          <div class="alert alert-warning">      
            <p>
              Deleting <b>${this.loadBalancer.name}</b> will destroy the following server groups:
              <ul>
                ${listOfServerGroupNames}
              </ul>
            </p>
          </div>
        `;
      } else {
        return `
          <div class="alert alert-warning">
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
      this.$state.go('^', null, {location: 'replace'});
    }
  }
}

export const APPENGINE_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.appengine.loadBalancerDetails.controller';

module(APPENGINE_LOAD_BALANCER_DETAILS_CTRL, [
  require('core/loadBalancer/loadBalancer.write.service.js'),
  CONFIRMATION_MODAL_SERVICE,
]).controller('appengineLoadBalancerDetailsCtrl', AppengineLoadBalancerDetailsController);
