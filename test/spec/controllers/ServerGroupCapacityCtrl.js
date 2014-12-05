'use strict';

describe('Controller: ServerGroupCapacity', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(module('deckApp'));

  beforeEach(inject(function ($controller, $rootScope, modalWizardService) {
    this.scope = $rootScope.$new();

    this.scope.command = {
      capacity: {
        min: 0,
        max: 0,
        desired: 0
      },
      viewState: {
        useSimpleCapacity: false
      }
    };

    this.wizard = jasmine.createSpyObj('wizard', ['markComplete', 'markClean', 'markDirty']);

    spyOn(modalWizardService, 'getWizard').and.returnValue(this.wizard);

    this.ctrl = $controller('ServerGroupCapacityCtrl', {
      $scope: this.scope,
      modalWizardService: modalWizardService
    });
  }));


  it('synchronizes capacity only when in simple capacity mode', function() {
    var scope = this.scope,
        command = scope.command;

    command.viewState.useSimpleCapacity = true;
    command.capacity.desired = 2;
    scope.$digest();

    expect(command.capacity.min).toBe(2);
    expect(command.capacity.max).toBe(2);

    command.viewState.useSimpleCapacity = false;
    command.capacity.desired = 1;
    scope.$digest();

    expect(command.capacity.min).toBe(2);
    expect(command.capacity.max).toBe(2);

  });


});
