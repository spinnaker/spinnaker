'use strict';

describe('Directives: helpField', function () {

  require('./helpField.directive.html');

  var helpContentsRegistry;

  beforeEach(function() {
    window.module(
      require('./helpField.directive.js'),
      function($provide) {
        $provide.constant('helpContents', {'aws.serverGroup.stack': 'expected stack help'});
      });
  });

  beforeEach(window.inject(function ($rootScope, $compile, _helpContentsRegistry_) {
    helpContentsRegistry = _helpContentsRegistry_;
    this.executeTest = function executeTest(htmlString, expected, attr = 'uib-popover-html') {
      var $scope = $rootScope.$new();
      var helpField = $compile(htmlString)($scope);
      $scope.$digest();
      expect(helpField.find('a').attr(attr)).toBe(expected);
    };
    this.testContent = function(htmlString, expected) {
      var $scope = $rootScope.$new();
      var helpField = $compile(htmlString)($scope);
      $scope.$digest();
      expect(angular.element(helpField.find('a')).scope().contents.content).toBe(expected);
    };
  }));

  it('uses provided content if supplied', function() {
    this.testContent('<help-field content="some content"></help-field>', 'some content');
  });

  it('uses key to look up content if supplied', function() {
    this.testContent('<help-field key="aws.serverGroup.stack"></help-field>', 'expected stack help');
  });

  it('prefers overrides', function() {
    spyOn(helpContentsRegistry, 'getHelpField').and.returnValue('override content');
    this.testContent('<help-field key="aws.serverGroup.stack"></help-field>', 'override content');
  });


  it('uses fallback if key not present', function() {
    this.testContent('<help-field key="nonexistent.key" fallback="the fallback"></help-field>', 'the fallback');
  });

  it('ignores key if content is defined', function() {
    this.testContent('<help-field key="aws.serverGroup.stack" content="overridden!"></help-field>', 'overridden!');
  });

  it('ignores key and fallback if content is defined', function() {
    this.testContent('<help-field key="aws.serverGroup.stack" fallback="will be ignored" content="overridden!"></help-field>', 'overridden!');
  });

  it('defaults position to "top"', function() {
    this.executeTest('<help-field content="overridden!"></help-field>', 'top', 'popover-placement');
  });

  it('overrides position to "left"', function() {
    this.executeTest('<help-field content="some content" placement="left"></help-field>', 'left', 'popover-placement');
  });

});
