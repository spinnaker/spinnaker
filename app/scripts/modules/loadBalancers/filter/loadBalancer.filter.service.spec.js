'use strict';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: loadBalancerFilterService', function () {


  var service;
  var $location;
  var LoadBalancerFilterModel;
  var _;
  var app;
  var resultJson;
  var $timeout;

  beforeEach(
    window.module(
      require('../../utils/lodash.js'),
      require('./loadBalancer.filter.service.js'),
      require('./loadBalancer.filter.model.js')
    )
  );

  beforeEach(
    window.inject(
      function (_$location_, loadBalancerFilterService, _LoadBalancerFilterModel_, ___, _$timeout_) {
        _ = ___;
        service = loadBalancerFilterService;
        $location = _$location_;
        LoadBalancerFilterModel = _LoadBalancerFilterModel_;
        $timeout = _$timeout_;
        LoadBalancerFilterModel.groups = [];
      }
    )
  );

  beforeEach(function () {
    app = {
      loadBalancers: [
        { name: 'elb-1', region: 'us-east-1', account: 'test', vpcName: '', serverGroups: [], downCount: 0, startingCount: 0, outOfServiceCount: 0 },
        { name: 'elb-1', region: 'us-west-1', account: 'test', vpcName: 'main', serverGroups: [], downCount: 0, startingCount: 0, outOfServiceCount: 0 },
        { name: 'elb-2', region: 'us-east-1', account: 'prod', vpcName: '', serverGroups: [], downCount: 0, startingCount: 0, outOfServiceCount: 0 },
      ]
    };
    resultJson = [
      { heading: 'us-east-1', loadBalancer: app.loadBalancers[0], serverGroups: [] },
      { heading: 'us-west-1', loadBalancer: app.loadBalancers[1], serverGroups: [] },
      { heading: 'us-east-1', loadBalancer: app.loadBalancers[2], serverGroups: [] }
    ];
    LoadBalancerFilterModel.clearFilters();
  });

  describe('Updating the load balancer group', function () {

    it('no filter: should be transformed', function () {
      var expected = [
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]},
      ];
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual(expected);
    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', function () {
        LoadBalancerFilterModel.sortFilter.filter = 'vpc:main';
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.groups).toEqual([
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[1] ]}
          ]}
        ]);
      });

      it('should not match on partial vpc name', function () {
        LoadBalancerFilterModel.sortFilter.filter = 'vpc:main-old';
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.groups).toEqual([]);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function () {
        LoadBalancerFilterModel.sortFilter.account = {prod: true};
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]}
        ]);
      });

      it('All account filters: should show all accounts', function () {
        LoadBalancerFilterModel.sortFilter.account = {prod: true, test: true};
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
          ]},
        ]);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region', function () {
      LoadBalancerFilterModel.sortFilter.region = {'us-east-1' : true};

      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]}
      ]);
    });

    it('All regions: should show all load balancers', function () {
      LoadBalancerFilterModel.sortFilter.region = {'us-east-1' : true, 'us-west-1': true};

      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });
  });
  describe('filter by healthy state', function () {
    it('should filter any load balancers with down instances (based on downCount) if "Up" checked', function () {
      LoadBalancerFilterModel.sortFilter.status = {'Up' : true };
      app.loadBalancers[0].downCount = 1;
      app.loadBalancers.forEach(function (loadBalancer) {
        loadBalancer.instances = [ { healthState: 'Up' } ];
      });
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[1] ]}
        ]}
      ]);
    });

    it('should filter any load balancers without down instances (based on downCount) if "Down" checked', function () {
      LoadBalancerFilterModel.sortFilter.status = {'Down' : true };
      app.loadBalancers[0].downCount = 1;
      app.loadBalancers.forEach(function (loadBalancer) {
        loadBalancer.instances = [ { healthState: 'Down' } ];
      });
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]},
      ]);
    });

    it('should filter any load balancers with starting instances (based on startingCount) if "Starting" checked', function () {
      LoadBalancerFilterModel.sortFilter.status = {'Starting' : true };
      app.loadBalancers[0].startingCount = 1;
      app.loadBalancers.forEach(function (loadBalancer) {
        loadBalancer.instances = [ { healthState: 'Starting' } ];
      });
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]},
      ]);
    });

  });

  describe('filtered by provider type', function () {
    beforeEach(function() {
      app.loadBalancers[0].type = 'aws';
      app.loadBalancers[1].type = 'gce';
      app.loadBalancers[2].type = 'aws';
    });
    it('should filter by aws if checked', function () {
      LoadBalancerFilterModel.sortFilter.providerType = {aws : true};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]}
      ]);
    });

    it('should not filter if no provider type is selected', function () {
      LoadBalancerFilterModel.sortFilter.providerType = undefined;
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });

    it('should not filter if all provider are selected', function () {
      LoadBalancerFilterModel.sortFilter.providerType = {aws: true, gce: true};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });
  });

  describe('group diffing', function() {
    beforeEach(function() {
      app.loadBalancers[0].stringVal = 'original';
      app.loadBalancers[1].stringVal = 'should be deleted';
      LoadBalancerFilterModel.groups = [
        { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [resultJson[2]] }
        ]},
        { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] }
        ]}
      ];
    });

    it('adds a group when new one provided', function() {
      app.loadBalancers.push({
        name: 'elb-1', account: 'management', region: 'us-east-1', serverGroups: [], vpcName: '',
      });
      var newGroup = { heading: 'management', subgroups: [
        { heading: 'elb-1', subgroups: [
          { heading: 'us-east-1', loadBalancer: app.loadBalancers[3], serverGroups: [] }
        ]}
      ]};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        newGroup,
        { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [resultJson[2]] }
        ]},
        { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] },
        ]}
      ]);
    });

    it('adds a subgroup when new one provided', function() {
      app.loadBalancers.push({
        name: 'elb-3', account: 'prod', region: 'eu-west-1', serverGroups: [], vpcName: '',
      });
      var newSubGroup = { heading: 'elb-3', subgroups: [{heading: 'eu-west-1', loadBalancer: app.loadBalancers[3], serverGroups: [] }]};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [resultJson[2]] },
            newSubGroup
        ]},
        { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] },
        ]}
      ]);
    });

    it('adds a sub-subgroup when new one provided', function() {
      app.loadBalancers.push({
        name: 'elb-2', account: 'test', region: 'eu-west-1', serverGroups: [], vpcName: '',
      });
      var newSubsubGroup = { heading: 'eu-west-1', loadBalancer: app.loadBalancers[3], serverGroups: [] };
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.groups).toEqual([
        {
          heading: 'prod', subgroups: [
          {heading: 'elb-2', subgroups: [resultJson[2]] }
        ]
        },
        {
          heading: 'test', subgroups: [
          {heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] },
          {heading: 'elb-2', subgroups: [newSubsubGroup] },
        ]
        }
      ]);
    });

  });
});
