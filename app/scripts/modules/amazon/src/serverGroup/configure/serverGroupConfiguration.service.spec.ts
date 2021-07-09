import { mock, IQService, IScope, IRootScopeService } from 'angular';

import {
  AccountService,
  ApplicationModelBuilder,
  CacheInitializerService,
  LoadBalancerReader,
  SecurityGroupReader,
  SubnetReader,
} from '@spinnaker/core';

import { KeyPairsReader } from '../../keyPairs';
import {
  AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  AwsServerGroupConfigurationService,
} from './serverGroupConfiguration.service';

describe('Service: awsServerGroupConfiguration', function () {
  let service: AwsServerGroupConfigurationService,
    $q: IQService,
    securityGroupReader: SecurityGroupReader,
    awsInstanceTypeService: any,
    cacheInitializer: CacheInitializerService,
    loadBalancerReader: LoadBalancerReader,
    $scope: IScope;

  beforeEach(mock.module(AWS_SERVER_GROUP_CONFIGURATION_SERVICE));

  beforeEach(
    mock.inject(function (
      _awsServerGroupConfigurationService_: AwsServerGroupConfigurationService,
      _$q_: IQService,
      _securityGroupReader_: SecurityGroupReader,
      _awsInstanceTypeService_: any,
      _cacheInitializer_: CacheInitializerService,
      _loadBalancerReader_: LoadBalancerReader,
      $rootScope: IRootScopeService,
    ) {
      service = _awsServerGroupConfigurationService_;
      $q = _$q_;
      securityGroupReader = _securityGroupReader_;
      awsInstanceTypeService = _awsInstanceTypeService_;
      cacheInitializer = _cacheInitializer_;
      loadBalancerReader = _loadBalancerReader_;
      $scope = $rootScope.$new();

      this.allLoadBalancers = [
        {
          name: 'elb-1',
          accounts: [
            {
              name: 'test',
              regions: [
                {
                  name: 'us-east-1',
                  loadBalancers: [{ region: 'us-east-1', vpcId: 'vpc-1', name: 'elb-1', targetGroups: [] }],
                },
              ],
            },
          ],
        },
        {
          name: 'elb-2',
          accounts: [
            {
              name: 'test',
              regions: [
                {
                  name: 'us-east-1',
                  loadBalancers: [{ region: 'us-east-1', vpcId: 'vpc-2', name: 'elb-2', targetGroups: [] }],
                },
                {
                  name: 'us-west-1',
                  loadBalancers: [{ region: 'us-west-1', vpcId: null, name: 'elb-2', targetGroups: [] }],
                },
              ],
            },
          ],
        },
      ];
    }),
  );

  describe('configureCommand', function () {
    it('attempts to reload firewalls if some are not found on initialization, but does not set dirty flag', function () {
      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue($q.when([] as any));
      const getAllSecurityGroupsSpy = spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue(
        $q.when([] as any),
      );
      spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when(this.allLoadBalancers));
      spyOn(SubnetReader, 'listSubnets').and.returnValue($q.when([]));
      spyOn(AccountService, 'getPreferredZonesByAccount').and.returnValue($q.when([] as any));
      spyOn(KeyPairsReader, 'listKeyPairs').and.returnValue($q.when([]));
      spyOn(awsInstanceTypeService, 'getAllTypesByRegion').and.returnValue($q.when([]));
      const refreshCacheSpy = spyOn(cacheInitializer, 'refreshCache').and.returnValue($q.when(null));

      const command = {
        credentials: 'test',
        region: 'us-east-1',
        securityGroups: ['sg-1'],
        vpcId: null,
        viewState: {
          disableImageSelection: true,
          dirty: {},
        },
      } as any;

      service.configureCommand(
        ApplicationModelBuilder.createApplicationForTests('name', {
          key: 'loadBalancers',
          lazy: true,
          defaultData: [],
        }),
        command,
      );
      $scope.$digest();
      $scope.$digest();

      expect(cacheInitializer.refreshCache).toHaveBeenCalledWith('securityGroups');
      expect(refreshCacheSpy.calls.count()).toBe(1);
      expect(getAllSecurityGroupsSpy.calls.count()).toBe(2);
    });
  });

  describe('configureLoadBalancerOptions', function () {
    beforeEach(function () {
      this.command = {
        viewState: {},
        backingData: {
          loadBalancers: this.allLoadBalancers,
          filtered: {
            loadBalancers: ['elb-1', 'elb-2'],
          },
        },
        loadBalancers: ['elb-1'],
        credentials: 'test',
        region: 'us-east-1',
        vpcId: null,
      } as any;
    });

    it('matches existing load balancers based on name - no VPC', function () {
      const result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual([]);
      expect(this.command.vpcLoadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: {} });
    });

    it('matches existing load balancers based on name - VPC', function () {
      this.command.vpcId = 'vpc-1';
      const result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: {} });
    });

    it('sets dirty all unmatched load balancers - no VPC', function () {
      this.command.region = 'us-west-1';
      this.command.loadBalancers = ['elb-1', 'elb-2'];
      const result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-2']);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-1'] } });
    });

    it('moves load balancers to vpcLoadBalancers when vpc is de-selected', function () {
      this.command.loadBalancers = ['elb-1'];
      this.command.vpcId = 'vpc-1';
      let result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: {} });

      this.command.vpcId = null;
      result = service.configureLoadBalancerOptions(this.command);
      expect(result).toEqual({ dirty: {} });
      expect(this.command.vpcLoadBalancers).toEqual(['elb-1']);
      expect(this.command.loadBalancers).toEqual([]);
    });

    it('sets dirty all unmatched load balancers - VPC', function () {
      this.command.loadBalancers = ['elb-1', 'elb-2'];
      this.command.vpcId = 'vpc-1';
      let result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-2'] } });

      this.command.vpcId = 'vpc-2';
      result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual([]);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-1'] } });
    });

    it('updates filteredData to new region - no VPC', function () {
      this.command.region = 'us-west-1';
      service.configureLoadBalancerOptions(this.command);
      expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-2']);
    });

    it('updates filteredData to new VPC', function () {
      this.command.vpcId = 'vpc-1';
      service.configureLoadBalancerOptions(this.command);
      expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-1']);
    });
  });

  describe('configureSecurityGroupOptions', function () {
    beforeEach(function () {
      this.allSecurityGroups = {
        test: {
          aws: {
            'us-west-1': [
              { name: 'sg1', id: 'sg-1a', vpcId: null },
              { name: 'sg2', id: 'sg-2a', vpcId: null },
              { name: 'sg3', id: 'sg-3a', vpcId: null },
              { name: 'sg1', id: 'sg-1va', vpcId: 'vpc-1' },
              { name: 'sg2', id: 'sg-2va', vpcId: 'vpc-1' },
              { name: 'sg3', id: 'sg-3va', vpcId: 'vpc-2' },
            ],
            'us-east-1': [
              { name: 'sg1', id: 'sg-1c', vpcId: null },
              { name: 'sg2', id: 'sg-2c', vpcId: null },
              { name: 'sg1', id: 'sg-1vc', vpcId: 'vpc-3' },
              { name: 'sg2', id: 'sg-2vc', vpcId: 'vpc-4' },
            ],
          },
        },
      };

      this.command = {
        backingData: {
          securityGroups: this.allSecurityGroups,
          filtered: {
            securityGroups: this.allSecurityGroups.test.aws['us-west-1'],
          },
        },
        securityGroups: ['sg-1a', 'sg-2a'],
        credentials: 'test',
        region: 'us-west-1',
      };
    });

    it('matches existing firewalls based on name - no VPC', function () {
      this.command.region = 'us-east-1';
      const result = service.configureSecurityGroupOptions(this.command);
      expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
      expect(result).toEqual({ dirty: {} });
    });

    it('matches existing firewalls based on name - VPC', function () {
      this.command.vpcId = 'vpc-1';
      const result = service.configureSecurityGroupOptions(this.command);
      expect(this.command.securityGroups).toEqual(['sg-1va', 'sg-2va']);
      expect(result).toEqual({ dirty: {} });
    });

    it('matches on name or id, converting to id when name encountered', function () {
      this.command.securityGroups = ['sg1', 'sg-2a'];
      this.command.region = 'us-east-1';
      const result = service.configureSecurityGroupOptions(this.command);
      expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
      expect(result).toEqual({ dirty: {} });
    });

    it('sets dirty all unmatched firewalls - no VPC', function () {
      this.command.securityGroups.push('sg-3a');
      this.command.region = 'us-east-1';
      const result = service.configureSecurityGroupOptions(this.command);
      expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
      expect(result).toEqual({ dirty: { securityGroups: ['sg3'] } });
    });

    it('sets dirty all unmatched firewalls - VPC', function () {
      this.command.securityGroups.push('sg-3a');
      this.command.vpcId = 'vpc-2';
      const result = service.configureSecurityGroupOptions(this.command);
      expect(this.command.securityGroups).toEqual(['sg-3va']);
      expect(result).toEqual({ dirty: { securityGroups: ['sg1', 'sg2'] } });
    });

    it('updates filteredData to new region - no VPC', function () {
      const expected = this.allSecurityGroups.test.aws['us-east-1'].slice(0, 2);
      this.command.region = 'us-east-1';
      service.configureSecurityGroupOptions(this.command);
      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
    });

    it('updates filteredData to new VPC', function () {
      const expected = this.allSecurityGroups.test.aws['us-west-1'].slice(3, 5);
      this.command.vpcId = 'vpc-1';
      service.configureSecurityGroupOptions(this.command);
      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
    });
  });

  describe('configureKeyPairs', function () {
    beforeEach(function () {
      this.command = {
        backingData: {
          filtered: {},
          credentialsKeyedByAccount: {
            test: {
              defaultKeyPair: 'test-pair-{{region}}',
            },
            prod: {
              defaultKeyPair: 'prod-pair',
            },
            nothing: {},
          },
          keyPairs: [
            { account: 'test', region: 'us-west-1', keyName: 'test-pair-us-west-1' },
            { account: 'test', region: 'us-east-1', keyName: 'test-pair-us-east-1' },
            { account: 'test', region: 'us-west-1', keyName: 'shared' },
            { account: 'test', region: 'eu-west-1', keyName: 'odd-pair' },
            { account: 'test', region: 'eu-west-1', keyName: 'other-pair' },
            { account: 'prod', region: 'us-west-1', keyName: 'prod-pair' },
            { account: 'prod', region: 'us-west-1', keyName: 'shared' },
          ],
        },
        credentials: 'test',
        region: 'us-west-1',
        keyPair: 'shared',
      };
    });

    it('retains keyPair when found in new account', function () {
      this.command.credentials = 'prod';
      service.configureKeyPairs(this.command);
      expect(this.command.keyPair).toBe('shared');
    });

    it('retains keyPair when found in new region', function () {
      this.command.region = 'us-east-1';
      this.command.keyPair = 'test-pair-us-west-1';
      service.configureKeyPairs(this.command);
      expect(this.command.keyPair).toBe('test-pair-us-east-1');
    });

    it('sets to new default value when changing account and using default key pair without marking dirty', function () {
      this.command.credentials = 'prod';
      this.command.keyPair = 'test-pair-us-west-1';
      const result = service.configureKeyPairs(this.command);
      expect(result.dirty.keyPair).toBeFalsy();
      expect(this.command.keyPair).toBe('prod-pair');
    });

    it('sets to new default value when changing region and using default key pair without marking dirty', function () {
      this.command.region = 'us-east-1';
      this.command.keyPair = 'test-pair-us-west-1';
      const result = service.configureKeyPairs(this.command);
      expect(result.dirty.keyPair).toBeFalsy();
      expect(this.command.keyPair).toBe('test-pair-us-east-1');
    });

    it('marks dirty, sets to first value found when default not present in new region', function () {
      this.command.region = 'eu-west-1';
      const result = service.configureKeyPairs(this.command);
      expect(result.dirty.keyPair).toBe(true);
      expect(this.command.keyPair).toBe(null);
    });
  });

  describe('configureImages', function () {
    beforeEach(function () {
      this.command = {
        viewState: {},
        backingData: {
          filtered: {},
          credentialsKeyedByAccount: {
            test: {
              defaultKeyPair: 'test-pair',
            },
            prod: {
              defaultKeyPair: 'prod-pair',
            },
          },
          packageImages: [
            { amis: { 'us-east-1': [{}] }, attributes: { virtualizationType: 'hvm' }, imageName: 'ami-1234' },
            {
              amis: { 'us-east-1': [{}], 'eu-west-1': [{}] },
              attributes: { virtualizationType: 'pv' },
              imageName: 'ami-1235',
            },
            { amis: { 'us-west-1': [{}] }, attributes: { virtualizationType: 'hvm' }, imageName: 'ami-1236' },
          ],
        },
        credentials: 'test',
        region: 'us-west-1',
        amiName: 'ami-1236',
      };
    });

    it('clears virtualization type if no ami present', function () {
      this.command.virtualizationType = 'pv';
      this.command.amiName = null;
      service.configureImages(this.command);
      expect(this.command.virtualizationType).toBe(null);
    });

    it('clears amiName if region is absent and sets dirty flag', function () {
      this.command.region = null;
      const result = service.configureImages(this.command);
      expect(this.command.amiName).toBe(null);
      expect(result.dirty.amiName).toBe(true);
    });
  });

  describe('managedResources', () => {
    beforeEach(() => {
      this.command = {
        viewState: {},
        backingData: {
          filtered: {},
          credentialsKeyedByAccount: {
            prod: {
              regions: [
                { name: 'us-east-1', availabilityZones: [] },
                { name: 'us-west-1', availabilityZones: [] },
              ],
            },
            test: {
              regions: [
                { name: 'us-east-1', availabilityZones: [] },
                { name: 'us-west-1', availabilityZones: [] },
              ],
            },
          },
          managedResources: [
            {
              kind: 'ec2/cluster@v1',
              locations: {
                account: 'prod',
                regions: [{ name: 'us-east-1' }],
              },
              moniker: {
                stack: 'foo',
                detail: 'bar',
              },
            },
          ],
          securityGroups: {},
          preferredZones: {},
        },
        credentials: 'test',
        region: 'us-west-1',
        stack: '',
        freeFormDetails: '',
      };
      service.attachEventHandlers(this.command);
    });

    it('does not add managedResource on initial load if not matched', () => {
      expect(this.command.viewState.resourceSummary).toBeFalsy();
    });

    it('adds managedResource when all fields match a non-paused resource', () => {
      const { command } = this;
      const managedResource = command.backingData.managedResources[0];
      expect(command.resourceSummary).toBeFalsy();

      command.credentials = managedResource.locations.account;
      command.credentialsChanged(command);
      expect(command.resourceSummary).toBeFalsy();

      command.region = managedResource.locations.regions[0].name;
      command.regionChanged(command);
      expect(command.resourceSummary).toBeFalsy();

      command.stack = managedResource.moniker.stack;
      command.clusterChanged(command);
      expect(command.resourceSummary).toBeFalsy();

      command.freeFormDetails = managedResource.moniker.detail;
      command.clusterChanged(command);
      expect(command.resourceSummary).toBe(managedResource);

      managedResource.isPaused = true;
      command.clusterChanged(command);
      expect(command.resourceSummary).toBeFalsy();
    });
  });
});
