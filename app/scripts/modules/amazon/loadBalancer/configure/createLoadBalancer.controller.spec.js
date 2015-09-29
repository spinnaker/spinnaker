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
    this.$scope = $rootScope.$new();
    this.ctrl = $controller('awsCreateLoadBalancerCtrl', {
      $scope: this.$scope,
      $modalInstance: { dismiss: angular.noop, result: { then: angular.noop } },
      application: {name: 'app'},
      loadBalancer: null,
      isNew: true
    });
  }));

  it('requires health check path for HTTP/S', function () {
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

});
