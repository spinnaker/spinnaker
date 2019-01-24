'use strict';

describe('Controller: AllSecurityGroupsCtrl', function() {
  var scope;

  beforeEach(window.module(require('./AllSecurityGroupsCtrl').name));

  beforeEach(
    window.inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      this.controller = $controller('AllSecurityGroupsCtrl', {
        $scope: scope,
        app: {
          securityGroups: { data: [], onRefresh: angular.noop },
          loadBalancers: { onRefresh: angular.noop },
          serverGroups: { onRefresh: angular.noop },
        },
        $uibModal: {},
      });
    }),
  );

  it('should initialize the controller', function() {
    expect(this.controller).toBeDefined();
  });
});
