import {module} from 'angular';
import * as _ from 'lodash';
import {ICredentials} from 'core/domain/ICredentials';
import {Application} from 'core/application/application.model';
import {IGceSubnet, IGceNetwork, IGceHealthCheck} from 'google/domain/index';
import gceHealthCheckCreate from '../common/healthCheck.component';
import {IStateService} from 'angular-ui-router';
import {InternalLoadBalancer,
        GceInternalLoadBalancerCommandBuilder,
        GCE_INTERNAL_LOAD_BALANCER_COMMAND_BUILDER} from './commandBuilder.service';

class ViewState {
  constructor(public sessionAffinity: string) {}
}

interface IKeyedByAccount {
  [account: string]: string[];
}

interface IPrivateScope extends ng.IScope {
  $$destroyed: boolean;
}

export interface IHealthCheckMap {
  [account: string]: { [healthCheckType: string]: IGceHealthCheck[] };
}

class InternalLoadBalancerCtrl implements ng.IComponentController {
  pages: any = {
    'location': require('./createLoadBalancerProperties.html'),
    'listener': require('./listener.html'),
    'healthCheck': require('./healthCheck.html'),
    'advancedSettings': require('./advancedSettings.html'),
  };
  viewToModelMap: any = {
    'None': 'NONE',
    'Client IP': 'CLIENT_IP',
    'Client IP and protocol': 'CLIENT_IP_PROTO',
    'Client IP, port and protocol': 'CLIENT_IP_PORT_PROTO',
  };
  accounts: ICredentials[];
  modelToViewMap: any = _.invert(this.viewToModelMap);
  regions: string[];
  networks: IGceNetwork[];
  networkOptions: string[];
  subnets: IGceSubnet[];
  subnetOptions: string[];
  existingHealthCheckMap: IHealthCheckMap;
  existingHealthCheckNamesMap: { [account: string]: string[] };
  existingHealthCheckNames: string[];
  loadBalancerNameMap: IKeyedByAccount;
  existingLoadBalancerNames: string[];
  viewState: ViewState = new ViewState('None');
  taskMonitor: any;

  constructor (private $scope: IPrivateScope,
               private $state: IStateService,
               private application: Application,
               private loadBalancer: InternalLoadBalancer,
               private gceInternalLoadBalancerCommandBuilder: GceInternalLoadBalancerCommandBuilder,
               private infrastructureCaches: any,
               private isNew: boolean,
               private $uibModalInstance: any,
               private accountService: any,
               private loadBalancerWriter: any,
               private wizardSubFormValidation: any,
               private taskMonitorService: any) { }

  $onInit (): void {
    this.gceInternalLoadBalancerCommandBuilder
      .buildCommand(this.loadBalancer, this.isNew)
      .then(({ accounts,
               networks,
               subnets,
               loadBalancer,
               loadBalancerNames,
               healthCheckMap,
               healthCheckNamesMap, }: { accounts: ICredentials[],
                                         networks: IGceNetwork[],
                                         subnets: IGceSubnet[],
                                         loadBalancer: InternalLoadBalancer,
                                         loadBalancerNames: IKeyedByAccount,
                                         healthCheckMap: IHealthCheckMap,
                                         healthCheckNamesMap: IKeyedByAccount, }) => {
        this.loadBalancer = loadBalancer;
        if (!this.isNew) {
          this.initializeEditMode();
        }

        this.loadBalancer.loadBalancerName = this.getName();

        let accountNames: string[] = accounts.map((account) => account.name);
        if (accountNames.length && !accountNames.includes(this.loadBalancer.account)) {
          this.loadBalancer.credentials = accountNames[0];
        } else {
          this.loadBalancer.credentials = this.loadBalancer.account;
        }

        this.accounts = accounts;
        this.networks = networks;
        this.subnets = subnets;
        this.existingHealthCheckMap = healthCheckMap;
        this.existingHealthCheckNamesMap = healthCheckNamesMap;
        this.loadBalancerNameMap = loadBalancerNames;
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
          onTaskComplete: () => this.onTaskComplete(),
        });
    });
  }

  initializeEditMode (): void {
    this.loadBalancer.ports = this.loadBalancer.ports.join(', ');
    this.loadBalancer.subnet = this.loadBalancer.subnet.split('/').pop();
    this.loadBalancer.network = this.loadBalancer.network.split('/').pop();
    this.viewState = new ViewState(this.modelToViewMap[this.loadBalancer.backendService.sessionAffinity]);
  }

  onApplicationRefresh (): void {
    // If the user has already closed the modal, do not navigate to the new details view
    if (this.$scope.$$destroyed) {
      return;
    }
    this.$uibModalInstance.close();

    let lb = this.loadBalancer;
    let newStateParams = {
      name: lb.loadBalancerName,
      accountId: lb.credentials,
      region: lb.region,
      provider: 'gce',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }

  onTaskComplete (): void {
    this.infrastructureCaches.clearCache('healthCheck');
    this.application.getDataSource('loadBalancers').refresh();
    this.application.getDataSource('loadBalancers').onNextRefresh(this.$scope, () => this.onApplicationRefresh());
  }

  onHealthCheckRefresh (): void {
    let { healthCheckMapPromise, healthCheckNamesByAccountPromise } = this.gceInternalLoadBalancerCommandBuilder
      .getHealthCheckPromises(this.loadBalancer, this.isNew);

    healthCheckMapPromise.then((existingHealthCheckMap) => {
      this.existingHealthCheckMap = existingHealthCheckMap;
    });

    healthCheckNamesByAccountPromise.then((existingHealthCheckNamesMap) => {
      this.existingHealthCheckNamesMap = existingHealthCheckNamesMap;
    });
  }

  networkUpdated (): void {
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

  protocolUpdated (): void {
    if (this.loadBalancer.ipProtocol === 'UDP') {
      this.viewState = new ViewState('None');
      this.loadBalancer.backendService.sessionAffinity = 'NONE';
    }
  }

  accountUpdated (): void {
    let existingHealthCheckNames =
      _.get<any, string[]>(this, ['existingHealthCheckNamesMap', this.loadBalancer.credentials]);
    this.existingHealthCheckNames = existingHealthCheckNames || [];

    let existingLoadBalancerNames =
      _.get<any, string[]>(this, ['loadBalancerNameMap', this.loadBalancer.credentials]);
    this.existingLoadBalancerNames = existingLoadBalancerNames || [];

    this.networkOptions = this.networks
      .filter((network) => network.account === this.loadBalancer.credentials)
      .map((network) => network.name);

    this.accountService.getRegionsForAccount(this.loadBalancer.credentials)
      .then((regions: { name: string }[]) => {
        this.regions = regions.map((r) => r.name);
        this.networkUpdated();
      });
  }

  regionUpdated (): void {
    this.networkUpdated();
  }

  getName (): string {
    let lb = this.loadBalancer;
    let loadBalancerName = [this.application.name, (lb.stack || ''), (lb.detail || '')].join('-');
    return _.trimEnd(loadBalancerName, '-');
  }

  updateName (): void {
    this.loadBalancer.loadBalancerName = this.getName();
  }

  setSessionAffinity (viewState: ViewState): void {
    this.loadBalancer.backendService.sessionAffinity = this.viewToModelMap[viewState.sessionAffinity];
  }

  cancel (): void {
    this.$uibModalInstance.dismiss();
  }

  submit (): void {
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
}

const gceInternalLoadBalancerCtrl = 'spinnaker.gce.internalLoadBalancer.controller';

module(gceInternalLoadBalancerCtrl, [
    gceHealthCheckCreate,
    GCE_INTERNAL_LOAD_BALANCER_COMMAND_BUILDER,
    require('core/account/account.service.js'),
    require('core/cache/infrastructureCaches.js'),
    require('core/modal/wizard/wizardSubFormValidation.service.js'),
    require('core/loadBalancer/loadBalancer.write.service.js'),
    require('core/subnet/subnet.read.service.js'),
    require('core/task/monitor/taskMonitorService.js'),
  ])
  .controller('gceInternalLoadBalancerCtrl', InternalLoadBalancerCtrl);

export default gceInternalLoadBalancerCtrl;
