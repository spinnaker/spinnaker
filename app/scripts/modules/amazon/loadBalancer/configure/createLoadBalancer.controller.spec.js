'use strict';

describe('Controller: awsCreateLoadBalancerCtrl', function () {

  // load the controller's module
  beforeEach(
    window.module(
      require('./createLoadBalancer.controller')
    )
  );

  // Initialize the controller and a mock scope
  beforeEach(window.inject(function ($controller, $rootScope) {
    this.settings = {};
    this.$scope = $rootScope.$new();
    this.initialize = () => {
      this.ctrl = $controller('awsCreateLoadBalancerCtrl', {
        $scope: this.$scope,
        $uibModalInstance: {dismiss: angular.noop, result: {then: angular.noop}},
        application: {name: 'app', defaultCredentials: {}, defaultRegions: {}},
        loadBalancer: null,
        isNew: true,
        forPipelineConfig: false,
        settings: this.settings,
      });
    };
  }));

  it('requires health check path for HTTP/S', function () {
    this.initialize();
    var loadBalancer = {
      healthCheckProtocol: 'HTTP'
    };

    this.$scope.loadBalancer = loadBalancer;

    expect(this.ctrl.requiresHealthCheckPath()).toBe(true);

    loadBalancer.healthCheckProtocol = 'HTTPS';
    expect(this.ctrl.requiresHealthCheckPath()).toBe(true);

    loadBalancer.healthCheckProtocol = 'SSL';
    expect(this.ctrl.requiresHealthCheckPath()).toBe(false);

    loadBalancer.healthCheckProtocol = 'TCP';
    expect(this.ctrl.requiresHealthCheckPath()).toBe(false);

  });

  it('includes SSL Certificate field when any listener is HTTPS or SSL', function() {
    this.initialize();
    var loadBalancer = {
      listeners: [],
    };

    this.$scope.loadBalancer = loadBalancer;

    expect(this.ctrl.showSslCertificateIdField()).toBe(false);

    loadBalancer.listeners.push({ externalProtocol: 'HTTP' });
    expect(this.ctrl.showSslCertificateIdField()).toBe(false);

    loadBalancer.listeners.push({ externalProtocol: 'TCP' });
    expect(this.ctrl.showSslCertificateIdField()).toBe(false);

    loadBalancer.listeners.push({ externalProtocol: 'SSL' });
    expect(this.ctrl.showSslCertificateIdField()).toBe(true);

    loadBalancer.listeners = [{externalProtocol: 'HTTP'}];
    loadBalancer.listeners.push({ externalProtocol: 'HTTPS' });
    expect(this.ctrl.showSslCertificateIdField()).toBe(true);

    loadBalancer.listeners = [ { externalProtocol: 'HTTPS' }, { externalProtocol: 'HTTPS' }];
    expect(this.ctrl.showSslCertificateIdField()).toBe(true);
  });

  describe('prependForwardSlash', function () {
    beforeEach(this.initialize);
    it('should add the leading slash if it is NOT present', function () {
      let result = this.ctrl.prependForwardSlash('foo');
      expect(result).toEqual('/foo');
    });

    it('should not add the leading slash if it IS present', function () {
      let result = this.ctrl.prependForwardSlash('/foo');
      expect(result).toEqual('/foo');
    });

    it('should not add the leading slash the input is undefined', function () {
      let result = this.ctrl.prependForwardSlash(undefined);
      expect(result).toBeUndefined();
    });

    it('should not add the leading slash the input is empty string', function () {
      let result = this.ctrl.prependForwardSlash('');
      expect(result).toEqual('');
    });
  });

  describe('isInternal flag', function () {
    it('should remove the flag and set a state value if inferInternalFlagFromSubnet is true', function () {
      this.settings.providers = {
        aws: { loadBalancers: { inferInternalFlagFromSubnet: true }}
      };
      this.initialize();

      expect(this.$scope.loadBalancer.isInternal).toBe(undefined);
      expect(this.$scope.state.hideInternalFlag).toBe(true);
    });

    it('should set the flag based on purpose when subnet is updated', function () {
      this.initialize();

      this.$scope.subnets = [
        { purpose: 'internal/old', vpcIds: [], availabilityZones: ['us-east-1c'] },
        { purpose: 'internal/new', vpcIds: [], availabilityZones: ['us-east-1c'] },
        { purpose: 'external', vpcIds: [], availabilityZones: ['us-east-1c'] }
      ];
      this.$scope.loadBalancer.subnetType = 'internal/old';
      this.ctrl.subnetUpdated();
      expect(this.$scope.loadBalancer.isInternal).toBe(true);

      this.$scope.loadBalancer.subnetType = 'external';
      this.ctrl.subnetUpdated();
      expect(this.$scope.loadBalancer.isInternal).toBe(false);

      this.$scope.loadBalancer.subnetType = 'internal/new';
      this.ctrl.subnetUpdated();
      expect(this.$scope.loadBalancer.isInternal).toBe(true);
    });

    it('should leave the flag once it has been toggled', function () {
      this.initialize();

      this.$scope.subnets = [
        { purpose: 'internal/old', vpcIds: [], availabilityZones: ['us-east-1c'] },
        { purpose: 'internal/new', vpcIds: [], availabilityZones: ['us-east-1c'] },
        { purpose: 'external', vpcIds: [], availabilityZones: ['us-east-1c'] }
      ];
      this.$scope.loadBalancer.isInternal = false;
      this.$scope.state.internalFlagToggled = true;

      this.$scope.loadBalancer.subnetType = 'internal/old';
      this.ctrl.subnetUpdated();
      expect(this.$scope.loadBalancer.isInternal).toBe(false);

      this.$scope.loadBalancer.subnetType = 'external';
      this.ctrl.subnetUpdated();
      expect(this.$scope.loadBalancer.isInternal).toBe(false);

      this.$scope.loadBalancer.subnetType = 'internal/new';
      this.ctrl.subnetUpdated();
      expect(this.$scope.loadBalancer.isInternal).toBe(false);
    });

    it('should leave the flag and not set a state value if inferInternalFlagFromSubnet is false or not defined', function () {
      this.settings.providers = {
        aws: { loadBalancers: { inferInternalFlagFromSubnet: true }}
      };

      this.initialize();
      expect(this.$scope.loadBalancer.isInternal).toBe(undefined);
      expect(this.$scope.state.hideInternalFlag).toBe(true);

      this.settings.providers.aws.loadBalancers.inferInternalFlagFromSubnet = false;
      this.initialize();
      expect(this.$scope.loadBalancer.isInternal).toBe(false);
      expect(this.$scope.state.hideInternalFlag).toBeUndefined();

      delete this.settings.providers.aws.loadBalancers.inferInternalFlagFromSubnet;
      this.initialize();

      expect(this.$scope.loadBalancer.isInternal).toBe(false);
      expect(this.$scope.state.hideInternalFlag).toBeUndefined();

      delete this.settings.providers.aws.loadBalancers;
      this.initialize();

      expect(this.$scope.loadBalancer.isInternal).toBe(false);
      expect(this.$scope.state.hideInternalFlag).toBeUndefined();

      delete this.settings.providers.aws;
      this.initialize();

      expect(this.$scope.loadBalancer.isInternal).toBe(false);
      expect(this.$scope.state.hideInternalFlag).toBeUndefined();

      delete this.settings.providers;
      this.initialize();

      expect(this.$scope.loadBalancer.isInternal).toBe(false);
      expect(this.$scope.state.hideInternalFlag).toBeUndefined();
    });
  });

});
