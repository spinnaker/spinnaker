import {module} from 'angular';
import {IStateService} from 'angular-ui-router';
import * as _ from 'lodash';

import {ICredentials} from 'core/domain/ICredentials';
import {Application} from 'core/application/application.model';
import {IGceSubnet, IGceNetwork, IGceHealthCheck, IGceBackendService, IGceLoadBalancer} from 'google/domain/index';
import {GCE_HEALTH_CHECK_SELECTOR_COMPONENT} from '../common/healthCheck.component';
import {GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
        GceCommonLoadBalancerCommandBuilder} from '../common/commonLoadBalancerCommandBuilder.service';
import {ACCOUNT_SERVICE, AccountService, IRegion, IAccount} from 'core/account/account.service';
import {CommonGceLoadBalancerCtrl} from '../common/commonLoadBalancer.controller';

class ViewState {
  constructor(public sessionAffinity: string) {}
}

interface IListKeyedByAccount {
  [account: string]: string[];
}

interface IPrivateScope extends ng.IScope {
  $$destroyed: boolean;
}

class InternalLoadBalancer implements IGceLoadBalancer {
  stack: string;
  detail: string;
  loadBalancerName: string;
  ports: any;
  ipProtocol: string = 'TCP';
  loadBalancerType: string = 'INTERNAL';
  credentials: string;
  account: string;
  network: string = 'default';
  subnet: string;
  backendService: IGceBackendService = { healthCheck: { healthCheckType: 'TCP' } } as IGceBackendService;

  constructor (public region: string) {}
}

class InternalLoadBalancerCtrl extends CommonGceLoadBalancerCtrl implements ng.IComponentController {
  public pages: any = {
    'location': require('./createLoadBalancerProperties.html'),
    'listener': require('./listener.html'),
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

  private sessionAffinityModelToViewMap: any = _.invert(this.sessionAffinityViewToModelMap);

  static get $inject () { return ['$scope',
                                  'application',
                                  '$uibModalInstance',
                                  'loadBalancer',
                                  'gceCommonLoadBalancerCommandBuilder',
                                  'isNew',
                                  'accountService',
                                  'loadBalancerWriter',
                                  'wizardSubFormValidation',
                                  'taskMonitorService',
                                  'settings',
                                  '$state',
                                  'infrastructureCaches']; }

  constructor (public $scope: IPrivateScope,
               public application: Application,
               public $uibModalInstance: any,
               private loadBalancer: InternalLoadBalancer,
               private gceCommonLoadBalancerCommandBuilder: GceCommonLoadBalancerCommandBuilder,
               private isNew: boolean,
               private accountService: AccountService,
               private loadBalancerWriter: any,
               private wizardSubFormValidation: any,
               private taskMonitorService: any,
               private settings: any,
               $state: IStateService,
               infrastructureCaches: any) {
    super($scope, application, $uibModalInstance, $state, infrastructureCaches);
  }

  public $onInit (): void {
    this.gceCommonLoadBalancerCommandBuilder
      .getBackingData(['existingLoadBalancerNamesByAccount', 'accounts', 'networks', 'subnets', 'healthChecks'])
      .then((backingData) => {
        if (!this.isNew) {
          this.initializeEditMode();
        } else {
          this.loadBalancer = new InternalLoadBalancer(
            this.settings.providers.gce
            ? this.settings.providers.gce.defaults.region
            : null);
        }

        this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);

        let accountNames: string[] = backingData.accounts.map((account: IAccount) => account.name);
        if (accountNames.length && !accountNames.includes(this.loadBalancer.account)) {
          this.loadBalancer.credentials = accountNames[0];
        } else {
          this.loadBalancer.credentials = this.loadBalancer.account;
        }

        this.accounts = backingData.accounts;
        this.networks = backingData.networks;
        this.subnets = backingData.subnets;
        this.existingLoadBalancerNamesByAccount = backingData.existingLoadBalancerNamesByAccount;
        this.healthChecksByAccountAndType = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthChecksByAccountAndType(backingData.healthChecks as IGceHealthCheck[]);

        // We don't count the load balancer's health check in the existing health checks list.
        let healthCheckNamesToOmit = this.isNew ? [] : [this.loadBalancer.backendService.healthCheck.name];
        this.existingHealthCheckNamesByAccount = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthCheckNamesByAccount(backingData.healthChecks as IGceHealthCheck[], healthCheckNamesToOmit);

        this.accountUpdated();

        this.wizardSubFormValidation.config({scope: this.$scope, form: 'form'})
          .register({page: 'location', subForm: 'locationForm'})
          .register({page: 'listener', subForm: 'listenerForm'})
          .register({page: 'healthCheck', subForm: 'healthCheckForm'})
          .register({page: 'advancedSettings', subForm: 'advancedSettingsForm'});

        this.taskMonitor = this.taskMonitorService.buildTaskMonitor({
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

        let healthCheckNamesToOmit = this.isNew ? [] : [this.loadBalancer.backendService.healthCheck.name];
        this.existingHealthCheckNamesByAccount = this.gceCommonLoadBalancerCommandBuilder
          .groupHealthCheckNamesByAccount(data.healthChecks as IGceHealthCheck[], healthCheckNamesToOmit);
      });
  }

  public networkUpdated (): void {
    this.subnetOptions = this.subnets
      .filter((subnet) => {
        return subnet.region === this.loadBalancer.region &&
               (subnet.account === this.loadBalancer.credentials || subnet.account === this.loadBalancer.account) &&
               subnet.network === this.loadBalancer.network;
      }).map((subnet) => subnet.name);

    if (!this.subnetOptions.includes(this.loadBalancer.subnet)) {
      this.loadBalancer.subnet = this.subnetOptions[0];
    }
  }

  public protocolUpdated (): void {
    if (this.loadBalancer.ipProtocol === 'UDP') {
      this.viewState = new ViewState('None');
      this.loadBalancer.backendService.sessionAffinity = 'NONE';
    }
  }

  public accountUpdated (): void {
    let existingHealthCheckNames =
      _.get<any, string[]>(this, ['existingHealthCheckNamesByAccount', this.loadBalancer.credentials]);
    this.existingHealthCheckNames = existingHealthCheckNames || [];

    let existingLoadBalancerNames =
      _.get<any, string[]>(this, ['existingLoadBalancerNamesByAccount', this.loadBalancer.credentials]);
    this.existingLoadBalancerNames = existingLoadBalancerNames || [];

    this.networkOptions = this.networks
      .filter((network: IGceNetwork) => network.account === this.loadBalancer.credentials)
      .map((network) => network.name);

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

  public setSessionAffinity (viewState: ViewState): void {
    this.loadBalancer.backendService.sessionAffinity = this.sessionAffinityViewToModelMap[viewState.sessionAffinity];
  }

  public submit (): void {
    let descriptor = this.isNew ? 'Create' : 'Update';
    let toSubmitLoadBalancer = _.cloneDeep(this.loadBalancer) as any;
    toSubmitLoadBalancer.ports = toSubmitLoadBalancer.ports.split(',').map((port: string) => port.trim());
    toSubmitLoadBalancer.provider = 'gce';
    toSubmitLoadBalancer.name = toSubmitLoadBalancer.loadBalancerName;
    toSubmitLoadBalancer.backendService.name = toSubmitLoadBalancer.loadBalancerName;
    delete toSubmitLoadBalancer.instances;

    this.taskMonitor.submit(() => this.loadBalancerWriter.upsertLoadBalancer(toSubmitLoadBalancer,
                                                                             this.application,
                                                                             descriptor,
                                                                             { healthCheck: {} }));
  }

  private initializeEditMode (): void {
    this.loadBalancer.ports = this.loadBalancer.ports.join(', ');
    this.loadBalancer.subnet = this.loadBalancer.subnet.split('/').pop();
    this.loadBalancer.network = this.loadBalancer.network.split('/').pop();
    this.viewState = new ViewState(this.sessionAffinityModelToViewMap[this.loadBalancer.backendService.sessionAffinity]);
  }
}

export const GCE_INTERNAL_LOAD_BALANCER_CTRL = 'spinnaker.gce.internalLoadBalancer.controller';

module(GCE_INTERNAL_LOAD_BALANCER_CTRL, [
    GCE_HEALTH_CHECK_SELECTOR_COMPONENT,
    GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
    ACCOUNT_SERVICE,
    require('core/cache/infrastructureCaches.js'),
    require('core/modal/wizard/wizardSubFormValidation.service.js'),
    require('core/loadBalancer/loadBalancer.write.service.js'),
    require('core/task/monitor/taskMonitorService.js'),
  ])
  .controller('gceInternalLoadBalancerCtrl', InternalLoadBalancerCtrl);
