'use strict';

describe('Service: securityGroupReader', function () {

  var securityGroupReader,
    $http,
    $scope;

  beforeEach(
    window.module(
      require('./securityGroup.read.service.js')
    )
  );

  beforeEach(
    window.inject(function (_securityGroupReader_, $httpBackend,
                            $rootScope, $q, securityGroupTransformer, _serviceDelegate_) {
      securityGroupReader = _securityGroupReader_;
      $http = $httpBackend;
      $scope = $rootScope.$new();
      spyOn(securityGroupTransformer, 'normalizeSecurityGroup').and.callFake((securityGroup) => {
        return $q.when(securityGroup);
      });
      spyOn(_serviceDelegate_, 'getDelegate').and.returnValue(
        {
          resolveIndexedSecurityGroup: (idx, container, id) => {
            return idx[container.account][container.region][id];
          }
        }
      );
    })
  );

  it('does nothing when index not in place', function () {
    var application = {
      accounts: [ 'test' ],
      securityGroups: { data: [] },
      serverGroups: {data: []},
      loadBalancers: {data: [
        {
          name: 'my-elb',
          account: 'test',
          region: 'us-east-1',
          securityGroups: [
            'not-cached',
          ]
        }
      ]}
    };

    securityGroupReader.attachSecurityGroups(application);
    $scope.$digest();
    expect(application.securityGroups.data.length).toBe(0);
  });

  it('attaches load balancer to security group usages', function() {
    var application = {
      accounts: [ 'test' ],
      securityGroupsIndex: { test: { 'us-east-1': { 'not-cached': { name: 'not-cached' }}}},
      securityGroups: { data: [] },
      serverGroups: {data: []},
      loadBalancers: {data: [
        {
          name: 'my-elb',
          account: 'test',
          region: 'us-east-1',
          securityGroups: [
            'not-cached',
          ]
        }
      ]}
    };

    securityGroupReader.attachSecurityGroups(application);
    $scope.$digest();
    var group = application.securityGroups.data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({name: application.loadBalancers.data[0].name});
  });

  it('should clear cache, then reload security groups and try again if a security group is not found', function () {
    var application = {
      accounts: [ 'test' ],
      securityGroups: { data: [] },
      serverGroups: {data: []},
      securityGroupsIndex: {},
      loadBalancers: {data: [
        {
          name: 'my-elb',
          account: 'test',
          region: 'us-east-1',
          securityGroups: [
            'not-cached',
          ]
        }
      ]}
    };

    $http.expectGET('/securityGroups').respond(200, {
      test: {
        aws: {
          'us-east-1': [
            {name: 'not-cached', id: 'not-cached-id', vpcId: null}
          ]
        }
      }
    });

    securityGroupReader.attachSecurityGroups(application, [], true);
    $http.flush();
    var group = application.securityGroups.data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({name:application.loadBalancers.data[0].name});

  });
});


