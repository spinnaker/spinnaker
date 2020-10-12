'use strict';

import { mock } from 'angular';

import { AuthenticationService } from 'core/authentication';
import { SETTINGS } from 'core/config/settings';

describe('Directives: userMenu', function () {
  var $scope, $compile;

  require('./userMenu.directive.html');
  beforeEach(mock.module(require('./userMenu.directive').AUTHENTICATION_USER_MENU));

  beforeEach(
    mock.inject(function ($rootScope, _$compile_) {
      $scope = $rootScope.$new();
      $compile = _$compile_;
    }),
  );

  afterEach(SETTINGS.resetToOriginal);

  function createUserMenu(givenScope) {
    var domNode;

    domNode = $compile('<user-menu></user-menu>')(givenScope);
    givenScope.$digest();

    // ng-if creates a sibling if used on the root element in the directive
    // so grab the sibling with .next()
    return domNode.next();
  }

  describe('user menu rendering', function () {
    it('displays nothing when auth is not enabled', function () {
      var domNode;

      SETTINGS.authEnabled = false;
      domNode = createUserMenu($scope);

      expect(domNode.length).toBe(0);
    });

    it('displays the user menu when auth is enabled', function () {
      var domNode;

      SETTINGS.authEnabled = true;
      spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'sam mulligan' });
      domNode = createUserMenu($scope);

      expect(domNode.length).toBe(1);
    });

    it('displays the user name for both large and small screens', function () {
      var domNode;

      SETTINGS.authEnabled = true;
      spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'sam mulligan' });
      domNode = createUserMenu($scope);

      expect(domNode.find('.user-name-small').text()).toBe('sam mulligan');
      expect(domNode.find('.user-name-large').text().trim()).toBe('sam mulligan');
    });
  });
});
