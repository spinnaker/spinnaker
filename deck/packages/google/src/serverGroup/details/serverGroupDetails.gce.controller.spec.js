'use strict';

import { ClusterTargetBuilder, NetworkReader, ServerGroupReader } from '@spinnaker/core';

describe('Controller: gceServerGroupDetailsCtrl', function () {
  let $controller;
  let $q;
  let scope;
  let app;
  let serverGroup;

  beforeEach(window.module(require('./serverGroupDetails.gce.controller').name));

  beforeEach(
    window.inject(function ($rootScope, _$controller_, _$q_) {
      $controller = _$controller_;
      $q = _$q_;
      scope = $rootScope.$new();
      app = {
        name: 'myapp',
        serverGroups: { data: [], onRefresh: angular.noop },
        loadBalancers: { data: [] },
        securityGroups: { data: [] },
      };
      serverGroup = { name: 'myapp-main-v000', accountId: 'test-account', region: 'us-central1' };
    }),
  );

  function buildServerGroupDetails(shieldedKey, shieldedConfig) {
    return {
      name: 'myapp-main-v000',
      region: 'us-central1',
      zones: ['us-central1-a'],
      securityGroups: [],
      launchConfig: {
        instanceTemplate: {
          properties: {
            networkInterfaces: [{ network: 'projects/test/global/networks/default' }],
            metadata: { items: [] },
            scheduling: { preemptible: false, automaticRestart: true, onHostMaintenance: 'MIGRATE' },
            tags: { items: [] },
            serviceAccounts: [],
            [shieldedKey]: shieldedConfig,
          },
        },
      },
    };
  }

  function initializeController(details) {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue($q.when(details));
    spyOn(NetworkReader, 'listNetworksByProvider').and.returnValue($q.when([]));
    spyOn(ClusterTargetBuilder, 'buildClusterTargets').and.returnValue([]);

    const controller = $controller('gceServerGroupDetailsCtrl', {
      $scope: scope,
      app: app,
      serverGroup: serverGroup,
      $state: { go: angular.noop, includes: () => false },
      gceServerGroupCommandBuilder: { buildServerGroupCommandFromExisting: () => $q.when({}) },
      $uibModal: { open: angular.noop },
      serverGroupWriter: {},
      gceXpnNamingService: {
        deriveProjectId: () => 'test-project',
        decorateXpnResourceIfNecessary: (_projectId, resource) => resource,
      },
    });
    scope.$digest();
    return controller;
  }

  it('should instantiate the controller', function () {
    const controller = initializeController(buildServerGroupDetails('shieldedInstanceConfig', null));
    expect(controller).toBeDefined();
  });

  it('uses shieldedInstanceConfig from server group details', function () {
    const controller = initializeController(
      buildServerGroupDetails('shieldedInstanceConfig', {
        enableSecureBoot: true,
        enableVtpm: false,
        enableIntegrityMonitoring: true,
      }),
    );

    expect(controller.serverGroup.shieldedVmConfig).toEqual({
      enableSecureBoot: 'On',
      enableVtpm: 'Off',
      enableIntegrityMonitoring: 'On',
    });
  });

  it('falls back to legacy shieldedVmConfig from server group details', function () {
    const controller = initializeController(
      buildServerGroupDetails('shieldedVmConfig', {
        enableSecureBoot: false,
        enableVtpm: true,
        enableIntegrityMonitoring: false,
      }),
    );

    expect(controller.serverGroup.shieldedVmConfig).toEqual({
      enableSecureBoot: 'Off',
      enableVtpm: 'On',
      enableIntegrityMonitoring: 'Off',
    });
  });
});
