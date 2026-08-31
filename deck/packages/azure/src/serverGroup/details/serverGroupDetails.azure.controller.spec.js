import { ApplicationModelBuilder, ServerGroupReader } from '@spinnaker/core';

describe('Controller: azureServerGroupDetailsCtrl guards', function () {
  var controller;
  var scope;
  var application;
  var $q;

  beforeEach(window.module(require('./serverGroupDetails.azure.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller, _$q_) {
      scope = $rootScope.$new();
      $q = _$q_;
      application = ApplicationModelBuilder.createApplicationForTests(
        'app',
        { key: 'serverGroups', lazy: true, defaultData: [] },
        { key: 'loadBalancers', lazy: true, defaultData: [] },
      );

      this.createController = function (sg, siblings) {
        application.serverGroups.data = [sg].concat(siblings || []);
        application.serverGroups.loaded = true;
        application.loadBalancers.data = [];
        application.loadBalancers.loaded = true;

        spyOn(ServerGroupReader, 'getServerGroup').and.returnValue($q.when(sg));

        controller = $controller('azureServerGroupDetailsCtrl', {
          $scope: scope,
          app: application,
          serverGroup: { name: sg.name, accountId: sg.account, region: sg.region },
        });
        scope.$digest();

        // the guards read $scope.serverGroup; pin it regardless of what the fetch assigned
        scope.serverGroup = sg;
      };
    }),
  );

  const serverGroup = (overrides) =>
    Object.assign(
      {
        name: 'myapp-dev-v086',
        cluster: 'myapp-dev',
        account: 'test',
        region: 'westus',
        isDisabled: false,
        instanceCounts: { outOfService: 0 },
        runningTasks: [],
      },
      overrides,
    );

  it('enables rollback for an enabled server group', function () {
    this.createController(serverGroup());
    expect(controller.isRollbackEnabled()).toBe(true);
  });

  it('enables rollback for a disabled server group when an enabled sibling exists', function () {
    this.createController(serverGroup({ isDisabled: true }), [serverGroup({ name: 'myapp-dev-v087' })]);
    expect(controller.isRollbackEnabled()).toBe(true);
  });

  it('disables rollback for a disabled server group with no enabled sibling', function () {
    this.createController(serverGroup({ isDisabled: true }), [
      serverGroup({ name: 'myapp-dev-v087', isDisabled: true }),
    ]);
    expect(controller.isRollbackEnabled()).toBe(false);
  });

  it('reports disabled instances when the server group is disabled', function () {
    this.createController(serverGroup({ isDisabled: true }));
    expect(controller.hasDisabledInstances()).toBe(true);
  });

  it('reports disabled instances when some instances are out of service', function () {
    this.createController(serverGroup({ instanceCounts: { outOfService: 2 } }));
    expect(controller.hasDisabledInstances()).toBe(true);
  });

  it('reports no disabled instances for a healthy enabled server group', function () {
    this.createController(serverGroup());
    expect(controller.hasDisabledInstances()).toBe(false);
  });

  it('locks enable while a resize task runs against a disabled server group', function () {
    this.createController(
      serverGroup({
        isDisabled: true,
        runningTasks: [{ execution: { stages: [{ type: 'resizeServerGroup' }] } }],
      }),
    );
    expect(controller.isEnableLocked()).toBe(true);
  });

  it('does not lock enable when no resize task is running', function () {
    this.createController(serverGroup({ isDisabled: true }));
    expect(controller.isEnableLocked()).toBe(false);
  });
});
