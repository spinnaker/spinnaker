'use strict'

describe('Controller: Instance Archetype Selector', function() {

  beforeEach(module('spinnaker.serverGroup.configure.common'));

  beforeEach(inject(function ($controller, $rootScope, instanceTypeService, infrastructureCaches,
                              serverGroupConfigurationService) {
    this.$scope = $rootScope.$new();
    this.$scope.command = {viewState: {instanceProfile: null}};
    this.$controller = $controller;
    this.controllerDeps = {
      $scope: this.$scope,
      instanceTypeService: instanceTypeService,
      infrastructureCaches: infrastructureCaches,
      serverGroupConfigurationService: serverGroupConfigurationService
    };
  }));

  it('should select a profile, change it, then unselect it', function(){
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
