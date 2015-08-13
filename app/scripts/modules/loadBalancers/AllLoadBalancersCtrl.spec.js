'use strict';


describe('Controller: AllLoadBalancersCtrl', function () {

  const angular = require('angular');

  var controller;
  var scope;
  var autoRefresh;

  beforeEach(
    window.module(
      require('./AllLoadBalancersCtrl.js')
    )
  );

  beforeEach(
    window.inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('AllLoadBalancersCtrl', {
        $scope: scope,
        app: {
          registerAutoRefreshHandler: function(handler) { autoRefresh = handler; },
          loadBalancers: [],
        }
      });
    })
  );

  it('should add search fields to each load balancer', function () {
    scope.application.loadBalancers = [
      { name: 'elb-1', region: 'us-east-1', account: 'prod',
        serverGroups: [ { name: 'asg-1' }, { name: 'asg-2' } ],
        instances: [ { id: 'i-1234' }, { id: 'i-2345' }]
      }
    ];

    autoRefresh();

    expect(scope.application.loadBalancers[0].searchField).toBe('elb-1 us-east-1 prod asg-1 asg-2 i-1234 i-2345');

  });
});
