'use strict';

describe('Controller: ServerGroupAdvancedSettings', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(module('deckApp'));

  beforeEach(inject(function ($controller, $rootScope) {
    this.scope = $rootScope.$new();

    this.scope.command = {
      suspendedProcesses: [],
    };

    this.ctrl = $controller('ServerGroupAdvancedSettingsCtrl', {
      $scope: this.scope,
    });
  }));


  it('toggleSuspendedProcess adds suspended processes to command if absent, removes if present', function() {
    var scope = this.scope,
      command = scope.command;

    expect(command.suspendedProcesses).toEqual([]);

    this.ctrl.toggleSuspendedProcess('foo');
    expect(command.suspendedProcesses).toEqual(['foo']);

    this.ctrl.toggleSuspendedProcess('bar');
    expect(command.suspendedProcesses).toEqual(['foo', 'bar']);

    this.ctrl.toggleSuspendedProcess('baz');
    expect(command.suspendedProcesses).toEqual(['foo', 'bar', 'baz']);

    this.ctrl.toggleSuspendedProcess('bar');
    expect(command.suspendedProcesses).toEqual(['foo', 'baz']);

    this.ctrl.toggleSuspendedProcess('foo');
    expect(command.suspendedProcesses).toEqual(['baz']);

    this.ctrl.toggleSuspendedProcess('baz');
    expect(command.suspendedProcesses).toEqual([]);
  });

  it('processIsSuspended returns true if process is in suspendedProcesses array, false otherwise', function() {
    var scope = this.scope,
      command = scope.command;

    expect(command.suspendedProcesses).toEqual([]);
    expect(this.ctrl.processIsSuspended('foo')).toBe(false);

    command.suspendedProcesses = ['foo'];
    expect(this.ctrl.processIsSuspended('foo')).toBe(true);
    expect(this.ctrl.processIsSuspended('bar')).toBe(false);
  });


});
