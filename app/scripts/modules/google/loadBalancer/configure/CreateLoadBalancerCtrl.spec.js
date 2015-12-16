'use strict';


describe('Controller: gceCreateLoadBalancerCtrl', function () {

  const angular = require('angular');

  // load the controller's module
  beforeEach(function() {
      window.module(
        require('./CreateLoadBalancerCtrl.js')
      );
    });

  // Initialize the controller and a mock scope
  beforeEach(window.inject(function ($controller, $rootScope, _modalWizardService_) {
    this.$scope = $rootScope.$new();
    this.ctrl = $controller('gceCreateLoadBalancerCtrl', {
      $scope: this.$scope,
      $modalInstance: { dismiss: angular.noop, result: { then: angular.noop } },
      application: {name: 'testApp'},
      loadBalancer: null,
      isNew: true
    });
    this.wizardService = _modalWizardService_;
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

  it('should update name', function() {
    var lb = this.$scope.loadBalancer;
    expect(lb).toBeDefined();
    expect(lb.name).toBeUndefined();

    this.ctrl.updateName();
    expect(lb.name).toBe('testApp');

    this.$scope.loadBalancer.stack = 'testStack';
    this.ctrl.updateName();
    expect(lb.name).toBe('testApp-testStack');
  });

  it('should make the health check tab invisible then visible again', function() {
    var wiz = jasmine.createSpyObj('wizard', ['markComplete', 'markIncomplete', 'includePage', 'excludePage']);
    spyOn(this.wizardService, 'getWizard').and.returnValue(wiz);
    this.$scope.loadBalancer.listeners[0].healthCheck = false;
    this.ctrl.setVisibilityHealthCheckTab();
    expect(wiz.excludePage.calls.count()).toEqual(1);

    this.$scope.loadBalancer.listeners[0].healthCheck = true;
    this.ctrl.setVisibilityHealthCheckTab();
    expect(wiz.includePage.calls.count()).toEqual(1);
  });

});
