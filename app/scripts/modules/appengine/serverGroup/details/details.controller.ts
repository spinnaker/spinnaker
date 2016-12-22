import {module, IScope} from 'angular';
import {find, cloneDeep, reduce, mapValues, get, map} from 'lodash';

import {ServerGroup, Execution} from 'core/domain/index';
import {Application} from 'core/application/application.model';
import {IAppengineLoadBalancer, IAppengineServerGroup} from 'appengine/domain/index';

interface IPrivateScope extends IScope {
  $$destroyed: boolean;
}

interface IServerGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class AppengineServerGroupDetailsController {
  public state = { loading: true };
  public serverGroup: IAppengineServerGroup;

  static get $inject () {
    return ['$state',
            '$scope',
            'serverGroup',
            'app',
            'serverGroupReader',
            'InsightFilterStateModel',
            'serverGroupWriter',
            'serverGroupWarningMessageService',
            'confirmationModalService',
            'runningExecutionsService'];
  }

  constructor(private $state: any,
              private $scope: IPrivateScope,
              serverGroup: IServerGroupFromStateParams,
              private app: Application,
              private serverGroupReader: any,
              public InsightFilterStateModel: any,
              private serverGroupWriter: any,
              private serverGroupWarningMessageService: any,
              private confirmationModalService: any,
              private runningExecutionsService: any) {

    this.serverGroupReader
      .getServerGroup(this.app.name, serverGroup.accountId, serverGroup.region, serverGroup.name)
      .then((serverGroupDetails: ServerGroup) => {
        this.serverGroup = Object.assign(serverGroupDetails, this.extractServerGroupFromApp(serverGroup)) as IAppengineServerGroup;
        this.state.loading = false;
      })
      .catch(() => this.autoClose());
  }

  public canDestroyServerGroup(): boolean {
    if (this.serverGroup) {
      let expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
      if (expectedAllocations) {
        return Object.keys(expectedAllocations).length > 0;
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  public destroyServerGroup(): void {
    let taskMonitor = {
      application: this.app,
      title: 'Destroying ' + this.serverGroup.name,
      forceRefreshMessage: 'Refreshing application...',
      forceRefreshEnabled: true,
      katoPhaseToMonitor: 'DESTROY_ASG'
    };

    let submitMethod = (params: any) => this.serverGroupWriter.destroyServerGroup(this.serverGroup, this.app, params);

    let stateParams = {
      name: this.serverGroup.name,
      accountId: this.serverGroup.account,
      region: this.serverGroup.region
    };

    let confirmationModalParams = {
      header: 'Really destroy ' + this.serverGroup.name + '?',
      buttonText: 'Destroy ' + this.serverGroup.name,
      account: this.serverGroup.account,
      provider: 'appengine',
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
      askForReason: true,
      platformHealthOnlyShowOverride: this.app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'appengine',
      body: this.getBodyTemplate(this.serverGroup, this.app),
      onTaskComplete: () => {
        if (this.$state.includes('**.serverGroup', stateParams)) {
          this.$state.go('^');
        }
      },
      onApplicationRefresh: () => {
        if (this.$state.includes('**.serverGroup', stateParams)) {
          this.$state.go('^');
        }
      },
      interestingHealthProviderNames: [] as string[],
    };

    if (this.app.attributes.platformHealthOnlyShowOverride && this.app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['appengine'];
    }

    this.confirmationModalService.confirm(confirmationModalParams);
  };

  public runningExecutions(): Execution[] {
    return this.runningExecutionsService.filterRunningExecutions((this.serverGroup as any).executions);
  }

  private getBodyTemplate(serverGroup: IAppengineServerGroup, app: Application): string {
    let template = '';
    if (this.isLastServerGroup(serverGroup, app)) {
      template += this.serverGroupWarningMessageService.getMessage(serverGroup);
    }

    if (!serverGroup.disabled) {
      let expectedAllocations = this.expectedAllocationsAfterDisableOperation(serverGroup, app);
      let tableRows = map(expectedAllocations, (allocation, serverGroupName) => {
        return `
          <tr>
            <td>${serverGroupName}</td>
            <td>${allocation * 100}%</td>
          </tr>
        `;
      }).join('');

      template += `
        <div class="well well-sm">
          <p>
            A destroy operation will first disable this server group.
          </p>
          <p>
            For App Engine, a disable operation sets this server group's allocation
            to 0% and sets the other enabled server groups' allocations to their relative proportions
            before the disable operation. The approximate allocations that will result from this operation are shown below.
          </p>
          <p>
            If you would like more fine-grained control over your server groups' allocations,
            edit <b>${serverGroup.loadBalancers[0]}</b> under the <b>Load Balancers</b> tab.
          </p>
          <div class="row">
            <div class="col-md-12">
              <table class="table table-condensed">
                <thead>
                <tr>
                  <th>Server Group</th>
                  <th>Allocation</th>
                </tr>
                </thead>
                <tbody>
                  ${tableRows}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      `;
    }

    return template;
  }

  private expectedAllocationsAfterDisableOperation(serverGroup: ServerGroup, app: Application): {[key: string]: number} {
    let loadBalancer = app.getDataSource('loadBalancers').data.find((toCheck: IAppengineLoadBalancer): boolean => {
      let allocations = get(toCheck, 'split.allocations', {});
      let enabledServerGroups = Object.keys(allocations);
      return enabledServerGroups.includes(serverGroup.name);
    });

    if (loadBalancer) {
      let allocations = cloneDeep(loadBalancer.split.allocations);
      delete allocations[serverGroup.name];
      let denominator = reduce(allocations, (partialSum: number, allocation: number) => partialSum + allocation, 0);
      let precision = loadBalancer.split.shardBy === 'COOKIE' ? 1000 : 100;
      allocations = mapValues(
        allocations,
        (allocation) => Math.round(allocation / denominator * precision) / precision
      );
      return allocations;
    } else {
      return null;
    }
  }

  private isLastServerGroup(serverGroup: ServerGroup, app: any): boolean {
    let cluster = find(app.clusters, {name: serverGroup.cluster, account: serverGroup.account}) as any;
    if (cluster && cluster.serverGroups) {
      return cluster.serverGroups.length === 1;
    } else {
      return false;
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

  private extractServerGroupFromApp(fromParams: IServerGroupFromStateParams): ServerGroup {
    let serverGroup = this.app.getDataSource('serverGroups').data.find((toCheck: ServerGroup) => {
      return toCheck.name === fromParams.name &&
        toCheck.account === fromParams.accountId &&
        toCheck.region === fromParams.region;
    });

    if (!serverGroup) {
      this.app.getDataSource('loadBalancers').data.some((loadBalancer) => {
        if (loadBalancer.account === fromParams.accountId) {
          return loadBalancer.serverGroups.some((toCheck: ServerGroup) => {
            if (toCheck.name === fromParams.name) {
              serverGroup = toCheck;
              return true;
            }
          });
        }
      });
    }

    return serverGroup;
  }
}

export const APPENGINE_SERVER_GROUP_DETAILS_CONTROLLER = 'spinnaker.appengine.serverGroup.details.controller';

module(APPENGINE_SERVER_GROUP_DETAILS_CONTROLLER, [
    require('core/confirmationModal/confirmationModal.service.js'),
    require('core/insight/insightFilterState.model.js'),
    require('core/serverGroup/serverGroup.read.service.js'),
    require('core/serverGroup/details/serverGroupWarningMessage.service.js'),
    require('core/serverGroup/serverGroup.write.service.js'),
    require('core/serverGroup/configure/common/runningExecutions.service.js'),
  ])
  .controller('appengineServerGroupDetailsCtrl', AppengineServerGroupDetailsController);
