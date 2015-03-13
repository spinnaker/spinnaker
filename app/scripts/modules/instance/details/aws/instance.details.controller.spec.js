'use strict';

describe('Controller: awsInstanceDetailsCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;
  var instanceReader;
  var $q;

  beforeEach(
    module('deckApp.instance.detail.aws.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller, _instanceReader_, _$q_) {
      scope = $rootScope.$new();
      instanceReader = _instanceReader_;
      $q = _$q_;

      controller = $controller('awsInstanceDetailsCtrl', {
        $scope: scope,
        instance: {},
        application: {
          registerAutoRefreshHandler: angular.noop
        }
      });

      this.createController = function(application, instance) {
        application.registerAutoRefreshHandler = application.registerAutoRefreshHandler || angular.noop;
        controller = $controller('awsInstanceDetailsCtrl', {
          $scope: scope,
          instance: instance,
          application: application,
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
        clusters: [ {
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
        }]
      };

      this.createController(application, params);
      scope.$digest();

      expect(scope.healthMetrics.length).toBe(1);
      expect(scope.healthMetrics[0].reason).toBe('original reason');
      expect(scope.healthMetrics[0].status).toBe('Down');
      expect(scope.healthMetrics[0].extra).toBe('details field');
    });
  });
});
