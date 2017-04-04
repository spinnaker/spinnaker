import {module, IScope} from 'angular';
import {IModalService} from 'angular-ui-bootstrap';
import {IStateService} from 'angular-ui-router';
import {cloneDeep} from 'lodash';

import {Application} from 'core/application/application.model';
import {CONFIRMATION_MODAL_SERVICE, ConfirmationModalService} from 'core/confirmationModal/confirmationModal.service';
import {IAppengineLoadBalancer} from 'appengine/domain/index';
import {ILoadBalancer} from 'core/domain/index';
import {
  LOAD_BALANCER_WRITE_SERVICE, LoadBalancerWriter,
  ILoadBalancerDeleteDescription
} from 'core/loadBalancer/loadBalancer.write.service';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class AppengineLoadBalancerDetailsController {
  public state = { loading: true };
  public loadBalancer: IAppengineLoadBalancer;
  public dispatchRules: string[] = [];

  static get $inject() {
    return ['$uibModal', '$state', '$scope', 'loadBalancer', 'app', 'loadBalancerWriter', 'confirmationModalService'];
  }

  constructor(private $uibModal: IModalService,
              private $state: IStateService,
              private $scope: IScope,
              private loadBalancerFromParams: ILoadBalancerFromStateParams,
              private app: Application,
              private loadBalancerWriter: LoadBalancerWriter,
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
    };

    let submitMethod = () => {
      const loadBalancer: ILoadBalancerDeleteDescription = {
        cloudProvider: this.loadBalancer.cloudProvider,
        loadBalancerName: this.loadBalancer.name,
        credentials: this.loadBalancer.account,
      };
      return this.loadBalancerWriter.deleteLoadBalancer(loadBalancer, this.app);
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
    this.loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: ILoadBalancer) => {
      return test.name === this.loadBalancerFromParams.name &&
        test.account === this.loadBalancerFromParams.accountId;
    }) as IAppengineLoadBalancer;

    if (this.loadBalancer) {
      this.state.loading = false;
      this.buildDispatchRules();
      this.app.getDataSource('loadBalancers').onRefresh(this.$scope, () => this.extractLoadBalancer());
    } else {
      this.autoClose();
    }
  }

  private buildDispatchRules(): void {
    this.dispatchRules = [];
    if (this.loadBalancer && this.loadBalancer.dispatchRules) {
      this.loadBalancer.dispatchRules.forEach(rule => {
        if (rule.service === this.loadBalancer.name) {
          this.dispatchRules.push(rule.domain + rule.path);
        }
      });
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
  LOAD_BALANCER_WRITE_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
]).controller('appengineLoadBalancerDetailsCtrl', AppengineLoadBalancerDetailsController);
