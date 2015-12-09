'use strict';

describe('Directives: userMenu', function () {
  var $scope, $compile, settings, authenticationService;

  require('./userMenu.directive.html');
  beforeEach(window.module(
    require('./userMenu.directive.js')
  ));

  beforeEach(
    window.inject(function ($rootScope, _$compile_, _settings_, _authenticationService_) {
      $scope = $rootScope.$new();
      $compile = _$compile_;
      settings = _settings_;
      authenticationService = _authenticationService_;
    })
  );

  describe('user menu rendering', function() {
    it('displays nothing when auth is not enabled', function () {
      var domNode;

      settings.authEnabled = false;
      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({'name': 'sam mulligan'});

      domNode = $compile('<user-menu></user-menu>')($scope);
      $scope.$digest();

      expect(domNode.hasClass('ng-hide')).toBe(true);
    });

    it('displays the user menu when auth is enabled', function () {
      var domNode;

      settings.authEnabled = true;
      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({'name': 'sam mulligan'});

      domNode = $compile('<user-menu></user-menu>')($scope);
      $scope.$digest();

      expect(domNode.hasClass('ng-hide')).toBe(false);
      expect(domNode.find('.user-name').text()).toBe('sam mulligan');
    });
  });
});
