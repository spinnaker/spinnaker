'use strict';

import * as angular from 'angular';
require('./advancedSettings.directive.html');

describe('Directive: GCE Group Advanced Settings Selector', function () {
  beforeEach(
    window.module(
      require('./advancedSettingsSelector.directive').name,
      require('../securityGroups/tagManager.service').name,
    ),
  );

  beforeEach(
    window.inject(function ($rootScope, $compile, gceTagManager) {
      ['showToolTip', 'updateSelectedTags', 'getToolTipContent', 'inferSelectedSecurityGroupFromTag'].forEach((prop) =>
        spyOn(gceTagManager, prop),
      );
      this.gceTagManager = gceTagManager;
      this.scope = $rootScope.$new();
      this.scope.command = { instanceMetadata: [], tags: [], labels: [], authScopes: [] };
      this.elem = angular.element(
        '<gce-server-group-advanced-settings-selector command="command"></gce-server-group-advanced-settings-selector>',
      );
      this.element = $compile(this.elem)(this.scope);
      this.scope.$digest();
    }),
  );

  it('should correctly add tags to the command', function () {
    expect(this.scope.command.tags.length).toEqual(0);

    this.elem.find('table.tags button').trigger('click');
    this.scope.$apply();
    expect(this.scope.command.tags.length).toEqual(1);

    this.elem.find('table.tags input').val('myTag').trigger('input');
    this.scope.$apply();

    expect(this.scope.command.tags.length).toEqual(1);
    expect(this.scope.command.tags[0].value).toEqual('myTag');
  });

  it('should correctly remove a tag from the command', function () {
    this.scope.command.tags.push({ value: 'myTag1' }, { value: 'myTag2' });
    this.scope.$apply();

    const removeLinks = this.elem.find('table.tags a');
    expect(removeLinks.length).toEqual(2);

    $(removeLinks[0]).trigger('click');
    this.scope.$apply();

    expect(this.scope.command.tags.length).toEqual(1);
    expect(this.scope.command.tags[0].value).toEqual('myTag2');
    expect(this.gceTagManager.updateSelectedTags).toHaveBeenCalled();
  });
});
