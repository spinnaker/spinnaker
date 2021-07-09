import { StateService } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import _ from 'lodash';

import {
  AccountService,
  Application,
  IAccount,
  ICredentials,
  IInstance,
  ILoadBalancerUpsertCommand,
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
import { IGceBackendService, IGceHealthCheck, IGceLoadBalancer } from '../../../domain/index';
import { GCEProviderSettings } from '../../../gce.settings';

class ViewState {
  constructor(public sessionAffinity: string) {}
}

interface IListKeyedByAccount {
  [account: string]: string[];
}

interface ISslLoadBalancerUpsertDescription extends ILoadBalancerUpsertCommand {
  backendService: IGceBackendService;
  loadBalancerName: string;
  instances: IInstance[];
}

class SslLoadBalancer implements IGceLoadBalancer {
  public stack: string;
  public detail: string;
  public loadBalancerName: string;
  public portRange = '443';
  public ipProtocol = 'TCP';
  public instances: IInstance[];
  public loadBalancerType = 'SSL';
  public credentials: string;
  public account: string;
  public certificate: string;
  public backendService: IGceBackendService = { healthCheck: { healthCheckType: 'TCP' } } as IGceBackendService;
  public cloudProvider: string;
  public name: string;
  constructor(public region = 'global') {}
}

class SslLoadBalancerCtrl extends CommonGceLoadBalancerCtrl implements IController {
  public pages: any = {
    location: require('./createLoadBalancerProperties.html'),
    listener: require('./listener.html'),
    healthCheck: require('../common/commonHealthCheckPage.html'),
    advancedSettings: require('../common/commonAdvancedSettingsPage.html'),
    backendService: require('./backendService.html'),
  };
  public sessionAffinityViewToModelMap: any = {
    None: 'NONE',
    'Client IP': 'CLIENT_IP',
    'Generated Cookie': 'GENERATED_COOKIE',
  };
  public accounts: ICredentials[];
  public regions: string[];
  public certificateWrappers: any[];
  public healthChecksByAccountAndType: { [account: string]: { [healthCheckType: string]: IGceHealthCheck[] } };
  public portOptions: string[] = ['25', '43', '110', '143', '195', '443', '465', '587', '700', '993', '995'];

  // The 'by account' maps populate the corresponding 'existing names' lists below.
  public existingLoadBalancerNamesByAccount: IListKeyedByAccount;
  public existingHealthCheckNamesByAccount: IListKeyedByAccount;
  public existingLoadBalancerNames: string[];
  public existingHealthCheckNames: string[];

  public viewState: ViewState = new ViewState('None');
  public maxCookieTtl = 60 * 60 * 24; // One day.
  public taskMonitor: any;
  public hasBackendService = true;

  private sessionAffinityModelToViewMap: any = _.invert(this.sessionAffinityViewToModelMap);

  public static $inject = [
    '$scope',
    'application',
    '$uibModalInstance',
    'loadBalancer',
    'gceCommonLoadBalancerCommandBuilder',
    'isNew',
    'wizardSubFormValidation',
    '$state',
  ];
  constructor(
    public $scope: IScope,
    public application: Application,
    public $uibModalInstance: IModalInstanceService,
    private loadBalancer: SslLoadBalancer,
    private gceCommonLoadBalancerCommandBuilder: GceCommonLoadBalancerCommandBuilder,
    private isNew: boolean,
    private wizardSubFormValidation: any,
    $state: StateService,
  ) {
    super($scope, application, $uibModalInstance, $state);
  }

  public $onInit(): void {
    this.gceCommonLoadBalancerCommandBuilder
      .getBackingData(['existingLoadBalancerNamesByAccount', 'accounts', 'healthChecks', 'certificates'])
      .then((backingData) => {
        if (!this.isNew) {
          this.initializeEditMode();
        } else {
          this.loadBalancer = new SslLoadBalancer(GCEProviderSettings ? GCEProviderSettings.defaults.region : null);
        }

        this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);

        const accountNames: string[] = backingData.accounts.map((account: IAccount) => account.name);
        if (accountNames.length && !accountNames.includes(this.loadBalancer.account)) {
          this.loadBalancer.credentials = accountNames[0];
        } else {
          this.loadBalancer.credentials = this.loadBalancer.account;
        }

        this.accounts = backingData.accounts;
        this.certificateWrappers = backingData.certificates as any[];
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

  public onCertificateRefresh(): void {
    this.gceCommonLoadBalancerCommandBuilder.getBackingData(['certificates']).then((data) => {
      this.certificateWrappers = data.certificates as any[];
    });
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

    AccountService.getRegionsForAccount(this.loadBalancer.credentials).then((regions: IRegion[]) => {
      this.regions = regions.map((region: IRegion) => region.name);
    });
  }

  public updateName(): void {
    this.loadBalancer.loadBalancerName = this.getName(this.loadBalancer, this.application);
  }

  public setSessionAffinity(viewState: ViewState): void {
    this.loadBalancer.backendService.sessionAffinity = this.sessionAffinityViewToModelMap[viewState.sessionAffinity];
  }

  public submit(): void {
    const descriptor = this.isNew ? 'Create' : 'Update';
    const toSubmitLoadBalancer = _.cloneDeep(this.loadBalancer) as ISslLoadBalancerUpsertDescription;
    toSubmitLoadBalancer.name = toSubmitLoadBalancer.loadBalancerName;
    toSubmitLoadBalancer.backendService.name = toSubmitLoadBalancer.loadBalancerName;
    toSubmitLoadBalancer.cloudProvider = 'gce';
    delete toSubmitLoadBalancer.instances;

    this.taskMonitor.submit(() =>
      LoadBalancerWriter.upsertLoadBalancer(toSubmitLoadBalancer, this.application, descriptor, {
        healthCheck: {},
      }),
    );
  }

  private initializeEditMode(): void {
    this.loadBalancer.portRange = this.loadBalancer.portRange.split('-')[0];
    this.loadBalancer.certificate = this.loadBalancer.certificate.split('/').pop();
    this.viewState = new ViewState(
      this.sessionAffinityModelToViewMap[this.loadBalancer.backendService.sessionAffinity],
    );
  }
}

export const GCE_SSL_LOAD_BALANCER_CTRL = 'spinnaker.gce.sslLoadBalancer.controller';

module(GCE_SSL_LOAD_BALANCER_CTRL, [
  GCE_HEALTH_CHECK_SELECTOR_COMPONENT,
  GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER,
]).controller('gceSslLoadBalancerCtrl', SslLoadBalancerCtrl);
