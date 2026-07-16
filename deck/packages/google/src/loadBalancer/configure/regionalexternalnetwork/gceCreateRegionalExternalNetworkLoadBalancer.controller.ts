import type { StateService } from '@uirouter/angularjs';
import type { IController, IScope } from 'angular';
import { module } from 'angular';
import type { IModalInstanceService } from 'angular-ui-bootstrap';
import _ from 'lodash';

import type { Application, IAccount, ICredentials, IRegion } from '@spinnaker/core';
import { AccountService, LoadBalancerWriter, TaskMonitor } from '@spinnaker/core';

import { GCE_ADDRESS_READER } from '../../../address/address.reader';
import type { IGceAddress } from '../../../address/address.reader';
import { GCE_ADDRESS_SELECTOR } from '../common/addressSelector.component';
import { CommonGceLoadBalancerCtrl } from '../common/commonLoadBalancer.controller';
import type { GceCommonLoadBalancerCommandBuilder } from '../common/commonLoadBalancerCommandBuilder.service';
import { GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER } from '../common/commonLoadBalancerCommandBuilder.service';
import { GCE_HEALTH_CHECK_SELECTOR_COMPONENT } from '../common/healthCheck.component';
import type { IGceBackendService, IGceHealthCheck, IGceLoadBalancer } from '../../../domain/index';
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

class RegionalExternalNetworkLoadBalancer implements IGceLoadBalancer {
  public name: string;
  public stack: string;
  public detail: string;
  public loadBalancerName: string;
  public ports: any;
  public ipProtocol = 'TCP';
  public loadBalancerType = 'REGIONAL_EXTERNAL_NETWORK';
  public credentials: string;
  public account: string;
  public project: string;
  public cloudProvider = 'gce';
  public ipAddress: string;
  public networkTier: string;
  public backendService: IGceBackendService = { healthCheck: { healthCheckType: 'TCP' } } as IGceBackendService;

  constructor(public region: string) {}
}

class RegionalExternalNetworkLoadBalancerCtrl extends CommonGceLoadBalancerCtrl implements IController {
  public pages: any = {
    location: require('./createLoadBalancerProperties.html'),
    listener: require('./listener.html'),
    healthCheck: require('../common/commonHealthCheckPage.html'),
    advancedSettings: require('./advancedSettings.html'),
  };
  public sessionAffinityViewToModelMap: any = {
    None: 'NONE',
    'Client IP': 'CLIENT_IP',
    'Client IP and protocol': 'CLIENT_IP_PROTO',
    'Client IP, port and protocol': 'CLIENT_IP_PORT_PROTO',
  };
  public accounts: ICredentials[];
  public regions: string[];
  public healthChecksByAccountAndType: { [account: string]: { [healthCheckType: string]: IGceHealthCheck[] } };
  public addresses: IGceAddress[] = [];
  public existingLoadBalancerNamesByAccount: IListKeyedByAccount;
  public existingHealthCheckNamesByAccount: IListKeyedByAccount;
  public existingLoadBalancerNames: string[];
  public existingHealthCheckNames: string[];
  public viewState: ViewState = new ViewState('None');
  public taskMonitor: any;

  public sessionAffinityModelToViewMap: any = _.invert(this.sessionAffinityViewToModelMap);

  public static $inject = [
    '$scope',
    'application',
    '$uibModalInstance',
    'loadBalancer',
    'gceCommonLoadBalancerCommandBuilder',
    'gceAddressReader',
    'isNew',
    'forPipelineConfig',
    'wizardSubFormValidation',
    '$state',
  ];
  constructor(
    public $scope: IPrivateScope,
    public application: Application,
    public $uibModalInstance: IModalInstanceService,
    private loadBalancer: RegionalExternalNetworkLoadBalancer,
    private gceCommonLoadBalancerCommandBuilder: GceCommonLoadBalancerCommandBuilder,
    private gceAddressReader: { listAddresses(region?: string): PromiseLike<IGceAddress[]> },
    private isNew: boolean,
    private forPipelineConfig: boolean,
    private wizardSubFormValidation: any,
    $state: StateService,
  ) {
    super($scope, application, $uibModalInstance, $state);
  }

  public $onInit(): void {
    this.gceCommonLoadBalancerCommandBuilder
      .getBackingData(['existingLoadBalancerNamesByAccount', 'accounts', 'healthChecks'])
      .then((backingData) => {
        if (!this.isNew) {
          this.initializeEditMode();
        } else {
          this.loadBalancer = new RegionalExternalNetworkLoadBalancer(
            GCEProviderSettings ? GCEProviderSettings.defaults.region : null,
          );
        }

        this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);

        const accountNames: string[] = backingData.accounts.map((account: IAccount) => account.name);
        this.loadBalancer.credentials =
          accountNames.length && !accountNames.includes(this.loadBalancer.account)
            ? accountNames[0]
            : this.loadBalancer.account;

        this.accounts = backingData.accounts;
        this.existingLoadBalancerNamesByAccount = backingData.existingLoadBalancerNamesByAccount;
        this.healthChecksByAccountAndType = this.gceCommonLoadBalancerCommandBuilder.groupHealthChecksByAccountAndType(
          backingData.healthChecks as IGceHealthCheck[],
        );

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
      this.existingHealthCheckNames =
        _.get<any, string[]>(this, ['existingHealthCheckNamesByAccount', this.loadBalancer.credentials]) || [];
    });
  }

  public protocolUpdated(): void {}

  public accountUpdated(): void {
    this.existingHealthCheckNames =
      _.get<any, string[]>(this, ['existingHealthCheckNamesByAccount', this.loadBalancer.credentials]) || [];
    this.existingLoadBalancerNames =
      _.get<any, string[]>(this, ['existingLoadBalancerNamesByAccount', this.loadBalancer.credentials]) || [];

    AccountService.getRegionsForAccount(this.loadBalancer.credentials).then((regions: IRegion[]) => {
      this.regions = regions.map((region: IRegion) => region.name);
      this.regionUpdated();
    });
  }

  public regionUpdated(): void {
    this.addresses = [];
    this.gceAddressReader.listAddresses(this.loadBalancer.region).then((addresses) => {
      this.addresses = addresses.filter((address) => address.addressType === 'EXTERNAL');
    });
  }

  public onAddressSelect(address: IGceAddress): void {
    this.loadBalancer.ipAddress = address?.address || null;
    this.loadBalancer.networkTier = address?.networkTier || null;
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

    if (this.forPipelineConfig) {
      Object.assign(toSubmitLoadBalancer, { healthCheck: {} });
      this.$uibModalInstance.close(toSubmitLoadBalancer);
      return;
    }

    this.taskMonitor.submit(() =>
      LoadBalancerWriter.upsertLoadBalancer(toSubmitLoadBalancer, this.application, descriptor, {
        healthCheck: {},
      }),
    );
  }

  private initializeEditMode(): void {
    this.loadBalancer.ports = this.loadBalancer.ports.join(', ');
    this.viewState = new ViewState(
      this.sessionAffinityModelToViewMap[this.loadBalancer.backendService.sessionAffinity],
    );
  }
}

export const GCE_REGIONAL_EXTERNAL_NETWORK_LOAD_BALANCER_CTRL =
  'spinnaker.gce.regionalExternalNetworkLoadBalancer.controller';

module(GCE_REGIONAL_EXTERNAL_NETWORK_LOAD_BALANCER_CTRL, [
  GCE_ADDRESS_READER,
  GCE_ADDRESS_SELECTOR,
  GCE_HEALTH_CHECK_SELECTOR_COMPONENT,
  GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
]).controller('gceRegionalExternalNetworkLoadBalancerCtrl', RegionalExternalNetworkLoadBalancerCtrl);
