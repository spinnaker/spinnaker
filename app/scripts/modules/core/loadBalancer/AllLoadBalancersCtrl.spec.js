'use strict';


describe('Controller: AllLoadBalancersCtrl', function () {

  var scope;

  beforeEach(
    window.module(
      require('./AllLoadBalancersCtrl.js')
    )
  );

  beforeEach(
    window.inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      this.controller = $controller('AllLoadBalancersCtrl', {
        $scope: scope,
        app: {
          loadBalancers: { data: [], onRefresh: angular.noop },
        }
      });
    })
  );

  it('should initialize the controller', function () {
    expect(this.controller).toBeDefined();
  });
});
