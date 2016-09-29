describe('Service: securityGroupReader', function () {

  var securityGroupReader,
    $http,
    $scope,
    $q,
    API;

  beforeEach(
    window.module(
      require('./securityGroup.read.service.js'),
      require('../api/api.service')
    )
  );

  beforeEach(
    window.inject(function (_securityGroupReader_, $httpBackend, _API_,
                            $rootScope, _$q_, securityGroupTransformer, _serviceDelegate_) {
      securityGroupReader = _securityGroupReader_;
      $http = $httpBackend;
      API = _API_;
      $q = _$q_;
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

  it('attaches load balancer to security group usages', function() {
    let data = null;
    var application = {
      accounts: [ 'test' ],
      securityGroups: { data: [] },
      serverGroups: {data: [], ready: () => $q.when(null), loaded: true},
      loadBalancers: {
        data: [
          {
            name: 'my-elb',
            account: 'test',
            region: 'us-east-1',
            securityGroups: [
              'not-cached',
            ]
          }
        ],
        ready: () => $q.when(null),
        loaded: true
      }
    };
    $http.expectGET(API.baseUrl + '/securityGroups').respond(200,
    { test: { aws: { 'us-east-1': [{ name: 'not-cached' }]}}});
    securityGroupReader.getApplicationSecurityGroups(application).then(results => data = results);
    $http.flush();
    $scope.$digest();
    var group = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({name: application.loadBalancers.data[0].name});
  });

  it('adds security group names across accounts, falling back to the ID if none found', function () {
    var details = null;
    var application = {
      securityGroupsIndex: {
        test: { 'us-east-1': { 'sg-2': { name: 'matched' } } },
        prod: { 'us-east-1': { 'sg-2': { name: 'matched-prod' } } }
      },
    };

    $http.expectGET(API.baseUrl + '/securityGroups/test/us-east-1/sg-123?provider=aws&vpcId=vpc-1').respond(200, {
      inboundRules: [
        { securityGroup: { accountName: 'test', id: 'sg-345' }},
        { securityGroup: { accountName: 'test', id: 'sg-2' }},
        { securityGroup: { accountName: 'prod', id: 'sg-2' }},
      ],
      region: 'us-east-1',
    });

    securityGroupReader.getSecurityGroupDetails(application, 'test', 'aws', 'us-east-1', 'vpc-1', 'sg-123').then(
      (result) => details = result);
    $http.flush();

    expect(details.securityGroupRules.length).toBe(3);
    expect(details.securityGroupRules[0].securityGroup.name).toBe('sg-345');
    expect(details.securityGroupRules[0].securityGroup.inferredName).toBe(true);
    expect(details.securityGroupRules[1].securityGroup.name).toBe('matched');
    expect(details.securityGroupRules[1].securityGroup.inferredName).toBeFalsy();
    expect(details.securityGroupRules[2].securityGroup.name).toBe('matched-prod');
    expect(details.securityGroupRules[2].securityGroup.inferredName).toBeFalsy();
  });

  it('should clear cache, then reload security groups and try again if a security group is not found', function () {
    let data = null;
    var application = {
      accounts: [ 'test' ],
      securityGroups: { data: [] },
      serverGroups: {data: [], ready: () => $q.when(null), loaded: true},
      securityGroupsIndex: {},
      loadBalancers: {
        data: [
          {
            name: 'my-elb',
            account: 'test',
            region: 'us-east-1',
            securityGroups: [
              'not-cached',
            ]
          }
        ],
        ready: () => $q.when(null),
        loaded: true
      }
    };

    $http.expectGET(API.baseUrl + '/securityGroups').respond(200, {
      test: {
        aws: {
          'us-east-1': [
            {name: 'not-cached', id: 'not-cached-id', vpcId: null}
          ]
        }
      }
    });

    securityGroupReader.getApplicationSecurityGroups(application, []).then(results => data = results);
    $http.flush();
    var group = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({name:application.loadBalancers.data[0].name});

  });
});


