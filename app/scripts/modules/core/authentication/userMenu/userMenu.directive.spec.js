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

  function createUserMenu(givenScope) {
    var domNode;

    domNode = $compile('<user-menu></user-menu>')(givenScope);
    givenScope.$digest();

    // ng-if creates a sibling if used on the root element in the directive
    // so grab the sibling with .next()
    return domNode.next();
  }

  describe('user menu rendering', function() {
    it('displays nothing when auth is not enabled', function () {
      var domNode;

      settings.authEnabled = false;
      domNode = createUserMenu($scope);

      expect(domNode.size()).toBe(0);
    });

    it('displays the user menu when auth is enabled', function () {
      var domNode, templateElement;

      settings.authEnabled = true;
      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({'name': 'sam mulligan'});
      domNode = createUserMenu($scope);

      expect(domNode.size()).toBe(1);
    });

    it('displays the user name for both large and small screens', function () {
      var domNode, templateElement;

      settings.authEnabled = true;
      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({'name': 'sam mulligan'});
      domNode = createUserMenu($scope);

      expect(domNode.find('.user-name-small').text()).toBe('sam mulligan');
      expect(domNode.find('.user-name-large').text()).toBe('sam mulligan');
    });
  });
});
