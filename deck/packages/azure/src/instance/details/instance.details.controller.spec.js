import { ApplicationModelBuilder, InstanceReader } from '@spinnaker/core';

describe('Controller: azureInstanceDetailsCtrl', function () {
  var controller;
  var scope;
  var $q;
  var application;

  beforeEach(window.module(require('./instance.details.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller, _$q_) {
      scope = $rootScope.$new();
      $q = _$q_;

      application = ApplicationModelBuilder.createApplicationForTests(
        'app',
        { key: 'loadBalancers', lazy: true, defaultData: [] },
        { key: 'serverGroups', lazy: true, defaultData: [] },
      );

      this.createController = function (application, instance) {
        controller = $controller('azureInstanceDetailsCtrl', {
          $scope: scope,
          instance: instance,
          app: application,
        });
      };
    }),
  );

  describe('health metrics', function () {
    it('overrides new health with health from application, adding new fields', function () {
      var details = {
        health: [{ type: 'Discovery', status: 'Up', extra: 'details field', reason: 'mutated' }],
      };
      var params = {
        instanceId: 'i-123',
        region: 'us-west-1',
        account: 'test',
      };

      spyOn(InstanceReader, 'getInstanceDetails').and.returnValue($q.when(details));

      application.loadBalancers.loaded = true;

      application.serverGroups.data = [
        {
          account: 'test',
          region: 'us-west-1',
          instances: [
            {
              id: 'i-123',
              health: [{ type: 'Discovery', status: 'Down', reason: 'original reason' }],
            },
          ],
        },
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

  describe('instance actions', function () {
    function buildController(instanceOverrides) {
      var params = { instanceId: 'myapp-dev-v086_3', region: 'westus', account: 'test' };

      spyOn(InstanceReader, 'getInstanceDetails').and.returnValue($q.when({ health: [] }));

      application.serverGroups.data = [
        {
          name: 'myapp-dev-v086',
          account: 'test',
          region: 'westus',
          loadBalancers: [],
          instances: [Object.assign({ name: 'myapp-dev-v086_3', health: [] }, instanceOverrides || {})],
        },
      ];
      application.serverGroups.loaded = true;
      application.loadBalancers.loaded = true;

      this.createController(application, params);
      scope.$digest();
    }

    it('matches a server group instance by name when it has no id', function () {
      buildController.call(this);

      expect(scope.instance).toBeDefined();
      expect(scope.instance.id).toBe('myapp-dev-v086_3');
      expect(scope.instance.serverGroup).toBe('myapp-dev-v086');
    });

    it('offers reboot, terminate, and terminate and shrink', function () {
      buildController.call(this);

      expect(scope.instanceActions.map((a) => a.label)).toEqual([
        'Reboot',
        'Terminate',
        'Terminate and Shrink Server Group',
      ]);
    });

    it('omits terminate and shrink when the instance has no server group', function () {
      buildController.call(this);
      scope.instance.serverGroup = null;
      scope.instanceActions = controller.constructInstanceActions(scope.instance);

      expect(scope.instanceActions.map((a) => a.label)).toEqual(['Reboot', 'Terminate']);
    });
  });
});
