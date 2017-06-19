import { mock, IRootScopeService, IQService, IControllerService, IScope, noop } from 'angular';

import { APPLICATION_MODEL_BUILDER,
  AccountService,
  SubnetReader,
  ApplicationModelBuilder,
  SecurityGroupReader
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { IAmazonClassicLoadBalancer, IAmazonClassicLoadBalancerUpsertCommand } from 'amazon/domain';
import { AwsLoadBalancerTransformer } from 'amazon/loadBalancer/loadBalancer.transformer';
import { AWS_CREATE_CLASSIC_LOAD_BALANCER_CTRL, CreateClassicLoadBalancerCtrl } from './createClassicLoadBalancer.controller';

describe('Controller: awsCreateClassicLoadBalancerCtrl', () => {
  let controller: CreateClassicLoadBalancerCtrl,
      $scope: IScope,
      $q: IQService,
      securityGroupReader: SecurityGroupReader,
      accountService: AccountService,
      subnetReader: SubnetReader,
      initialize: (loadBalancer?: IAmazonClassicLoadBalancer) => void;

  beforeEach(
    mock.module(
      AWS_CREATE_CLASSIC_LOAD_BALANCER_CTRL,
      APPLICATION_MODEL_BUILDER
    )
  );

  // Initialize the controller and a mock scope
  beforeEach(mock.inject(($controller: IControllerService,
                          $rootScope: IRootScopeService,
                          _$q_: IQService,
                          _accountService_: AccountService,
                          _subnetReader_: SubnetReader,
                          applicationModelBuilder: ApplicationModelBuilder,
                          _securityGroupReader_: SecurityGroupReader,
                          awsLoadBalancerTransformer: AwsLoadBalancerTransformer) => {
    $scope = $rootScope.$new();
    $q = _$q_;
    securityGroupReader = _securityGroupReader_;
    accountService = _accountService_;
    subnetReader = _subnetReader_;
    const app = applicationModelBuilder.createApplication('app', {key: 'loadBalancers', lazy: true});
    initialize = (loadBalancer: IAmazonClassicLoadBalancer = null) => {
      if (loadBalancer) {
        spyOn(awsLoadBalancerTransformer, 'convertClassicLoadBalancerForEditing').and.returnValue(loadBalancer);
      }
      controller = $controller<CreateClassicLoadBalancerCtrl>('awsCreateClassicLoadBalancerCtrl', {
        $scope: $scope,
        $uibModalInstance: {dismiss: noop, result: {then: noop}},
        infrastructureCaches: { get: () => { return {getStats: () => {return {}; } }; } },
        application: app,
        loadBalancer: loadBalancer,
        isNew: loadBalancer === null,
        forPipelineConfig: false,
        securityGroupReader: securityGroupReader,
        accountService: accountService,
        subnetReader: subnetReader,
        awsLoadBalancerTransformer: awsLoadBalancerTransformer,
      });
      controller.$onInit();
    };
  }));

  afterEach(AWSProviderSettings.resetToOriginal);

  it('requires health check path for HTTP/S', () => {
    initialize();
    const loadBalancer = {
      healthCheckProtocol: 'HTTP'
    } as IAmazonClassicLoadBalancerUpsertCommand;

    controller.loadBalancerCommand = loadBalancer;

    expect(controller.requiresHealthCheckPath()).toBe(true);

    loadBalancer.healthCheckProtocol = 'HTTPS';
    expect(controller.requiresHealthCheckPath()).toBe(true);

    loadBalancer.healthCheckProtocol = 'SSL';
    expect(controller.requiresHealthCheckPath()).toBe(false);

    loadBalancer.healthCheckProtocol = 'TCP';
    expect(controller.requiresHealthCheckPath()).toBe(false);

  });

  it('includes SSL Certificate field when any listener is HTTPS or SSL', () => {
    initialize();
    const loadBalancer = {
      listeners: [],
    } as IAmazonClassicLoadBalancerUpsertCommand;

    controller.loadBalancerCommand = loadBalancer;

    expect(controller.showSslCertificateNameField()).toBe(false);

    loadBalancer.listeners.push({ externalProtocol: 'HTTP', internalProtocol: 'HTTP', internalPort: 80, externalPort: 80 });
    expect(controller.showSslCertificateNameField()).toBe(false);

    loadBalancer.listeners.push({ externalProtocol: 'TCP', internalProtocol: 'TCP', internalPort: 80, externalPort: 80 });
    expect(controller.showSslCertificateNameField()).toBe(false);

    loadBalancer.listeners.push({ externalProtocol: 'SSL', internalProtocol: 'SSL', internalPort: 80, externalPort: 80 });
    expect(controller.showSslCertificateNameField()).toBe(true);

    loadBalancer.listeners = [{externalProtocol: 'HTTP', internalProtocol: 'HTTP', internalPort: 80, externalPort: 80 }];
    loadBalancer.listeners.push({ externalProtocol: 'HTTPS', internalProtocol: 'HTTPS', internalPort: 80, externalPort: 80 });
    expect(controller.showSslCertificateNameField()).toBe(true);

    loadBalancer.listeners = [
      { externalProtocol: 'HTTPS', internalProtocol: 'HTTPS', internalPort: 80, externalPort: 80 },
      { externalProtocol: 'HTTPS', internalProtocol: 'HTTPS', internalPort: 80, externalPort: 80 }
    ];
    expect(controller.showSslCertificateNameField()).toBe(true);
  });

  describe('prependForwardSlash', () => {
    beforeEach(() => {
      initialize();
    });
    it('should add the leading slash if it is NOT present', () => {
      const result = controller.prependForwardSlash('foo');
      expect(result).toEqual('/foo');
    });

    it('should not add the leading slash if it IS present', () => {
      const result = controller.prependForwardSlash('/foo');
      expect(result).toEqual('/foo');
    });

    it('should not add the leading slash the input is undefined', () => {
      const result = controller.prependForwardSlash(undefined);
      expect(result).toBeUndefined();
    });

    it('should not add the leading slash the input is empty string', () => {
      const result = controller.prependForwardSlash('');
      expect(result).toEqual('');
    });
  });

  describe('isInternal flag', () => {
    it('should remove the flag and set a state value if inferInternalFlagFromSubnet is true', () => {
      AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet = true;
      initialize();

      expect(controller.loadBalancerCommand.isInternal).toBe(undefined);
      expect(controller.viewState.hideInternalFlag).toBe(true);
    });

    it('should set the flag based on purpose when subnet is updated', () => {
      initialize();

      controller.subnets = [
        { purpose: 'internal/old', vpcIds: [], availabilityZones: ['us-east-1c'], label: undefined },
        { purpose: 'internal/new', vpcIds: [], availabilityZones: ['us-east-1c'], label: undefined },
        { purpose: 'external', vpcIds: [], availabilityZones: ['us-east-1c'], label: undefined }
      ];
      controller.loadBalancerCommand.subnetType = 'internal/old';
      controller.subnetUpdated();
      expect(controller.loadBalancerCommand.isInternal).toBe(true);

      controller.loadBalancerCommand.subnetType = 'external';
      controller.subnetUpdated();
      expect(controller.loadBalancerCommand.isInternal).toBe(false);

      controller.loadBalancerCommand.subnetType = 'internal/new';
      controller.subnetUpdated();
      expect(controller.loadBalancerCommand.isInternal).toBe(true);
    });

    it('should leave the flag once it has been toggled', () => {
      initialize();

      controller.subnets = [
        { purpose: 'internal/old', vpcIds: [], availabilityZones: ['us-east-1c'], label: undefined },
        { purpose: 'internal/new', vpcIds: [], availabilityZones: ['us-east-1c'], label: undefined },
        { purpose: 'external', vpcIds: [], availabilityZones: ['us-east-1c'], label: undefined }
      ];
      controller.loadBalancerCommand.isInternal = false;
      controller.viewState.internalFlagToggled = true;

      controller.loadBalancerCommand.subnetType = 'internal/old';
      controller.subnetUpdated();
      expect(controller.loadBalancerCommand.isInternal).toBe(false);

      controller.loadBalancerCommand.subnetType = 'external';
      controller.subnetUpdated();
      expect(controller.loadBalancerCommand.isInternal).toBe(false);

      controller.loadBalancerCommand.subnetType = 'internal/new';
      controller.subnetUpdated();
      expect(controller.loadBalancerCommand.isInternal).toBe(false);
    });

    it('should leave the flag and not set a state value if inferInternalFlagFromSubnet is false or not defined', () => {
      AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet = true;

      initialize();
      expect(controller.loadBalancerCommand.isInternal).toBe(undefined);
      expect(controller.viewState.hideInternalFlag).toBe(true);

      AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet = false;
      initialize();
      expect(controller.loadBalancerCommand.isInternal).toBe(false);
      expect(controller.viewState.hideInternalFlag).toBe(false);

      delete AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet;
      initialize();

      expect(controller.loadBalancerCommand.isInternal).toBe(false);
      expect(controller.viewState.hideInternalFlag).toBe(false);

      delete AWSProviderSettings.loadBalancers;
      initialize();

      expect(controller.loadBalancerCommand.isInternal).toBe(false);
      expect(controller.viewState.hideInternalFlag).toBe(false);
    });
  });

  describe('available security groups', () => {
    it('should put existing security groups in the front of the available list', () => {
      const availableSecurityGroups = {
        test: {
          aws: {
            'us-east-1': [
              {name: 'a', id: '1', vpcId: 'vpc-1'},
              {name: 'b', id: '2', vpcId: 'vpc-1'},
              {name: 'c', id: '3', vpcId: 'vpc-1'},
              {name: 'd', id: '4', vpcId: 'vpc-1'}]
          }
        }
      };
      const existingLoadBalancer = {
        name: 'elb-1',
        vpcId: 'vpc-1',
        credentials: 'test',
        region: 'us-east-1',
        securityGroups: ['4'],
        listeners: [],
      } as IAmazonClassicLoadBalancer;
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when(availableSecurityGroups));
      spyOn(accountService, 'getAccountDetails').and.returnValue($q.when([{name: 'test'}]));
      spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([{account: 'test', region: 'us-east-1', vpcId: 'vpc-1'}]));
      initialize(existingLoadBalancer);
      $scope.$digest();
      expect(controller.availableSecurityGroups.map(g => g.name)).toEqual(['d', 'a', 'b', 'c']);
    });

    it('should put default security groups in the front of the available list', () => {
      AWSProviderSettings.defaultSecurityGroups = ['sg-a'];
      AWSProviderSettings.defaults.subnetType = 'external';
      const availableSecurityGroups = {
        test: {
          aws: {
            'us-east-1': [
              {name: 'a', id: '1', vpcId: 'vpc-1'},
              {name: 'b', id: '2', vpcId: 'vpc-1'},
              {name: 'c', id: '3', vpcId: 'vpc-1'},
              {name: 'd', id: '4', vpcId: 'vpc-1'},
              {name: 'sg-a', id: '5', vpcId: 'vpc-1'}]
          }
        }
      };

      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when(availableSecurityGroups));
      spyOn(accountService, 'listAccounts').and.returnValue($q.when([{name: 'test'}]));
      spyOn(accountService, 'getAccountDetails').and.returnValue($q.when([{name: 'test'}]));
      spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([
        {account: 'test', region: 'us-east-1', vpcId: 'vpc-1', purpose: 'external'}
      ]));
      initialize();
      $scope.$digest();
      expect(controller.availableSecurityGroups.map(g => g.name)).toEqual(['sg-a', 'a', 'b', 'c', 'd']);
    });
  });

});
