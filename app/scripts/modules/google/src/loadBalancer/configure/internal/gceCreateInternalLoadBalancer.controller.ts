import { StateService } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import _ from 'lodash';

import {
  AccountService,
  Application,
  IAccount,
  ICredentials,
  IRegion,
  LoadBalancerWriter,
  TaskMonitor,
} from '@spinnaker/core';

import { CommonGceLoadBalancerCtrl } from '../common/commonLoadBalancer.controller';
import {
  GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
  GceCommonLoadBalancerCommandBuilder,
} from '../common/commonLoadBalancerCommandBuilder.service';
import { GCE_HEALTH_CHECK_SELECTOR_COMPONENT } from '../common/healthCheck.component';
import { GOOGLE_COMMON_XPNNAMING_GCE_SERVICE } from '../../../common/xpnNaming.gce.service';
import { IGceBackendService, IGceHealthCheck, IGceLoadBalancer, IGceNetwork, IGceSubnet } from '../../../domain/index';
import { GCEProviderSettings } from '../../../gce.settings';

class ViewState {
  constructor(public sessionAffinity: string) {}
}

interface IListKeyedByAccount {
  [account: string]: string[];
}

interface IPrivateScope extends IScope {
  $$destroyed: boolean;
}

class InternalLoadBalancer implements IGceLoadBalancer {
  public name: string;
  public stack: string;
  public detail: string;
  public loadBalancerName: string;
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

  constructor(public region: string) {}
}

class InternalLoadBalancerCtrl extends CommonGceLoadBalancerCtrl implements IController {
  public pages: any = {
    location: require('./createLoadBalancerProperties.html'),
    listener: require('./listener.html'),
    healthCheck: require('../common/commonHealthCheckPage.html'),
    advancedSettings: require('../common/commonAdvancedSettingsPage.html'),
  };
  public sessionAffinityViewToModelMap: any = {
    None: 'NONE',
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
  public healthChecksByAccountAndType: { [account: string]: { [healthCheckType: string]: IGceHealthCheck[] } };

  // The 'by account' maps populate the corresponding 'existing names' lists below.
  public existingLoadBalancerNamesByAccount: IListKeyedByAccount;
  public existingHealthCheckNamesByAccount: IListKeyedByAccount;
  public existingLoadBalancerNames: string[];
  public existingHealthCheckNames: string[];

  public viewState: ViewState = new ViewState('None');
  public taskMonitor: any;

  private sessionAffinityModelToViewMap: any = _.invert(this.sessionAffinityViewToModelMap);

  public static $inject = [
    '$scope',
    'application',
    '$uibModalInstance',
    'loadBalancer',
    'gceCommonLoadBalancerCommandBuilder',
    'isNew',
    'wizardSubFormValidation',
    'gceXpnNamingService',
    '$state',
  ];
  constructor(
    public $scope: IPrivateScope,
    public application: Application,
    public $uibModalInstance: IModalInstanceService,
    private loadBalancer: InternalLoadBalancer,
    private gceCommonLoadBalancerCommandBuilder: GceCommonLoadBalancerCommandBuilder,
    private isNew: boolean,
    private wizardSubFormValidation: any,
    private gceXpnNamingService: any,
    $state: StateService,
  ) {
    super($scope, application, $uibModalInstance, $state);
  }

  public $onInit(): void {
    this.gceCommonLoadBalancerCommandBuilder
      .getBackingData(['existingLoadBalancerNamesByAccount', 'accounts', 'networks', 'subnets', 'healthChecks'])
      .then((backingData) => {
        if (!this.isNew) {
          this.initializeEditMode();
        } else {
          this.loadBalancer = new InternalLoadBalancer(
            GCEProviderSettings ? GCEProviderSettings.defaults.region : null,
          );
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
        this.existingLoadBalancerNamesByAccount = backingData.existingLoadBalancerNamesByAccount;
        this.healthChecksByAccountAndType = this.gceCommonLoadBalancerCommandBuilder.groupHealthChecksByAccountAndType(
          backingData.healthChecks as IGceHealthCheck[],
        );

        // We don't count the load balancer's health check in the existing health checks list.
        const healthCheckNamesToOmit = this.isNew ? [] : [this.loadBalancer.backendService.healthCheck.name];
        this.existingHealthCheckNamesByAccount = this.gceCommonLoadBalancerCommandBuilder.groupHealthCheckNamesByAccount(
          backingData.healthChecks as IGceHealthCheck[],
          healthCheckNamesToOmit,
        );

        this.accountUpdated();

        this.wizardSubFormValidation
          .config({ scope: this.$scope, form: 'form' })
          .register({ page: 'location', subForm: 'locationForm' })
          .register({ page: 'listener', subForm: 'listenerForm' })
          .register({ page: 'healthCheck', subForm: 'healthCheckForm' })
          .register({ page: 'advancedSettings', subForm: 'advancedSettingsForm' });

        this.taskMonitor = new TaskMonitor({
          application: this.application,
          title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
          modalInstance: this.$uibModalInstance,
          onTaskComplete: () => this.onTaskComplete(this.loadBalancer),
        });
      });
  }

  public onHealthCheckRefresh(): void {
    this.gceCommonLoadBalancerCommandBuilder.getBackingData(['healthChecks']).then((data) => {
      this.healthChecksByAccountAndType = this.gceCommonLoadBalancerCommandBuilder.groupHealthChecksByAccountAndType(
        data.healthChecks as IGceHealthCheck[],
      );

      const healthCheckNamesToOmit = this.isNew ? [] : [this.loadBalancer.backendService.healthCheck.name];
      this.existingHealthCheckNamesByAccount = this.gceCommonLoadBalancerCommandBuilder.groupHealthCheckNamesByAccount(
        data.healthChecks as IGceHealthCheck[],
        healthCheckNamesToOmit,
      );
    });
  }

  public networkUpdated(): void {
    this.subnetOptions = this.subnets
      .filter((subnet) => {
        return (
          subnet.region === this.loadBalancer.region &&
          (subnet.account === this.loadBalancer.credentials || subnet.account === this.loadBalancer.account) &&
          subnet.network === this.loadBalancer.network
        );
      })
      .map((subnet) => subnet.id);

    if (!this.subnetOptions.includes(this.loadBalancer.subnet)) {
      this.loadBalancer.subnet = this.subnetOptions[0];
    }
  }

  public protocolUpdated(): void {
    if (this.loadBalancer.ipProtocol === 'UDP') {
      this.viewState = new ViewState('None');
      this.loadBalancer.backendService.sessionAffinity = 'NONE';
    }
  }

  public accountUpdated(): void {
    const existingHealthCheckNames = _.get<any, string[]>(this, [
      'existingHealthCheckNamesByAccount',
      this.loadBalancer.credentials,
    ]);
    this.existingHealthCheckNames = existingHealthCheckNames || [];

    const existingLoadBalancerNames = _.get<any, string[]>(this, [
      'existingLoadBalancerNamesByAccount',
      this.loadBalancer.credentials,
    ]);
    this.existingLoadBalancerNames = existingLoadBalancerNames || [];

    this.networkOptions = this.networks
      .filter((network: IGceNetwork) => network.account === this.loadBalancer.credentials)
      .map((network) => network.id);

    AccountService.getRegionsForAccount(this.loadBalancer.credentials).then((regions: IRegion[]) => {
      this.regions = regions.map((region: IRegion) => region.name);
      this.networkUpdated();
    });
  }

  public regionUpdated(): void {
    this.networkUpdated();
  }

  public updateName(): void {
    this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);
  }

  public setSessionAffinity(viewState: ViewState): void {
    this.loadBalancer.backendService.sessionAffinity = this.sessionAffinityViewToModelMap[viewState.sessionAffinity];
  }

  public submit(): void {
    const descriptor = this.isNew ? 'Create' : 'Update';
    const toSubmitLoadBalancer = _.cloneDeep(this.loadBalancer) as any;
    toSubmitLoadBalancer.ports = toSubmitLoadBalancer.ports.split(',').map((port: string) => port.trim());
    toSubmitLoadBalancer.cloudProvider = 'gce';
    toSubmitLoadBalancer.name = toSubmitLoadBalancer.loadBalancerName;
    toSubmitLoadBalancer.backendService.name = toSubmitLoadBalancer.loadBalancerName;
    delete toSubmitLoadBalancer.instances;

    this.taskMonitor.submit(() =>
      LoadBalancerWriter.upsertLoadBalancer(toSubmitLoadBalancer, this.application, descriptor, {
        healthCheck: {},
      }),
    );
  }

  private initializeEditMode(): void {
    this.loadBalancer.ports = this.loadBalancer.ports.join(', ');
    this.loadBalancer.subnet = this.gceXpnNamingService.decorateXpnResourceIfNecessary(
      this.loadBalancer.project,
      this.loadBalancer.subnet,
    );
    this.loadBalancer.network = this.gceXpnNamingService.decorateXpnResourceIfNecessary(
      this.loadBalancer.project,
      this.loadBalancer.network,
    );
    this.viewState = new ViewState(
      this.sessionAffinityModelToViewMap[this.loadBalancer.backendService.sessionAffinity],
    );
  }
}

export const GCE_INTERNAL_LOAD_BALANCER_CTRL = 'spinnaker.gce.internalLoadBalancer.controller';

module(GCE_INTERNAL_LOAD_BALANCER_CTRL, [
  GCE_HEALTH_CHECK_SELECTOR_COMPONENT,
  GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
  GOOGLE_COMMON_XPNNAMING_GCE_SERVICE,
]).controller('gceInternalLoadBalancerCtrl', InternalLoadBalancerCtrl);
