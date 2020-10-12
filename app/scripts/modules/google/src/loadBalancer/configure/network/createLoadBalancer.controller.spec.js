'use strict';

import * as angular from 'angular';
import { ApplicationModelBuilder, ModalWizard } from '@spinnaker/core';

describe('Controller: gceCreateLoadBalancerCtrl', function () {
  // load the controller's module
  beforeEach(function () {
    window.module(require('./createLoadBalancer.controller').name);
  });

  // Initialize the controller and a mock scope
  beforeEach(
    window.inject(function ($controller, $rootScope) {
      this.$scope = $rootScope.$new();
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      this.ctrl = $controller('gceCreateLoadBalancerCtrl', {
        $scope: this.$scope,
        $uibModalInstance: { dismiss: angular.noop, result: { then: angular.noop } },
        application: app,
        loadBalancer: null,
        isNew: true,
      });
    }),
  );

  it('requires health check path for HTTP/S', function () {
    const loadBalancer = {
      healthCheckProtocol: 'HTTP',
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

  it('should update name', function () {
    const lb = this.$scope.loadBalancer;
    expect(lb).toBeDefined();
    expect(lb.name).toBeUndefined();

    this.ctrl.updateName();
    expect(lb.name).toBe('app');

    this.$scope.loadBalancer.stack = 'testStack';
    this.ctrl.updateName();
    expect(lb.name).toBe('app-testStack');
  });

  it('should make the health check tab invisible then visible again', function () {
    spyOn(ModalWizard, 'includePage');
    spyOn(ModalWizard, 'markIncomplete');
    spyOn(ModalWizard, 'excludePage');
    spyOn(ModalWizard, 'markComplete');
    this.$scope.loadBalancer.listeners[0].healthCheck = false;
    this.ctrl.setVisibilityHealthCheckTab();
    expect(ModalWizard.excludePage.calls.count()).toEqual(2);

    this.$scope.loadBalancer.listeners[0].healthCheck = true;
    this.ctrl.setVisibilityHealthCheckTab();
    expect(ModalWizard.includePage.calls.count()).toEqual(2);
  });
});
