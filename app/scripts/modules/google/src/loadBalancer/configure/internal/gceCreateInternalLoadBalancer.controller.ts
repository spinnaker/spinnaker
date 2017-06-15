import { module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';
import * as _ from 'lodash';

import {
  ACCOUNT_SERVICE,
  AccountService,
  Application,
  IAccount,
  ICredentials,
  INFRASTRUCTURE_CACHE_SERVICE,
  InfrastructureCacheService,
  IRegion,
  LOAD_BALANCER_WRITE_SERVICE,
  NAMING_SERVICE,
  NamingService,
  TASK_MONITOR_BUILDER,
  TaskMonitorBuilder
} from '@spinnaker/core';

import { IGceBackendService, IGceHealthCheck, IGceLoadBalancer, IGceNetwork, IGceSubnet, IGceListener } from 'google/domain/index';
import { GCEProviderSettings } from 'google/gce.settings';
import { CommonGceLoadBalancerCtrl } from '../common/commonLoadBalancer.controller';
import {
  GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
  GceCommonLoadBalancerCommandBuilder
} from '../common/commonLoadBalancerCommandBuilder.service';
import { GCE_HEALTH_CHECK_SELECTOR_COMPONENT } from '../common/healthCheck.component';

class ViewState {
  constructor(public sessionAffinity: string) {}
}

interface IListKeyedByAccount {
  [account: string]: string[];
}

interface IPrivateScope extends ng.IScope {
  $$destroyed: boolean;
}

export class InternalLoadBalancer implements IGceLoadBalancer {
  public name: string;
  public stack: string;
  public detail: string;
  public loadBalancerName: string;
  public listeners: IGceListener[];
  public ports: any;
  public ipProtocol = 'TCP';
  public loadBalancerType = 'INTERNAL';
  public credentials: string;
  public account: string;
  public project: string;
  public network = 'default';
  public subnet: string;
  public cloudProvider = 'gce';
  public backendService: IGceBackendService = { healthCheck: { healthCheckType: 'TCP' } } as IGceBackendService;

  constructor (public region: string) {}
}

class InternalLoadBalancerCtrl extends CommonGceLoadBalancerCtrl implements ng.IComponentController {
  public pages: any = {
    'location': require('./createLoadBalancerProperties.html'),
    'listener': require('./listeners/listeners.html'),
    'healthCheck': require('../common/commonHealthCheckPage.html'),
    'advancedSettings': require('../common/commonAdvancedSettingsPage.html'),
  };
  public sessionAffinityViewToModelMap: any = {
    'None': 'NONE',
    'Client IP': 'CLIENT_IP',
    'Client IP and protocol': 'CLIENT_IP_PROTO',
    'Client IP, port and protocol': 'CLIENT_IP_PORT_PROTO',
  };
  public accounts: ICredentials[];
  public regions: string[];
  public networks: IGceNetwork[];
  public networkOptions: string[];
  public subnets: IGceSubnet[];
  public subnetOptions: string[];
  public healthChecksByAccountAndType: {[account: string]: {[healthCheckType: string]: IGceHealthCheck[]}};

  // The 'by account' maps populate the corresponding 'existing names' lists below.
  public existingLoadBalancerNamesByAccount: IListKeyedByAccount;
  public existingHealthCheckNamesByAccount: IListKeyedByAccount;
  public existingLoadBalancerNames: string[];
  public existingHealthCheckNames: string[];

  public viewState: ViewState = new ViewState('None');
  public taskMonitor: any;
  public originalListeners: IGceListener[];

  private sessionAffinityModelToViewMap: any = _.invert(this.sessionAffinityViewToModelMap);

  constructor (public $scope: IPrivateScope,
               public application: Application,
               public $uibModalInstance: IModalInstanceService,
               private loadBalancer: InternalLoadBalancer,
               private gceCommonLoadBalancerCommandBuilder: GceCommonLoadBalancerCommandBuilder,
               private isNew: boolean,
               private accountService: AccountService,
               private namingService: NamingService,
               private wizardSubFormValidation: any,
               private gceXpnNamingService: any,
               private taskMonitorBuilder: TaskMonitorBuilder,
               $state: StateService,
               infrastructureCaches: InfrastructureCacheService,
               private internalLoadBalancerTransformer: any,
               private gceMultipleListenerLoadBalancerWriter: any) {
    'ngInject';
    super($scope, application, $uibModalInstance, $state, infrastructureCaches);
  }

  public $onInit (): void {
    this.gceCommonLoadBalancerCommandBuilder
      .getBackingData(['existingLoadBalancerNamesByAccount', 'accounts', 'networks', 'subnets', 'healthChecks'])
      .then((backingData) => {
        if (!this.isNew) {
          this.initializeEditMode();
        } else {
          this.originalListeners = [];
          this.loadBalancer = new InternalLoadBalancer(
            GCEProviderSettings
            ? GCEProviderSettings.defaults.region
            : null);
        }

        this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);

        const accountNames: string[] = backingData.accounts.map((account: IAccount) => account.name);
        if (accountNames.length && !accountNames.includes(this.loadBalancer.account)) {
          this.loadBalancer.credentials = accountNames[0];
        } else {
          this.loadBalancer.credentials = this.loadBalancer.account;
        }

        this.accounts = backingData.accounts;
        this.networks = backingData.networks;
        this.subnets = backingData.subnets;
        this.updateSubnetOptions();
        this.existingLoadBalancerNamesByAccount = backingData.existingLoadBalancerNamesByAccount;
        this.healthChecksByAccountAndType = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthChecksByAccountAndType(backingData.healthChecks as IGceHealthCheck[]);

        // We don't count the load balancer's health check in the existing health checks list.
        const healthCheckNamesToOmit = this.isNew ? [] : [this.loadBalancer.backendService.healthCheck.name];
        this.existingHealthCheckNamesByAccount = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthCheckNamesByAccount(backingData.healthChecks as IGceHealthCheck[], healthCheckNamesToOmit);

        this.accountUpdated();

        this.wizardSubFormValidation.config({scope: this.$scope, form: 'form'})
          .register({page: 'location', subForm: 'locationForm'})
          .register({page: 'listener', subForm: 'listenerForm'})
          .register({page: 'healthCheck', subForm: 'healthCheckForm'})
          .register({page: 'advancedSettings', subForm: 'advancedSettingsForm'});

        this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
          application: this.application,
          title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
          modalInstance: this.$uibModalInstance,
          onTaskComplete: () => this.onTaskComplete(this.loadBalancer),
        });
    });
  }

  public onHealthCheckRefresh (): void {
    this.gceCommonLoadBalancerCommandBuilder.getBackingData(['healthChecks'])
      .then((data) => {
        this.healthChecksByAccountAndType = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthChecksByAccountAndType(data.healthChecks as IGceHealthCheck[]);

        const healthCheckNamesToOmit = this.isNew ? [] : [this.loadBalancer.backendService.healthCheck.name];
        this.existingHealthCheckNamesByAccount = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthCheckNamesByAccount(data.healthChecks as IGceHealthCheck[], healthCheckNamesToOmit);
      })
  }

  public networkUpdated (): void {
    this.updateSubnetOptions();
    if (!this.subnetOptions.includes(this.loadBalancer.subnet)) {
      this.loadBalancer.subnet = this.subnetOptions[0];
    }
  }

  private updateSubnetOptions (): void {
    this.subnetOptions = this.subnets
      .filter(subnet => {
        return subnet.region === this.loadBalancer.region &&
            (subnet.account === this.loadBalancer.credentials || subnet.account === this.loadBalancer.account) &&
            subnet.network === this.loadBalancer.network;
      }).map((subnet) => subnet.id);
  }

  public protocolUpdated (): void {
    if (this.loadBalancer.ipProtocol === 'UDP') {
      this.viewState = new ViewState('None');
      this.loadBalancer.backendService.sessionAffinity = 'NONE';
    }
  }

  public accountUpdated (): void {
    const existingHealthCheckNames =
      _.get<any, string[]>(this, ['existingHealthCheckNamesByAccount', this.loadBalancer.credentials]);
    this.existingHealthCheckNames = existingHealthCheckNames || [];

    const existingLoadBalancerNames =
      _.get<any, string[]>(this, ['existingLoadBalancerNamesByAccount', this.loadBalancer.credentials]);
    this.existingLoadBalancerNames = existingLoadBalancerNames || [];

    this.networkOptions = this.networks
      .filter((network: IGceNetwork) => network.account === this.loadBalancer.credentials)
      .map((network) => network.id);

    this.accountService.getRegionsForAccount(this.loadBalancer.credentials)
      .then((regions: IRegion[]) => {
        this.regions = regions.map((region: IRegion) => region.name);
        this.networkUpdated();
      });
  }

  public regionUpdated (): void {
    this.networkUpdated();
  }

  public updateName (): void {
    this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);
  }

  public addListener (): void {
    this.updateSubnetOptions();
    this.loadBalancer.listeners = this.loadBalancer.listeners || [];
    this.loadBalancer.listeners.push({} as IGceListener);
  }

  public removeListener (index: number): void {
    this.loadBalancer.listeners.splice(index, 1);
  }

 public setSessionAffinity (viewState: ViewState): void {
    this.loadBalancer.backendService.sessionAffinity = this.sessionAffinityViewToModelMap[viewState.sessionAffinity];
  }

  public submit (): void {
    const serializedLoadBalancers = this.internalLoadBalancerTransformer.serialize(this.loadBalancer, this.originalListeners);
    const descriptor = this.isNew ? 'Create' : 'Update';

    this.taskMonitor.submit(() => this.gceMultipleListenerLoadBalancerWriter.upsertLoadBalancer(serializedLoadBalancers, this.application, descriptor));
  }

  private initializeEditMode (): void {
    this.originalListeners = _.cloneDeep(this.loadBalancer.listeners);
    this.loadBalancer.ports = this.loadBalancer.ports.join(', ');
    this.loadBalancer.subnet = this.gceXpnNamingService.decorateXpnResourceIfNecessary(this.loadBalancer.project, this.loadBalancer.subnet);
    this.loadBalancer.network = this.gceXpnNamingService.decorateXpnResourceIfNecessary(this.loadBalancer.project, this.loadBalancer.network);

    this.loadBalancer.listeners.forEach((listener: any) =>  {
      let { stack, freeFormDetails } = this.namingService.parseLoadBalancerName(listener.name);
      listener.stack = stack;
      listener.detail = freeFormDetails;
      listener.ports = listener.ports.join(', ');
      listener.subnet = this.loadBalancer.subnet;
      listener.created = true;
    });
    this.viewState = new ViewState(this.sessionAffinityModelToViewMap[this.loadBalancer.backendService.sessionAffinity]);
  }
}

export const GCE_INTERNAL_LOAD_BALANCER_CTRL = 'spinnaker.gce.internalLoadBalancer.controller';

module(GCE_INTERNAL_LOAD_BALANCER_CTRL, [
    GCE_HEALTH_CHECK_SELECTOR_COMPONENT,
    GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
    ACCOUNT_SERVICE,
    NAMING_SERVICE,
    INFRASTRUCTURE_CACHE_SERVICE,
    require('google/common/xpnNaming.gce.service.js'),
    LOAD_BALANCER_WRITE_SERVICE,
    TASK_MONITOR_BUILDER,
    require('./listeners/listener.component.js'),
    require('./transformer.service.js'),
    require('../common/multipleListenerLoadBalancer.write.service.js'),
  ])
  .controller('gceInternalLoadBalancerCtrl', InternalLoadBalancerCtrl);
