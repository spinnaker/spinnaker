'use strict';

import { API, ApplicationModelBuilder } from '@spinnaker/core';

describe('Controller: azureCreateLoadBalancerCtrl', function () {
  var $httpBackend;

  // load the controller's module
  beforeEach(window.module(require('./createLoadBalancer.controller').name));

  // Initialize the controller and a mock scope
  beforeEach(
    window.inject(function ($controller, $rootScope) {
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      this.$scope = $rootScope.$new();
      this.ctrl = $controller('azureCreateLoadBalancerCtrl', {
        $scope: this.$scope,
        $uibModalInstance: { dismiss: angular.noop, result: { then: angular.noop } },
        application: app,
        loadBalancer: null,
        isNew: true,
        loadBalancerType: 'Azure Load Balancer',
      });
    }),
  );

  beforeEach(
    window.inject(function (_$httpBackend_) {
      // Set up the mock http service responses
      $httpBackend = _$httpBackend_;
    }),
  );

  it('correctly creates a default loadbalancer', function () {
    var lb = this.$scope.loadBalancer;

    expect(lb.probes.length).toEqual(1);
    expect(lb.loadBalancingRules.length).toEqual(1);

    expect(lb.loadBalancingRules[0].protocol).toEqual('HTTP');

    expect(this.$scope.existingLoadBalancerNames).toEqual(undefined);
    expect(lb.providerType).toEqual(undefined);
  });

  it('makes the expected REST calls for data for a new loadbalancer', function () {
    $httpBackend.when('GET', API.baseUrl + '/networks').respond([]);
    $httpBackend.when('GET', API.baseUrl + '/securityGroups').respond({});
    $httpBackend.when('GET', API.baseUrl + '/credentials?expand=true').respond([]);
    $httpBackend.when('GET', API.baseUrl + '/subnets').respond([]);
  });
});
