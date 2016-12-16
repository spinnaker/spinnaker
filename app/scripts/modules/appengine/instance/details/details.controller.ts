import {module, IScope, IPromise, IQService} from 'angular';
import {IStateService} from 'angular-ui-router';
import {flattenDeep} from 'lodash';

import {Application} from 'core/application/application.model';
import {IAppengineInstance} from 'appengine/domain/index';

interface InstanceFromStateParams {
  instanceId: string;
}

interface InstanceContainer {
  account: string;
  region: string;
  instances: IAppengineInstance[];
}

class AppengineInstanceDetailsController {
  public state = {loading: true};
  public instance: IAppengineInstance;
  public upToolTip: string = 'An App Engine instance is \'Up\' if a load balancer is directing traffic to its server group.';
  public outOfServiceToolTip: string = `
    An App Engine instance is 'Out Of Service' if no load balancers are directing traffic to its server group.`;

  static get $inject() { return ['$scope', '$state', '$q', 'app', 'instanceReader', 'instance']; }

  constructor(private $scope: IScope,
              private $state: IStateService,
              private $q: IQService,
              private app: Application,
              private instanceReader: any,
              instance: InstanceFromStateParams) {
    this.app.ready()
      .then(() => this.retrieveInstance(instance))
      .then((instanceDetails) => {
        this.instance = instanceDetails;
        this.state.loading = false;
      })
      .catch(() => this.autoClose());
  }

  private autoClose(): void {
    if (!(this.$scope as any).$$destroyed) {
      (this.$state as any).params.allowModalToStayOpen = true;
      this.$state.go('^', null, {location: 'replace'});
    }
  }

  private retrieveInstance(instance: InstanceFromStateParams): IPromise<IAppengineInstance> {
    let instanceLocatorPredicate = (dataSource: InstanceContainer) => {
      return dataSource.instances.some((possibleMatch) => possibleMatch.id === instance.instanceId);
    };

    let dataSources: InstanceContainer[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map((loadBalancer) => loadBalancer.serverGroups),
    ]);

    let instanceContainer = dataSources.find(instanceLocatorPredicate);

    if (instanceContainer) {
      return this.instanceReader
        .getInstanceDetails(instanceContainer.account, instanceContainer.region, instance.instanceId)
        .then((instanceDetails: IAppengineInstance) => {
          instanceDetails.account = instanceContainer.account;
          instanceDetails.region = instanceContainer.region;
          return instanceDetails;
        });
    } else {
      return this.$q.reject();
    }
  }
}

export const APPENGINE_INSTANCE_DETAILS_CTRL = 'spinnaker.appengine.instanceDetails.controller';

module(APPENGINE_INSTANCE_DETAILS_CTRL, [
  require('core/instance/instance.read.service.js'),
]).controller('appengineInstanceDetailsCtrl', AppengineInstanceDetailsController);
