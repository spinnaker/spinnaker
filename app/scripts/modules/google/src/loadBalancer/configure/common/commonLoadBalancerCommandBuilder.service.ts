import { IPromise, module } from 'angular';
import * as _ from 'lodash';

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

import { GCE_CERTIFICATE_READER, GceCertificateReader, IGceCertificate } from 'google/certificate/certificate.reader';
import { IGceHealthCheck } from 'google/domain/healthCheck';
import { GCE_HEALTH_CHECK_READER, GceHealthCheckReader } from 'google/healthCheck/healthCheck.read.service';

export class GceCommonLoadBalancerCommandBuilder {
  private dataFetchers: { [key: string]: Function } = {
    existingLoadBalancerNamesByAccount: (): IPromise<_.Dictionary<any>> => {
      return this.loadBalancerReader.listLoadBalancers('gce').then((loadBalancerList: ILoadBalancersByAccount[]) => {
        return _.chain(loadBalancerList)
          .map('accounts')
          .flatten()
          .groupBy('name') // account name
          .mapValues(regionWrappers =>
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
    accounts: (): IPromise<IAccount[]> => AccountService.listAccounts('gce'),
    networks: (): IPromise<INetwork[]> => NetworkReader.listNetworksByProvider('gce'),
    subnets: (): IPromise<ISubnet[]> => SubnetReader.listSubnetsByProvider('gce'),
    healthChecks: (): IPromise<IGceHealthCheck[]> => this.gceHealthCheckReader.listHealthChecks(),
    certificates: (): IPromise<IGceCertificate[]> => this.gceCertificateReader.listCertificates(),
  };

  public static $inject = ['$q', 'loadBalancerReader', 'gceHealthCheckReader', 'gceCertificateReader'];
  constructor(
    private $q: ng.IQService,
    private loadBalancerReader: LoadBalancerReader,
    private gceHealthCheckReader: GceHealthCheckReader,
    private gceCertificateReader: GceCertificateReader,
  ) {
    'ngInject';
  }

  public getBackingData(dataTypes: string[]): IPromise<any> {
    const promises = dataTypes.reduce((promisesByDataType: { [dataType: string]: IPromise<any> }, dataType: string) => {
      if (this.dataFetchers[dataType]) {
        promisesByDataType[dataType] = this.dataFetchers[dataType]();
      }
      return promisesByDataType;
    }, {});

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
      .mapValues((grouped: IGceHealthCheck[]) =>
        _.chain(grouped)
          .map('name')
          .difference(namesToOmit)
          .value(),
      )
      .value() as { [account: string]: string[] };
  }
}

export const GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER = 'spinnaker.gce.commonLoadBalancerCommandBuilder.service';

module(GCE_COMMON_LOAD_BALANCER_COMMAND_BUILDER, [
  LOAD_BALANCER_READ_SERVICE,
  GCE_CERTIFICATE_READER,
  GCE_HEALTH_CHECK_READER,
]).service('gceCommonLoadBalancerCommandBuilder', GceCommonLoadBalancerCommandBuilder);
