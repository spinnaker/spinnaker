'use strict';


describe('Controller: AllSecurityGroupsCtrl', function () {

  const angular = require('angular');

  var controller;
  var scope;
  var autoRefresh;

  beforeEach(
    window.module(
      require('./AllSecurityGroupsCtrl.js')
    )
  );

  beforeEach(
    window.inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('AllSecurityGroupsCtrl', {
        $scope: scope,
        app: {
          registerAutoRefreshHandler: function(handler) { autoRefresh = handler; },
          securityGroups: [],
        },
        $uibModal: {},
      });
    })
  );

  it('should add search fields to each security group', function () {
    scope.application.securityGroups = [
      { name: 'deck-security', region: 'us-east-1', account: 'prod', accountName: 'prod', id: 'sg-1',
        usages: {
          serverGroups: [ { name: 'asg-1' }, { name: 'asg-2' } ],
          loadBalancers: [ { name: 'elb-1'} ]
        },
      }
    ];

    autoRefresh();

    expect(scope.application.securityGroups[0].searchField).toBe('deck-security sg-1 prod us-east-1 asg-1 asg-2 elb-1');

  });
});
