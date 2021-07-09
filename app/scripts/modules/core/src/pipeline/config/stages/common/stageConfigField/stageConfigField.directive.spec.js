'use strict';

import { HelpContentsRegistry } from '../../../../../help';

describe('Directives: stageConfigField', function () {
  var scope, compile;

  require('./stageConfigField.directive.html');

  beforeEach(window.module(require('./stageConfigField.directive').name));

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
  beforeEach(
    window.module(($compileProvider) => {
      $compileProvider.preAssignBindingsEnabled(true);
    }),
  );

  beforeEach(
    window.inject(function ($rootScope, $compile) {
      scope = $rootScope.$new();
      compile = $compile;
    }),
  );

  it('initializes label', function () {
    var field = compile('<stage-config-field label="The Label"></stage-config-field>')(scope);

    scope.$digest();

    expect(field.find('.label-text').html()).toEqual('The Label');
  });

  it('updates label', function () {
    scope.foo = true;

    var field = compile(`<stage-config-field label="{{foo ? 'foo' : 'bar'}}"></stage-config-field>`)(scope);

    scope.$digest();
    expect(field.find('.label-text').html()).toEqual('foo');

    scope.foo = false;
    scope.$digest();
    expect(field.find('.label-text').html()).toEqual('bar');
  });

  it('transcludes content, defaulting to 8-columns', function () {
    var field = compile('<stage-config-field label="Label"><h3>The content</h3></stage-config-field>')(scope);
    scope.$digest();
    expect(field.find('.col-md-8 h3').html()).toBe('The content');
  });

  it('allows columns to be overridden for field', function () {
    var field = compile(
      '<stage-config-field label="Label" field-columns="3"><h3>The content</h3></stage-config-field>',
    )(scope);
    scope.$digest();
    expect(field.find('.col-md-3 h3').html()).toBe('The content');
  });
});
