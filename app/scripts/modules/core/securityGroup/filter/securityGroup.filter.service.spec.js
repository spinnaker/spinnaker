'use strict';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: securityGroupFilterService', function () {


  var service;
  var $location;
  var SecurityGroupFilterModel;
  var _;
  var app;
  var resultJson;
  var $timeout;

  beforeEach(
    window.module(
      require('../../utils/lodash.js'),
      require('./securityGroup.filter.service.js'),
      require('./securityGroup.filter.model.js')
    )
  );

  beforeEach(
    window.inject(
      function (_$location_, securityGroupFilterService, _SecurityGroupFilterModel_, ___, _$timeout_) {
        _ = ___;
        service = securityGroupFilterService;
        $location = _$location_;
        SecurityGroupFilterModel = _SecurityGroupFilterModel_;
        $timeout = _$timeout_;
      }
    )
  );

  beforeEach(function () {
    app = {
      securityGroups: [
        { name: 'sg-1', region: 'us-east-1', account: 'test', vpcName: '' },
        { name: 'sg-1', region: 'us-west-1', account: 'test', vpcName: 'main' },
        { name: 'sg-2', region: 'us-east-1', account: 'prod', vpcName: '' },
      ]
    };
    resultJson = [
      { heading: 'us-east-1', vpcName: '', securityGroup: app.securityGroups[0],  },
      { heading: 'us-west-1', vpcName: 'main', securityGroup: app.securityGroups[1],  },
      { heading: 'us-east-1', vpcName: '', securityGroup: app.securityGroups[2],  }
    ];
    SecurityGroupFilterModel.clearFilters();
  });

  describe('Updating the security group group', function () {

    it('no filter: should be transformed', function () {
      var expected = [
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ];
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual(expected);
    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', function () {
        SecurityGroupFilterModel.sortFilter.filter = 'vpc:main';
        service.updateSecurityGroups(app);
        $timeout.flush();
        expect(SecurityGroupFilterModel.groups).toEqual([
          { heading: 'test', subgroups: [
            { heading: 'sg-1', subgroups: [ resultJson[1] ]}
          ]}
        ]);
      });

      it('should not match on partial vpc name', function () {
        SecurityGroupFilterModel.sortFilter.filter = 'vpc:main-old';
        service.updateSecurityGroups(app);
        $timeout.flush();
        expect(SecurityGroupFilterModel.groups).toEqual([]);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function () {
        SecurityGroupFilterModel.sortFilter.account = {prod: true};
        service.updateSecurityGroups(app);
        $timeout.flush();
        expect(SecurityGroupFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'sg-2', subgroups: [ resultJson[2] ]}
          ]}
        ]);
      });

      it('All account filters: should show all accounts', function () {
        SecurityGroupFilterModel.sortFilter.account = {prod: true, test: true};
        service.updateSecurityGroups(app);
        $timeout.flush();
        expect(SecurityGroupFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'sg-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'sg-1', subgroups: [ resultJson[0], resultJson[1] ]}
          ]}
        ]);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region', function () {
      SecurityGroupFilterModel.sortFilter.region = {'us-east-1' : true};

      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [ resultJson[0] ]}
        ]}
      ]);
    });

    it('All regions: should show all load balancers', function () {
      SecurityGroupFilterModel.sortFilter.region = {'us-east-1' : true, 'us-west-1': true};

      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });
  });

  describe('filtered by provider type', function () {
    beforeEach(function() {
      app.securityGroups[0].provider = 'aws';
      app.securityGroups[1].provider = 'gce';
      app.securityGroups[2].provider = 'aws';
    });
    it('should filter by aws if checked', function () {
      SecurityGroupFilterModel.sortFilter.providerType = {aws : true};
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [ resultJson[0] ]}
        ]}
      ]);
    });

    it('should not filter if no provider type is selected', function () {
      SecurityGroupFilterModel.sortFilter.providerType = undefined;
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });

    it('should not filter if all provider are selected', function () {
      SecurityGroupFilterModel.sortFilter.providerType = {aws: true, gce: true};
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });
  });

  describe('group diffing', function() {
    beforeEach(function() {
      app.securityGroups[0].stringVal = 'original';
      app.securityGroups[1].stringVal = 'should be deleted';
      SecurityGroupFilterModel.groups = [
        { heading: 'prod', subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]] }
        ]},
        { heading: 'test', subgroups: [
            { heading: 'sg-1', subgroups: [resultJson[0], resultJson[1]] }
        ]}
      ];
    });

    it('adds a group when new one provided', function() {
      app.securityGroups.push({
        name: 'sg-1', account: 'management', region: 'us-east-1',  vpcName: '',
      });
      var newGroup = { heading: 'management', subgroups: [
        { heading: 'sg-1', subgroups: [
          { heading: 'us-east-1', vpcName: '', securityGroup: app.securityGroups[3],  }
        ]}
      ]};
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        newGroup,
        { heading: 'prod', subgroups: [
          { heading: 'sg-2', subgroups: [resultJson[2]] }
        ]},
        { heading: 'test', subgroups: [
          { heading: 'sg-1', subgroups: [resultJson[0], resultJson[1]] },
        ]}
      ]);
    });

    it('adds a subgroup when new one provided', function() {
      app.securityGroups.push({
        name: 'sg-3', account: 'prod', region: 'eu-west-1',  vpcName: '',
      });
      var newSubGroup = { heading: 'sg-3', subgroups: [{heading: 'eu-west-1', vpcName: '', securityGroup: app.securityGroups[3],  }]};
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]] },
            newSubGroup
        ]},
        { heading: 'test', subgroups: [
            { heading: 'sg-1', subgroups: [resultJson[0], resultJson[1]] },
        ]}
      ]);
    });

    it('adds a sub-subgroup when new one provided', function() {
      app.securityGroups.push({
        name: 'sg-2', account: 'test', region: 'eu-west-1',  vpcName: '',
      });
      var newSubsubGroup = { heading: 'eu-west-1', vpcName: '', securityGroup: app.securityGroups[3],  };
      service.updateSecurityGroups(app);
      $timeout.flush();
      expect(SecurityGroupFilterModel.groups).toEqual([
        {
          heading: 'prod', subgroups: [
          {heading: 'sg-2', subgroups: [resultJson[2]] }
        ]
        },
        {
          heading: 'test', subgroups: [
          {heading: 'sg-1', subgroups: [resultJson[0], resultJson[1]] },
          {heading: 'sg-2', subgroups: [newSubsubGroup] },
        ]
        }
      ]);
    });

  });
});
