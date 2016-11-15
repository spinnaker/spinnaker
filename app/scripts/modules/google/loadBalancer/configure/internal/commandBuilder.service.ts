import {module} from 'angular';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {SubnetReader, SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';
import {IGceBackendService} from 'google/domain/index';
import {GceHealthCheckReader, GCE_HEALTH_CHECK_READER} from 'google/healthCheck/healthCheck.read.service';

export class InternalLoadBalancer {
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

  constructor (public region?: string) {}
}

export class GceInternalLoadBalancerCommandBuilder {
  constructor(private $q: ng.IQService,
              private loadBalancerReader: any,
              private settings: any,
              private accountService: AccountService,
              private subnetReader: SubnetReader,
              private gceHealthCheckReader: GceHealthCheckReader,
              private networkReader: any) {}

  buildCommand (loadBalancer: InternalLoadBalancer, isNew: boolean): ng.IPromise<any> {
    if (isNew) {
      loadBalancer = new InternalLoadBalancer(this.settings.providers.gce
                                              ? this.settings.providers.gce.defaults.region
                                              : null);
    }

    let loadBalancerNamePromise = this.getLoadBalancerPromise();
    let { healthCheckMapPromise, healthCheckNamesByAccountPromise } = this.getHealthCheckPromises(loadBalancer, isNew);

    return this.$q.all({
      accounts: this.accountService.listAccounts('gce'),
      networks: this.networkReader.listNetworksByProvider('gce'),
      subnets: this.subnetReader.listSubnetsByProvider('gce'),
      loadBalancerNames: loadBalancerNamePromise,
      healthCheckMap: healthCheckMapPromise,
      healthCheckNamesMap: healthCheckNamesByAccountPromise,
      loadBalancer: this.$q.resolve(loadBalancer),
    });
  }

  getLoadBalancerPromise (): ng.IPromise<any> {
    return this.loadBalancerReader.listLoadBalancers('gce')
      .then((loadBalancerList: any[]) => {
        return _.chain(loadBalancerList)
          .map('accounts')
          .flatten()
          .groupBy('name') // account name
          .mapValues((regionWrappers) => _.chain(regionWrappers)
            .map('regions')
            .flatten()
            .map('loadBalancers')
            .flatten()
            .map('name') // load balancer name
            .value())
          .value();
      });
  }

  getHealthCheckPromises (loadBalancer: InternalLoadBalancer, isNew: boolean): { [key: string]: ng.IPromise<any> } {
    let healthChecksPromise: ng.IPromise<any> = this.gceHealthCheckReader.listHealthChecks()
      .then((healthCheckWrapperList) => _.groupBy(healthCheckWrapperList, 'account'));

    let healthCheckMapPromise: ng.IPromise<any> = healthChecksPromise
      .then((healthCheckWrapperMap) => {
        return _.mapValues(healthCheckWrapperMap, (wrappers) => _.chain(wrappers)
          .map('healthCheck')
          .flatten()
          .groupBy('healthCheckType')
          .value());
      });

    let healthCheckNamesByAccountPromise: ng.IPromise<any> = healthChecksPromise
      .then((healthCheckWrapperMap) => {
        let healthCheckNamesByAccount = _.mapValues(healthCheckWrapperMap,
                                                    (wrappers) => _.chain(wrappers).map('healthCheck.name').value());
        if (!isNew) {
          healthCheckNamesByAccount[loadBalancer.account] =
            healthCheckNamesByAccount[loadBalancer.account]
              .filter((name: string) => name !== loadBalancer.backendService.healthCheck.name);
        }
        return healthCheckNamesByAccount;
      });

    return { healthCheckMapPromise, healthCheckNamesByAccountPromise };
  }
}

export const GCE_INTERNAL_LOAD_BALANCER_COMMAND_BUILDER = 'spinnaker.gce.internalLoadBalancer.commandBuilder.service';

module(GCE_INTERNAL_LOAD_BALANCER_COMMAND_BUILDER, [
  ACCOUNT_SERVICE,
  require('core/loadBalancer/loadBalancer.read.service.js'),
  SUBNET_READ_SERVICE,
  GCE_HEALTH_CHECK_READER,
]).service('gceInternalLoadBalancerCommandBuilder', GceInternalLoadBalancerCommandBuilder);
