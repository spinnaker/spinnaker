import {module, IScope} from 'angular';
import {find, cloneDeep, reduce, mapValues, get, map} from 'lodash';

import {Execution, ServerGroup} from 'core/domain/index';
import {CONFIRMATION_MODAL_SERVICE, ConfirmationModalService} from 'core/confirmationModal/confirmationModal.service';
import {SERVER_GROUP_READER, ServerGroupReader} from 'core/serverGroup/serverGroupReader.service';
import {SERVER_GROUP_WRITER, ServerGroupWriter} from 'core/serverGroup/serverGroupWriter.service';
import {Application} from 'core/application/application.model';
import {IAppengineLoadBalancer, IAppengineServerGroup} from 'appengine/domain/index';
import {SERVER_GROUP_WARNING_MESSAGE_SERVICE, ServerGroupWarningMessageService} from 'core/serverGroup/details/serverGroupWarningMessage.service';
import {APPENGINE_SERVER_GROUP_WRITER, AppengineServerGroupWriter} from '../writer/serverGroup.write.service';

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
            'serverGroupWriter',
            'serverGroupWarningMessageService',
            'confirmationModalService',
            'runningExecutionsService',
            'appengineServerGroupWriter'];
  }

  private static isLastServerGroup(serverGroup: ServerGroup, app: any): boolean {
    let cluster = find(app.clusters, {name: serverGroup.cluster, account: serverGroup.account}) as any;
    if (cluster && cluster.serverGroups) {
      return cluster.serverGroups.length === 1;
    } else {
      return false;
    }
  }

  private static buildExpectedAllocationsTable(expectedAllocations: {[key: string]: number}): string {
    let tableRows = map(expectedAllocations, (allocation, serverGroupName) => {
      return `
        <tr>
          <td>${serverGroupName}</td>
          <td>${allocation * 100}%</td>
        </tr>`;
    }).join('');

    return `
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
      </table>`;
  }

  constructor(private $state: any,
              private $scope: IPrivateScope,
              serverGroup: IServerGroupFromStateParams,
              private app: Application,
              private serverGroupReader: ServerGroupReader,
              private serverGroupWriter: ServerGroupWriter,
              private serverGroupWarningMessageService: ServerGroupWarningMessageService,
              private confirmationModalService: ConfirmationModalService,
              private runningExecutionsService: any,
              private appengineServerGroupWriter: AppengineServerGroupWriter) {

    this.app
      .ready()
      .then(() => this.extractServerGroup(serverGroup))
      .then(() => {
        if (!this.$scope.$$destroyed) {
          this.app.getDataSource('serverGroups').onRefresh(this.$scope, () => this.extractServerGroup(serverGroup));
        }
      })
      .catch(() => this.autoClose());
  }

  public canDisableServerGroup(): boolean {
    if (this.serverGroup) {
      if (this.serverGroup.disabled) {
        return false;
      }

      let expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
      if (expectedAllocations) {
        return Object.keys(expectedAllocations).length > 0;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  public canDestroyServerGroup(): boolean {
    if (this.serverGroup) {
      if (this.serverGroup.disabled) {
        return true;
      }

      let expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
      if (expectedAllocations) {
        return Object.keys(expectedAllocations).length > 0;
      } else {
        return false;
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

  public enableServerGroup(): void {
    let taskMonitor = {
      application: this.app,
      title: 'Enabling ' + this.serverGroup.name,
      forceRefreshMessage: 'Refreshing application...',
    };

    let submitMethod = (params: any) => this.serverGroupWriter.enableServerGroup(this.serverGroup, this.app, Object.assign(params));

    let modalBody = `
      <div class="well well-sm">
        <p>
          Enabling <b>${this.serverGroup.name}</b> will set its traffic allocation for 
          <b>${this.serverGroup.loadBalancers[0]}</b> to 100%.
        </p>
        <p>
          If you would like more fine-grained control over your server groups' allocations,
          edit <b>${this.serverGroup.loadBalancers[0]}</b> under the <b>Load Balancers</b> tab.
        </p> 
      </div>
    `;

    let confirmationModalParams = {
      header: 'Really enable ' + this.serverGroup.name + '?',
      buttonText: 'Enable ' + this.serverGroup.name,
      provider: 'appengine',
      body: modalBody,
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
      askForReason: true,
      interestingHealthProviderNames: [] as string[],
    };

    if (this.app.attributes.platformHealthOnlyShowOverride && this.app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['appengine'];
    }

    this.confirmationModalService.confirm(confirmationModalParams);
  }

  public disableServerGroup(): void {
    let taskMonitor = {
      application: this.app,
      title: 'Disabling ' + this.serverGroup.name,
    };

    let submitMethod = (params: any) => this.serverGroupWriter.disableServerGroup(this.serverGroup, this.app.name, params);

    let expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
    let modalBody = `
      <div class="well well-sm">
        <p>
          For App Engine, a disable operation sets this server group's allocation
          to 0% and sets the other enabled server groups' allocations to their relative proportions
          before the disable operation. The approximate allocations that will result from this operation are shown below.
        </p>
        <p>
          If you would like more fine-grained control over your server groups' allocations,
          edit <b>${this.serverGroup.loadBalancers[0]}</b> under the <b>Load Balancers</b> tab.
        </p>
        <div class="row">
          <div class="col-md-12">
            ${AppengineServerGroupDetailsController.buildExpectedAllocationsTable(expectedAllocations)}
          </div>
        </div>
      </div>
    `;

    let confirmationModalParams = {
      header: 'Really disable ' + this.serverGroup.name + '?',
      buttonText: 'Disable ' + this.serverGroup.name,
      provider: 'appengine',
      body: modalBody,
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
      askForReason: true,
      interestingHealthProviderNames: [] as string[],
    };

    if (this.app.attributes.platformHealthOnlyShowOverride && this.app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['appengine'];
    }

    this.confirmationModalService.confirm(confirmationModalParams);
  }

  public stopServerGroup(): void {
    let taskMonitor = {
      application: this.app,
      title: 'Stopping ' + this.serverGroup.name,
    };

    let submitMethod = () => this.appengineServerGroupWriter.stopServerGroup(this.serverGroup, this.app);

    let modalBody: string;
    if (!this.serverGroup.disabled) {
      modalBody = `
        <div class="alert alert-danger">
          <p>Stopping this server group will scale it down to zero instances.</p>
          <p>
            This server group is currently serving traffic from <b>${this.serverGroup.loadBalancers[0]}</b>.
            Traffic directed to this server group after it has been stopped will not be handled.
          </p>
        </div>`;
    }

    let confirmationModalParams = {
      header: 'Really stop ' + this.serverGroup.name + '?',
      buttonText: 'Stop ' + this.serverGroup.name,
      provider: 'appengine',
      account: this.serverGroup.account,
      body: modalBody,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
      askForReason: true,
    };

    this.confirmationModalService.confirm(confirmationModalParams);
  }

  public startServerGroup(): void {
    let taskMonitor = {
      application: this.app,
      title: 'Starting ' + this.serverGroup.name,
    };

    let submitMethod = () => this.appengineServerGroupWriter.startServerGroup(this.serverGroup, this.app);

    let confirmationModalParams = {
      header: 'Really start ' + this.serverGroup.name + '?',
      buttonText: 'Start ' + this.serverGroup.name,
      provider: 'appengine',
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
      askForReason: true,
    };

    this.confirmationModalService.confirm(confirmationModalParams);
  }

  public canStartServerGroup(): boolean {
    if (this.canStartOrStopServerGroup()) {
      return this.serverGroup.servingStatus === 'STOPPED';
    } else {
      return false;
    }
  }

  public canStopServerGroup(): boolean {
    if (this.canStartOrStopServerGroup()) {
      return this.serverGroup.servingStatus === 'SERVING';
    } else {
      return false;
    }
  }

  private canStartOrStopServerGroup(): boolean {
    let isFlex = this.serverGroup.env === 'FLEXIBLE';
    let usesManualScaling = get(this.serverGroup, 'scalingPolicy.type') === 'MANUAL';
    let usesBasicScaling = get(this.serverGroup, 'scalingPolicy.type') === 'BASIC';
    return isFlex || usesManualScaling || usesBasicScaling;
  }

  public runningExecutions(): Execution[] {
    return this.runningExecutionsService.filterRunningExecutions((this.serverGroup as any).executions);
  }

  private getBodyTemplate(serverGroup: IAppengineServerGroup, app: Application): string {
    let template = '';
    if (AppengineServerGroupDetailsController.isLastServerGroup(serverGroup, app)) {
      template += this.serverGroupWarningMessageService.getMessage(serverGroup);
    }

    if (!serverGroup.disabled) {
      let expectedAllocations = this.expectedAllocationsAfterDisableOperation(serverGroup, app);

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
              ${AppengineServerGroupDetailsController.buildExpectedAllocationsTable(expectedAllocations)}
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

  private autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    } else {
      this.$state.params.allowModalToStayOpen = true;
      this.$state.go('^', null, {location: 'replace'});
    }
  }

  private extractServerGroup(fromParams: IServerGroupFromStateParams): ng.IPromise<void> {
    return this.serverGroupReader
      .getServerGroup(this.app.name, fromParams.accountId, fromParams.region, fromParams.name)
      .then((serverGroupDetails: ServerGroup) => {
        let fromApp = this.app.getDataSource('serverGroups').data.find((toCheck: ServerGroup) => {
          return toCheck.name === fromParams.name &&
            toCheck.account === fromParams.accountId &&
            toCheck.region === fromParams.region;
        });

        if (!fromApp) {
          this.app.getDataSource('loadBalancers').data.some((loadBalancer) => {
            if (loadBalancer.account === fromParams.accountId) {
              return loadBalancer.serverGroups.some((toCheck: ServerGroup) => {
                let result = false;
                if (toCheck.name === fromParams.name) {
                  fromApp = toCheck;
                  result = true;
                }
                return result;
              });
            }
          });
        }

        this.serverGroup = Object.assign(fromApp, serverGroupDetails);
        this.state.loading = false;
      });
  }
}

export const APPENGINE_SERVER_GROUP_DETAILS_CTRL = 'spinnaker.appengine.serverGroup.details.controller';

module(APPENGINE_SERVER_GROUP_DETAILS_CTRL, [
    APPENGINE_SERVER_GROUP_WRITER,
    CONFIRMATION_MODAL_SERVICE,
    SERVER_GROUP_WARNING_MESSAGE_SERVICE,
    SERVER_GROUP_READER,
    SERVER_GROUP_WRITER,
    require('core/serverGroup/configure/common/runningExecutions.service.js'),
  ])
  .controller('appengineServerGroupDetailsCtrl', AppengineServerGroupDetailsController);
