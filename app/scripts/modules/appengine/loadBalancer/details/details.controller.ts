import {module} from 'angular';

import {Application} from 'core/application/application.model';
import {LoadBalancer} from 'core/domain/index';

interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class AppengineLoadBalancerDetailsController {
  public state = { loading: true };
  public loadBalancer: LoadBalancer;

  static get $inject() { return ['$state', '$scope', 'loadBalancer', 'app']; }

  constructor(private $state: any,
              private $scope: any,
              private loadBalancerFromParams: ILoadBalancerFromStateParams,
              private app: Application) {
    this.app.getDataSource('loadBalancers')
      .ready()
      .then(() => this.extractLoadBalancer());
  }

  private extractLoadBalancer(): void {
    this.loadBalancer = this.app.getDataSource('loadBalancers').data.find((test: LoadBalancer) => {
      return test.name === this.loadBalancerFromParams.name &&
        test.account === this.loadBalancerFromParams.accountId;
    });

    if (this.loadBalancer) {
      this.state.loading = false;
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
      this.$state.go('^', null, {location: 'replace'});
    }
  }
}

export const APPENGINE_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.appengine.loadBalancerDetails.controller';

module(APPENGINE_LOAD_BALANCER_DETAILS_CTRL, [])
  .controller('appengineLoadBalancerDetailsCtrl', AppengineLoadBalancerDetailsController);
