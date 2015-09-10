'use strict';


describe('Controller: awsInstanceDetailsCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;
  var instanceReader;
  var $q;

  beforeEach(
    window.module(
      require('./instance.details.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _instanceReader_, _$q_) {
      scope = $rootScope.$new();
      instanceReader = _instanceReader_;
      $q = _$q_;

      controller = $controller('awsInstanceDetailsCtrl', {
        $scope: scope,
        instance: {},
        app: {
          registerAutoRefreshHandler: angular.noop
        }
      });

      this.createController = function(application, instance) {
        application.registerAutoRefreshHandler = application.registerAutoRefreshHandler || angular.noop;
        controller = $controller('awsInstanceDetailsCtrl', {
          $scope: scope,
          instance: instance,
          app: application,
          recentHistoryService: {
            addExtraDataToLatest: angular.noop,
          },
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
      var application = {
        serverGroups: [
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
        ]
      };

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

      var application = {
        serverGroups: [
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
        ]
      };

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
