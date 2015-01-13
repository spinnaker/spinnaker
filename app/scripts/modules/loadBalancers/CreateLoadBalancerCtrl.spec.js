'use strict';

describe('Controller: CreateLoadBalancerCtrl', function () {

  // load the controller's module
  beforeEach(module('deckApp'));

  // Initialize the controller and a mock scope
  beforeEach(inject(function ($controller, $rootScope) {
    this.$scope = $rootScope.$new();
    this.ctrl = $controller('CreateLoadBalancerCtrl', {
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

  it('sets maximum length of stack based on application name', function() {
    // 19 === 32 - 3 (application name length) - 9 ("-frontend" length) - 1 (dash separator)
    expect(this.$scope.state.maxStackLength).toEqual(19);
  });
});
