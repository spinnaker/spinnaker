'use strict';

let angular = require('angular');
require('./serverGroupAdvancedSettingsDirective.html');

describe('Directive: Titan SG Advanced Settings Selector', function() {

  beforeEach(
    window.module(
      require('./serverGroupAdvancedSettingsSelector.directive.js')
    )
  );

  beforeEach(window.inject(function($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.scope.command = {instanceMetadata: [], tags: []};
    this.elem = angular.element('<titan-server-group-advanced-settings-selector command="command" />');
    this.element = $compile(this.elem)(this.scope);
    this.scope.$digest();
  }));

  it('should correctly add a metadata key/value to the command', function() {
    expect(this.scope.command.instanceMetadata.length).toEqual(0);

    this.elem.find('table.metadata button').trigger('click');
    this.scope.$apply();
    expect(this.scope.command.instanceMetadata.length).toEqual(1);

    var inputs = this.elem.find('table.metadata input');
    expect(inputs.length).toEqual(2);
    $(inputs[0]).val('myKey').trigger('input');
    $(inputs[1]).val('myVal').trigger('input');
    this.scope.$apply();

    expect(this.scope.command.instanceMetadata.length).toEqual(1);
    expect(this.scope.command.instanceMetadata[0].key).toEqual('myKey');
    expect(this.scope.command.instanceMetadata[0].value).toEqual('myVal');
  });

  it('should correctly remove a metadata key/value from the command', function() {
    this.scope.command.instanceMetadata.push({'key': 'myKey1', 'value': 'myVal1'},
      {'key': 'myKey2', 'value': 'myVal2'});
    this.scope.$apply();

    var removeLinks = this.elem.find('table.metadata a');
    expect(removeLinks.length).toEqual(2);

    $(removeLinks[0]).trigger('click');
    this.scope.$apply();

    expect(this.scope.command.instanceMetadata.length).toEqual(1);
    expect(this.scope.command.instanceMetadata[0].key).toEqual('myKey2');
    expect(this.scope.command.instanceMetadata[0].value).toEqual('myVal2');
  });

  it('should correctly add tags to the command', function() {
    expect(this.scope.command.tags.length).toEqual(0);

    this.elem.find('table.tags button').trigger('click');
    this.scope.$apply();
    expect(this.scope.command.tags.length).toEqual(1);

    this.elem.find('table.tags input').val('myTag').trigger('input');
    this.scope.$apply();

    expect(this.scope.command.tags.length).toEqual(1);
    expect(this.scope.command.tags[0].value).toEqual('myTag');
  });

  it('should correctly remove a tag from the command', function() {
    this.scope.command.tags.push({'value': 'myTag1'}, {'value': 'myTag2'});
    this.scope.$apply();

    var removeLinks = this.elem.find('table.tags a');
    expect(removeLinks.length).toEqual(2);

    $(removeLinks[0]).trigger('click');
    this.scope.$apply();

    expect(this.scope.command.tags.length).toEqual(1);
    expect(this.scope.command.tags[0].value).toEqual('myTag2');
  });
});
