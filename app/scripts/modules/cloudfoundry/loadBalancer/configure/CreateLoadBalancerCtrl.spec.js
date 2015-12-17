'use strict';


describe('Controller: cfCreateLoadBalancerCtrl', function () {

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
    this.ctrl = $controller('cfCreateLoadBalancerCtrl', {
      $scope: this.$scope,
      $modalInstance: { dismiss: angular.noop, result: { then: angular.noop } },
      application: {name: 'testApp'},
      loadBalancer: null,
      isNew: true
    });
    this.wizardService = _modalWizardService_;
  }));

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

});
