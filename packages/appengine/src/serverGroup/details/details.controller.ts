import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { cloneDeep, map, mapValues, reduce } from 'lodash';

import {
  Application,
  ConfirmationModalService,
  IConfirmationModalParams,
  ILoadBalancer,
  IServerGroup,
  ITaskMonitorConfig,
  SERVER_GROUP_WRITER,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  ServerGroupWriter,
} from '@spinnaker/core';

import { AppengineHealth } from '../../common/appengineHealth';
import { AppengineServerGroupCommandBuilder } from '../configure/serverGroupCommandBuilder.service';
import { IAppengineLoadBalancer, IAppengineServerGroup } from '../../domain/index';
import { APPENGINE_SERVER_GROUP_WRITER, AppengineServerGroupWriter } from '../writer/serverGroup.write.service';

interface IPrivateScope extends IScope {
  $$destroyed: boolean;
}

interface IServerGroupFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

class AppengineServerGroupDetailsController implements IController {
  public state = { loading: true };
  public serverGroup: IAppengineServerGroup;

  private static buildExpectedAllocationsTable(expectedAllocations: { [key: string]: number }): string {
    const tableRows = map(expectedAllocations, (allocation, serverGroupName) => {
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

  public static $inject = [
    '$state',
    '$scope',
    '$uibModal',
    'serverGroup',
    'app',
    'serverGroupWriter',
    'appengineServerGroupWriter',
    'appengineServerGroupCommandBuilder',
  ];
  constructor(
    private $state: any,
    private $scope: IPrivateScope,
    private $uibModal: IModalService,
    serverGroup: IServerGroupFromStateParams,
    public app: Application,
    private serverGroupWriter: ServerGroupWriter,
    private appengineServerGroupWriter: AppengineServerGroupWriter,
    private appengineServerGroupCommandBuilder: AppengineServerGroupCommandBuilder,
  ) {
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

      const expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
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

      const expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
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
    const stateParams = {
      name: this.serverGroup.name,
      accountId: this.serverGroup.account,
      region: this.serverGroup.region,
    };

    const taskMonitor = {
      application: this.app,
      title: 'Destroying ' + this.serverGroup.name,
      onTaskComplete: () => {
        if (this.$state.includes('**.serverGroup', stateParams)) {
          this.$state.go('^');
        }
      },
    };

    const submitMethod = (params: any) => this.serverGroupWriter.destroyServerGroup(this.serverGroup, this.app, params);

    const confirmationModalParams = {
      header: 'Really destroy ' + this.serverGroup.name + '?',
      buttonText: 'Destroy ' + this.serverGroup.name,
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod,
      askForReason: true,
      platformHealthOnlyShowOverride: this.app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: AppengineHealth.PLATFORM,
      body: this.getBodyTemplate(this.serverGroup, this.app),
      interestingHealthProviderNames: [] as string[],
    };

    if (this.app.attributes.platformHealthOnlyShowOverride && this.app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  public enableServerGroup(): void {
    const taskMonitor: ITaskMonitorConfig = {
      application: this.app,
      title: 'Enabling ' + this.serverGroup.name,
    };

    const submitMethod = (params: any) =>
      this.serverGroupWriter.enableServerGroup(this.serverGroup, this.app, { ...params });

    const modalBody = `<div class="well well-sm">
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

    const confirmationModalParams = {
      header: 'Really enable ' + this.serverGroup.name + '?',
      buttonText: 'Enable ' + this.serverGroup.name,
      body: modalBody,
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      platformHealthOnlyShowOverride: this.app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: AppengineHealth.PLATFORM,
      submitMethod,
      askForReason: true,
      interestingHealthProviderNames: [] as string[],
    };

    if (this.app.attributes.platformHealthOnlyShowOverride && this.app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  public disableServerGroup(): void {
    const taskMonitor = {
      application: this.app,
      title: 'Disabling ' + this.serverGroup.name,
    };

    const submitMethod = (params: any) =>
      this.serverGroupWriter.disableServerGroup(this.serverGroup, this.app.name, params);

    const expectedAllocations = this.expectedAllocationsAfterDisableOperation(this.serverGroup, this.app);
    const modalBody = `<div class="well well-sm">
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

    const confirmationModalParams = {
      header: 'Really disable ' + this.serverGroup.name + '?',
      buttonText: 'Disable ' + this.serverGroup.name,
      body: modalBody,
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      platformHealthOnlyShowOverride: this.app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: AppengineHealth.PLATFORM,
      submitMethod,
      askForReason: true,
      interestingHealthProviderNames: [] as string[],
    };

    if (this.app.attributes.platformHealthOnlyShowOverride && this.app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  public stopServerGroup(): void {
    const taskMonitor = {
      application: this.app,
      title: 'Stopping ' + this.serverGroup.name,
    };

    const submitMethod = () => this.appengineServerGroupWriter.stopServerGroup(this.serverGroup, this.app);

    let modalBody: string;
    if (!this.serverGroup.disabled) {
      modalBody = `<div class="alert alert-danger">
          <p>Stopping this server group will scale it down to zero instances.</p>
          <p>
            This server group is currently serving traffic from <b>${this.serverGroup.loadBalancers[0]}</b>.
            Traffic directed to this server group after it has been stopped will not be handled.
          </p>
        </div>`;
    }

    const confirmationModalParams = {
      header: 'Really stop ' + this.serverGroup.name + '?',
      buttonText: 'Stop ' + this.serverGroup.name,
      account: this.serverGroup.account,
      body: modalBody,
      platformHealthOnlyShowOverride: this.app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: AppengineHealth.PLATFORM,
      taskMonitorConfig: taskMonitor,
      submitMethod,
      askForReason: true,
    };

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  public startServerGroup(): void {
    const taskMonitor = {
      application: this.app,
      title: 'Starting ' + this.serverGroup.name,
    };

    const submitMethod = () => this.appengineServerGroupWriter.startServerGroup(this.serverGroup, this.app);

    const confirmationModalParams = {
      header: 'Really start ' + this.serverGroup.name + '?',
      buttonText: 'Start ' + this.serverGroup.name,
      account: this.serverGroup.account,
      platformHealthOnlyShowOverride: this.app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: AppengineHealth.PLATFORM,
      taskMonitorConfig: taskMonitor,
      submitMethod,
      askForReason: true,
    };

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  public cloneServerGroup(): void {
    this.$uibModal.open({
      templateUrl: require('../configure/wizard/serverGroupWizard.html'),
      controller: 'appengineCloneServerGroupCtrl as ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Clone ' + this.serverGroup.name,
        application: () => this.app,
        serverGroup: () => this.serverGroup,
        serverGroupCommand: () =>
          this.appengineServerGroupCommandBuilder.buildServerGroupCommandFromExisting(this.app, this.serverGroup),
      },
    });
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
    const isFlex = this.serverGroup.env === 'FLEXIBLE';
    return isFlex || ['MANUAL', 'BASIC'].includes(this.serverGroup.scalingPolicy?.type);
  }

  private getBodyTemplate(serverGroup: IAppengineServerGroup, app: Application): string {
    let template = '';
    const params: IConfirmationModalParams = {};
    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, params);
    if (params.body) {
      template += params.body;
    }

    if (!serverGroup.disabled) {
      const expectedAllocations = this.expectedAllocationsAfterDisableOperation(serverGroup, app);

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

  private expectedAllocationsAfterDisableOperation(
    serverGroup: IServerGroup,
    app: Application,
  ): { [key: string]: number } {
    const loadBalancer = app.getDataSource('loadBalancers').data.find((toCheck: IAppengineLoadBalancer): boolean => {
      const allocations = toCheck.split?.allocations ?? {};
      const enabledServerGroups = Object.keys(allocations);
      return enabledServerGroups.includes(serverGroup.name);
    });

    if (loadBalancer) {
      let allocations = cloneDeep(loadBalancer.split.allocations);
      delete allocations[serverGroup.name];
      const denominator = reduce(allocations, (partialSum: number, allocation: number) => partialSum + allocation, 0);
      const precision = loadBalancer.split.shardBy === 'COOKIE' ? 1000 : 100;
      allocations = mapValues(
        allocations,
        (allocation) => Math.round((allocation / denominator) * precision) / precision,
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
      this.$state.go('^', null, { location: 'replace' });
    }
  }

  private extractServerGroup(fromParams: IServerGroupFromStateParams): PromiseLike<void> {
    return ServerGroupReader.getServerGroup(
      this.app.name,
      fromParams.accountId,
      fromParams.region,
      fromParams.name,
    ).then((serverGroupDetails: IServerGroup) => {
      let fromApp = this.app.getDataSource('serverGroups').data.find((toCheck: IServerGroup) => {
        return (
          toCheck.name === fromParams.name &&
          toCheck.account === fromParams.accountId &&
          toCheck.region === fromParams.region
        );
      });

      if (!fromApp) {
        this.app.getDataSource('loadBalancers').data.some((loadBalancer: ILoadBalancer) => {
          if (loadBalancer.account === fromParams.accountId) {
            return loadBalancer.serverGroups.some((toCheck: IServerGroup) => {
              let result = false;
              if (toCheck.name === fromParams.name) {
                fromApp = toCheck;
                result = true;
              }
              return result;
            });
          } else {
            return false;
          }
        });
      }

      this.serverGroup = { ...serverGroupDetails, ...fromApp };
      this.state.loading = false;
    });
  }
}

export const APPENGINE_SERVER_GROUP_DETAILS_CTRL = 'spinnaker.appengine.serverGroup.details.controller';

module(APPENGINE_SERVER_GROUP_DETAILS_CTRL, [APPENGINE_SERVER_GROUP_WRITER, SERVER_GROUP_WRITER]).controller(
  'appengineServerGroupDetailsCtrl',
  AppengineServerGroupDetailsController,
);
