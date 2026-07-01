import { AccountService, NetworkReader, SubnetReader } from '@spinnaker/core';

import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE } from './commandBuilder.service';

describe('gceHttpLoadBalancerCommandBuilder', () => {
  let $q, $scope, commandBuilder;

  beforeEach(() => {
    window.module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE, ($provide) => {
      $provide.value('gceBackendServiceReader', {
        listBackendServices: () =>
          $q.resolve([
            {
              name: 'backend-service',
              region: 'us-central1',
              kind: 'regionBackendService',
              healthCheckLink: 'projects/p/regions/us-central1/healthChecks/hc',
            },
          ]),
      });
      $provide.value('gceCertificateReader', { listCertificates: () => $q.resolve([]) });
      $provide.value('gceHealthCheckReader', { listHealthChecks: () => $q.resolve([]) });
      $provide.value('gceHttpLoadBalancerTransformer', { deserialize: () => ({}) });
      $provide.value('loadBalancerReader', { listLoadBalancers: () => $q.resolve([]) });
      $provide.value('gceAddressReader', {
        listAddresses: () =>
          $q.resolve([
            { address: '34.0.0.1', account: 'test', region: 'us-central1', addressType: 'EXTERNAL' },
            { address: '10.0.0.1', account: 'test', region: 'us-central1', addressType: 'INTERNAL' },
          ]),
      });
      $provide.value('gceXpnNamingService', {
        decorateXpnResourceIfNecessary: (_project, resource) => resource,
      });
    });
  });

  beforeEach(() => {
    window.inject((_gceHttpLoadBalancerCommandBuilder_, _$q_, $rootScope) => {
      commandBuilder = _gceHttpLoadBalancerCommandBuilder_;
      $q = _$q_;
      $scope = $rootScope.$new();
    });
    spyOn(AccountService, 'listAccounts').and.returnValue(
      $q.resolve([{ name: 'test', regions: [{ name: 'us-central1' }] }]),
    );
    spyOn(NetworkReader, 'listNetworksByProvider').and.returnValue($q.resolve([{ name: 'default' }]));
    spyOn(SubnetReader, 'listSubnetsByProvider').and.returnValue(
      $q.resolve([
        { network: 'default', region: 'us-central1', purpose: 'REGIONAL_MANAGED_PROXY' },
        { network: 'internal-only', region: 'us-central1', purpose: 'INTERNAL_HTTPS_LOAD_BALANCER' },
      ]),
    );
  });

  it('builds external managed backing data with only external regional addresses', () => {
    let command;

    commandBuilder
      .buildCommand({
        isNew: true,
        isExternalManaged: true,
      })
      .then((result) => (command = result));
    $scope.$digest();

    expect(command.loadBalancer.loadBalancerType).toBe('EXTERNAL_MANAGED');
    expect(command.backingData.externalHttpLbNetworks).toEqual(['default']);
    expect(command.backingData.addresses.map((address) => address.address)).toEqual(['34.0.0.1']);
  });

  it('clears external managed network when selected region has no proxy-only subnet', () => {
    let command;
    SubnetReader.listSubnetsByProvider.and.returnValue(
      $q.resolve([{ network: 'internal-only', region: 'us-central1', purpose: 'INTERNAL_HTTPS_LOAD_BALANCER' }]),
    );

    commandBuilder
      .buildCommand({
        isNew: true,
        isExternalManaged: true,
      })
      .then((result) => (command = result));
    $scope.$digest();

    expect(command.backingData.externalHttpLbNetworks).toEqual([]);
    expect(command.loadBalancer.network).toBeNull();
  });
});
