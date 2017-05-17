import {mock, element} from 'angular';

import {HELP_FIELD_COMPONENT} from './helpField.component';
import {HelpContentsRegistry} from './helpContents.registry';
import IProvideService = angular.auto.IProvideService;

describe('Component: helpField', () => {

  let helpContentsRegistry: HelpContentsRegistry,
      $scope: ng.IScope,
      $compile: ng.ICompileService;

  const executeTest = (htmlString: string, expected: string, attr = 'uib-popover-html') => {
    const helpField: JQuery = $compile(htmlString)($scope);
    $scope.$digest();
    expect(helpField.find('a').attr(attr)).toBe(expected);
  };

  const testContent = (htmlString: string, expected: string) => {
    const helpField: JQuery = $compile(htmlString)($scope);
    $scope.$digest();
    expect(element(helpField.find('a')).scope()['$ctrl']['contents']['content']).toBe(expected);
  };

  beforeEach(() => {
    mock.module(
      HELP_FIELD_COMPONENT,
      ($provide: IProvideService) => {
        $provide.constant('helpContents', {'aws.serverGroup.stack': 'expected stack help'});
      });
  });

  beforeEach(mock.inject(($rootScope: ng.IRootScopeService,
                          _$compile_: ng.ICompileService,
                          _helpContentsRegistry_: HelpContentsRegistry) => {
    helpContentsRegistry = _helpContentsRegistry_;
    $compile = _$compile_;
    $scope = $rootScope.$new();
  }));

  it('uses provided content if supplied', () => {
    testContent('<help-field content="some content"></help-field>', 'some content');
  });

  it('uses key to look up content if supplied', () => {
    testContent('<help-field key="aws.serverGroup.stack"></help-field>', 'expected stack help');
  });

  it('prefers overrides', () => {
    spyOn(helpContentsRegistry, 'getHelpField').and.returnValue('override content');
    testContent('<help-field key="aws.serverGroup.stack"></help-field>', 'override content');
  });

  it('uses fallback if key not present', () => {
    testContent('<help-field key="nonexistent.key" fallback="the fallback"></help-field>', 'the fallback');
  });

  it('ignores key if content is defined', () => {
    testContent('<help-field key="aws.serverGroup.stack" content="overridden!"></help-field>', 'overridden!');
  });

  it('ignores key and fallback if content is defined', () => {
    testContent('<help-field key="aws.serverGroup.stack" fallback="will be ignored" content="overridden!"></help-field>', 'overridden!');
  });

  it('defaults position to "auto"', () => {
    executeTest('<help-field content="overridden!"></help-field>', 'auto', 'popover-placement');
  });

  it('overrides position to "left"', () => {
    executeTest('<help-field content="some content" placement="left"></help-field>', 'left', 'popover-placement');
  });

});
