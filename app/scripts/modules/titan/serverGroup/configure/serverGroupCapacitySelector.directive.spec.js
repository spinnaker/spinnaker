'use strict';

let angular = require('angular');
require('./serverGroupCapacityDirective.html');

describe('Directive: GCE Group Capacity Selector', function() {

  beforeEach(
    window.module(
      require('./serverGroupCapacitySelector.directive.js')
    )
  );

  beforeEach(window.inject(function($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.scope.command = {capacity: {}};
    this.elem = angular.element('<titan-server-group-capacity-selector command="command" />');
    this.element = $compile(this.elem)(this.scope);
    this.scope.$digest();
  }));

  it('should correctly assign min/max/desired capacity to the same values', function() {
    expect(this.scope.command.capacity.max).toBeUndefined();
    expect(this.scope.command.capacity.desired).toBeUndefined();
    expect(this.scope.command.capacity.min).toBeUndefined();

    this.elem.find('input').val(3).trigger('input');
    this.scope.$apply();

    expect(this.scope.command.capacity.max).toEqual(3);
    expect(this.scope.command.capacity.desired).toEqual(3);
    expect(this.scope.command.capacity.min).toEqual(3);
  });
});
