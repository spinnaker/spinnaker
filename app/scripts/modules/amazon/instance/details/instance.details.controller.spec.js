'use strict';

describe('Controller: awsInstanceDetailsCtrl', function () {

  var controller;
  var scope;
  var instanceReader;
  var $q;
  var applicationReader;

  beforeEach(
    window.module(
      require('./instance.details.controller'),
      require('../../../core/application/service/applications.read.service')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _instanceReader_, _$q_, _applicationReader_) {
      scope = $rootScope.$new();
      instanceReader = _instanceReader_;
      $q = _$q_;
      applicationReader = _applicationReader_;

      this.createController = function(application, instance) {
        controller = $controller('awsInstanceDetailsCtrl', {
          $scope: scope,
          instance: instance,
          app: application,
          recentHistoryService: {
            addExtraDataToLatest: angular.noop,
          },
          overrides: {},
        });
      };
    })
  );

  describe('health metrics', function () {
    it('overrides new health with health from application, adding new fields', function() {
      var details = {
        health: [
          { type: 'Discovery', status: 'Up', extra: 'details field', reason: 'mutated'}
        ]
      };
      var params = {
        instanceId: 'i-123', region: 'us-west-1', account: 'test'
      };

      spyOn(instanceReader, 'getInstanceDetails').and.returnValue(
        $q.when({
          plain: function() {
            return details;
          }
        })
      );
      var application = { attributes: {} };

      applicationReader.addSectionToApplication({key: 'loadBalancers', lazy: true}, application);
      application.loadBalancers.data = [];
      application.loadBalancers.loaded = true;

      applicationReader.addSectionToApplication({key: 'serverGroups', lazy: true}, application);
      application.serverGroups.data = [
        {
          account: 'test',
          region: 'us-west-1',
          instances: [
            {
              id: 'i-123',
              health: [
                { type: 'Discovery', status: 'Down', reason: 'original reason'}
              ]
            }
          ]
        }
      ];
      application.serverGroups.loaded = true;

      this.createController(application, params);
      scope.$digest();

      expect(scope.healthMetrics.length).toBe(1);
      expect(scope.healthMetrics[0].reason).toBe('original reason');
      expect(scope.healthMetrics[0].status).toBe('Down');
      expect(scope.healthMetrics[0].extra).toBe('details field');
    });
  });

  describe('canRegister methods', function() {
    beforeEach(function() {
      var details = { };
      var params = {
        instanceId: 'i-123', region: 'us-west-1', account: 'test'
      };

      spyOn(instanceReader, 'getInstanceDetails').and.returnValue(
        $q.when({
          plain: function() {
            return details;
          }
        })
      );

      var application = { attributes: {} };

      applicationReader.addSectionToApplication({key: 'loadBalancers', lazy: true}, application);
      application.loadBalancers.data = [];
      application.loadBalancers.loaded = true;

      applicationReader.addSectionToApplication({key: 'serverGroups', lazy: true}, application);
      application.serverGroups.data = [
        {
          account: 'test',
          region: 'us-west-1',
          instances: [
            {
              id: 'i-123',
              health: [
                { type: 'Discovery', state: 'Up', reason: 'original reason'}
              ]
            }
          ]
        }
      ];
      application.serverGroups.loaded = true;

      this.createController(application, params);
      scope.$digest();
    });

    it('can register with discovery when discovery', function() {
      expect(controller.canRegisterWithDiscovery()).toBe(false);
      scope.instance.health[0].state = 'OutOfService';
      expect(controller.canRegisterWithDiscovery()).toBe(true);
      scope.instance.health[0].state = 'Down';
      expect(controller.canRegisterWithDiscovery()).toBe(false);
      scope.instance.health = [];
      expect(controller.canRegisterWithDiscovery()).toBe(false);
    });

    it('can register with load balancer', function() {
      expect(controller.canRegisterWithLoadBalancer()).toBe(false);
      scope.instance.health[0].type = 'LoadBalancer';
      scope.instance.health[0].state = 'OutOfService';
      expect(controller.canRegisterWithLoadBalancer()).toBe(false);
      // add a load balancer
      scope.instance.loadBalancers = ['elb-1'];
      expect(controller.canRegisterWithLoadBalancer()).toBe(true);
      scope.instance.health[0].state = 'Up';
      expect(controller.canRegisterWithLoadBalancer()).toBe(false);

    });
  });
});
