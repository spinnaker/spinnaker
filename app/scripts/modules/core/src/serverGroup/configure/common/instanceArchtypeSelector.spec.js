'use strict';

import { INSTANCE_TYPE_SERVICE } from '../../../instance/instanceType.service';
import { SERVER_GROUP_CONFIGURATION_SERVICE } from './serverGroupConfiguration.service';

describe('Controller: Instance Archetype Selector', function () {
  var categories = [
    {
      type: 'cpu',
    },
    {
      type: 'micro',
    },
  ];

  beforeEach(
    window.module(
      require('./instanceArchetypeSelector.js').name,
      INSTANCE_TYPE_SERVICE,
      SERVER_GROUP_CONFIGURATION_SERVICE,
    ),
  );

  beforeEach(
    window.inject(function ($controller, $rootScope, instanceTypeService, serverGroupConfigurationService, $q) {
      this.$scope = $rootScope.$new();
      this.$scope.command = { viewState: { instanceProfile: null } };
      this.$controller = $controller;
      this.controllerDeps = {
        $scope: this.$scope,
        instanceTypeService: instanceTypeService,
        serverGroupConfigurationService: serverGroupConfigurationService,
      };
      spyOn(instanceTypeService, 'getCategories').and.returnValue($q.when(categories));
      spyOn(instanceTypeService, 'getAllTypesByRegion').and.returnValue($q.when(categories));
    }),
  );

  it('should select a profile, change it, then unselect it', function () {
    this.$scope.command.selectedProvider = 'gce'; // Doesn't matter which, since this module is shared.
    this.ctrl = this.$controller('InstanceArchetypeSelectorCtrl', this.controllerDeps);
    this.$scope.$apply();

    expect(this.$scope.selectedInstanceProfile).toBeUndefined();
    this.ctrl.selectInstanceType('micro');
    expect(this.$scope.selectedInstanceProfile.type).toBe('micro');

    this.ctrl.selectInstanceType('cpu');
    expect(this.$scope.selectedInstanceProfile.type).toBe('cpu');

    // When the user unselects an item, the same instance type is passed.
    this.ctrl.selectInstanceType('cpu');
    expect(this.$scope.selectedInstanceProfile).toBeNull();

    // Ensure the user can still select it
    this.ctrl.selectInstanceType('cpu');
    expect(this.$scope.selectedInstanceProfile.type).toBe('cpu');
  });
});
