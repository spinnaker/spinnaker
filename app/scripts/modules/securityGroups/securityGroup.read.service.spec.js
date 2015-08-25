'use strict';

describe('Service: securityGroupReader', function () {

  var securityGroupReader,
    infrastructureCaches,
    $http,
    settings,
    $exceptionHandler,
    $scope;

  beforeEach(
    window.module(
      require('./securityGroup.read.service.js')
    )
  );

  beforeEach(
    window.inject(function (_securityGroupReader_, _infrastructureCaches_, $httpBackend, _settings_,
                            _$exceptionHandler_, $rootScope, vpcReader, $q) {
      securityGroupReader = _securityGroupReader_;
      infrastructureCaches = _infrastructureCaches_;
      $http = $httpBackend;
      settings = _settings_;
      $exceptionHandler = _$exceptionHandler_;
      $scope = $rootScope.$new();
      spyOn(vpcReader, 'listVpcs').and.returnValue($q.when([ { id: 'vpc-1', name: 'main' }]));
    })
  );

  it('attaches account, vpcName fields', function () {
    var application = {
      serverGroups: [],
      loadBalancers: []
    };

    var securityGroups = [
      { account: 'test',
        securityGroups: {
          'us-east-1': [
            { name: 'in-vpc', id: 'sg-1', vpcId: 'vpc-1' },
            { name: 'not-in-vpc', id: 'sg-2', vpcId: null },
          ]
        }
      }
    ];

    var namedBasedGroups = [
      { account: 'test', region: 'us-east-1', vpcId: 'vpc-1', id: 'sg-1'},
      { account: 'test', region: 'us-east-1', vpcId: null, id: 'sg-2'}
    ];

    securityGroupReader.attachSecurityGroups(application, securityGroups, namedBasedGroups, false);
    $scope.$digest();
    expect(application.securityGroups[0].vpcName).toBe('main');
    expect(application.securityGroups[0].account).toBe('test');
    expect(application.securityGroups[1].vpcName).toBe('');
    expect(application.securityGroups[1].account).toBe('test');
  });

  it('attaches load balancer to security group usages', function() {
    var application = {
      accounts: [ 'test' ],
      serverGroups: [],
      loadBalancers: [
        {
          name: 'my-elb',
          account: 'test',
          region: 'us-east-1',
          securityGroups: [
            'not-cached',
          ]
        }
      ]
    };

    var securityGroups = [
      { account: 'test',
        securityGroups: {
          'us-east-1': [
            { name: 'not-cached', id: 'not-cached-id', vpcId: null }
          ]
        }
      }
    ];

    securityGroupReader.attachSecurityGroups(application, securityGroups, [], true);
    $scope.$digest();
    var group = application.securityGroups[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toBe(application.loadBalancers[0]);
  });

  it('should clear cache, then reload security groups and try again if a security group is not found', function () {
    var application = {
      accounts: [ 'test' ],
      serverGroups: [],
      loadBalancers: [
        {
          name: 'my-elb',
          account: 'test',
          region: 'us-east-1',
          securityGroups: [
            'not-cached',
          ]
        }
      ]
    };

    $http.expectGET('/credentials/test').respond(200, {
      provider: 'aws',
      regions: [
        {
          name: 'us-east-1',
          availabilityZones: [
            'us-east-1a',
          ]
        },
      ]
    });

    $http.expectGET('/securityGroups/test?provider=aws').respond(200, {
      'us-east-1': [
        { name: 'not-cached', id: 'not-cached-id', vpcId: null }
      ]
    });

    securityGroupReader.attachSecurityGroups(application, [], [], true);
    $http.flush();
    var group = application.securityGroups[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toBe(application.loadBalancers[0]);

  });
});


