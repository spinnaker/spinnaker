import { module } from 'angular';
import _ from 'lodash';

import {
  AccountService,
  IAccount,
  ILoadBalancersByAccount,
  INetwork,
  ISubnet,
  LOAD_BALANCER_READ_SERVICE,
  LoadBalancerReader,
  NetworkReader,
  SubnetReader,
} from '@spinnaker/core';
import { GCE_CERTIFICATE_READER, GceCertificateReader, IGceCertificate } from '../../../certificate/certificate.reader';
import { IGceHealthCheck } from '../../../domain/healthCheck';
import { GCE_HEALTH_CHECK_READER, GceHealthCheckReader } from '../../../healthCheck/healthCheck.read.service';

export class GceCommonLoadBalancerCommandBuilder {
  private dataFetchers: { [key: string]: Function } = {
    existingLoadBalancerNamesByAccount: (): PromiseLike<_.Dictionary<any>> => {
      return this.loadBalancerReader.listLoadBalancers('gce').then((loadBalancerList: ILoadBalancersByAccount[]) => {
        return _.chain(loadBalancerList)
          .map('accounts')
          .flatten()
          .groupBy('name') // account name
          .mapValues((regionWrappers) =>
            _.chain(regionWrappers)
              .map('regions')
              .flatten()
              .map('loadBalancers')
              .flatten()
              .map('name') // load balancer name
              .value(),
          )
          .value();
      });
    },
    accounts: (): PromiseLike<IAccount[]> => AccountService.listAccounts('gce'),
    networks: (): PromiseLike<INetwork[]> => NetworkReader.listNetworksByProvider('gce'),
    subnets: (): PromiseLike<ISubnet[]> => SubnetReader.listSubnetsByProvider('gce'),
    healthChecks: (): PromiseLike<IGceHealthCheck[]> => this.gceHealthCheckReader.listHealthChecks(),
    certificates: (): PromiseLike<IGceCertificate[]> => this.gceCertificateReader.listCertificates(),
  };

  public static $inject = ['$q', 'loadBalancerReader', 'gceHealthCheckReader', 'gceCertificateReader'];
  constructor(
    private $q: ng.IQService,
    private loadBalancerReader: LoadBalancerReader,
    private gceHealthCheckReader: GceHealthCheckReader,
    private gceCertificateReader: GceCertificateReader,
  ) {}

  public getBackingData(dataTypes: string[]): PromiseLike<any> {
    const promises = dataTypes.reduce(
      (promisesByDataType: { [dataType: string]: PromiseLike<any> }, dataType: string) => {
        if (this.dataFetchers[dataType]) {
          promisesByDataType[dataType] = this.dataFetchers[dataType]();
        }
        return promisesByDataType;
      },
      {},
    );

    return this.$q.all(promises);
  }

  public groupHealthChecksByAccountAndType(
    healthChecks: IGceHealthCheck[],
  ): { [account: string]: { [healthCheckType: string]: IGceHealthCheck[] } } {
    return _.chain(healthChecks)
      .groupBy('account')
      .mapValues((grouped: IGceHealthCheck[]) => _.groupBy(grouped, 'healthCheckType'))
      .value();
  }

  public groupHealthCheckNamesByAccount(
    healthChecks: IGceHealthCheck[],
    namesToOmit: string[],
  ): { [account: string]: string[] } {
    return _.chain(healthChecks)
      .groupBy('account')
      .mapValues((grouped: IGceHealthCheck[]) => _.chain(grouped).map('name').difference(namesToOmit).value())
      .value() as { [account: string]: string[] };
  }
}

export const GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER = 'spinnaker.gce.commonLoadBalancerCommandBuilder.service';

module(GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER, [
  LOAD_BALANCER_READ_SERVICE,
  GCE_CERTIFICATE_READER,
  GCE_HEALTH_CHECK_READER,
]).service('gceCommonLoadBalancerCommandBuilder', GceCommonLoadBalancerCommandBuilder);
