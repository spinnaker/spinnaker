import * as _ from 'lodash';
import { mock, ITimeoutService } from 'angular';

import { Application } from 'core/application/application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { ILoadBalancer, ServerGroup, ILoadBalancerGroup } from 'core/domain';
import { LOAD_BALANCER_FILTER_SERVICE, LoadBalancerFilterService } from './loadBalancer.filter.service';
import { LoadBalancerFilterModel } from './loadBalancerFilter.model';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: loadBalancerFilterService', function () {

  let service: LoadBalancerFilterService,
      LoadBalancerFilterModel: LoadBalancerFilterModel,
      app: Application,
      resultJson: any,
      $timeout: ITimeoutService,
      modelBuilder: ApplicationModelBuilder;

  beforeEach(() => {
    spyOn(_, 'debounce').and.callFake((fn: any) => (a: any) => $timeout(fn(a)));
    mock.module(
      APPLICATION_MODEL_BUILDER,
      LOAD_BALANCER_FILTER_SERVICE
    );
    mock.inject(
      function (applicationModelBuilder: ApplicationModelBuilder, loadBalancerFilterService: LoadBalancerFilterService, _LoadBalancerFilterModel_: LoadBalancerFilterModel, _$timeout_: ITimeoutService) {
        modelBuilder = applicationModelBuilder;
        service = loadBalancerFilterService;
        LoadBalancerFilterModel = _LoadBalancerFilterModel_;
        $timeout = _$timeout_;
        LoadBalancerFilterModel.asFilterModel.groups = [];
      }
    );
  });

  beforeEach(function () {
    app = modelBuilder.createApplication({key: 'loadBalancers', lazy: true});
    app.getDataSource('loadBalancers').data = [
      { name: 'elb-1', region: 'us-east-1', account: 'test', vpcName: '', serverGroups: [],
        instanceCounts: {down: 0, starting: 0, outOfService: 0 }, usages: {}},
      { name: 'elb-1', region: 'us-west-1', account: 'test', vpcName: 'main', serverGroups: [],
        instanceCounts: {down: 0, starting: 0, outOfService: 0 }, usages: {}},
      { name: 'elb-2', region: 'us-east-1', account: 'prod', vpcName: '', serverGroups: [],
        instanceCounts: {down: 0, starting: 0, outOfService: 0 }, usages: {}},
    ];

    resultJson = [
      { heading: 'us-east-1', loadBalancer: app.loadBalancers.data[0], serverGroups: [] },
      { heading: 'us-west-1', loadBalancer: app.loadBalancers.data[1], serverGroups: [] },
      { heading: 'us-east-1', loadBalancer: app.loadBalancers.data[2], serverGroups: [] }
    ];
    LoadBalancerFilterModel.asFilterModel.clearFilters();
  });

  describe('Updating the load balancer group', function () {

    it('no filter: should be transformed', function () {
      const expected = [
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]},
      ];
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual(expected);
    });

    describe('filter by search', function () {
      it('should add searchField when filter is not prefixed with vpc:', function () {
        expect(app.loadBalancers.data.length).toBe(3);
        app.loadBalancers.data.forEach((group: ILoadBalancerGroup) => {
          expect(group.searchField).toBeUndefined();
        });
        LoadBalancerFilterModel.asFilterModel.sortFilter.filter = 'main';
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        app.loadBalancers.data.forEach((group: ILoadBalancerGroup) => {
          expect(group.searchField).not.toBeUndefined();
        });
      });
    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', function () {
        LoadBalancerFilterModel.asFilterModel.sortFilter.filter = 'vpc:main';
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[1] ]}
          ]}
        ]);
      });

      it('should not match on partial vpc name', function () {
        LoadBalancerFilterModel.asFilterModel.sortFilter.filter = 'vpc:main-old';
        service.updateLoadBalancerGroups(app);
        expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([]);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function () {
        LoadBalancerFilterModel.asFilterModel.sortFilter.account = {prod: true};
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]}
        ]);
      });

      it('All account filters: should show all accounts', function () {
        LoadBalancerFilterModel.asFilterModel.sortFilter.account = {prod: true, test: true};
        service.updateLoadBalancerGroups(app);
        $timeout.flush();
        expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
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
      LoadBalancerFilterModel.asFilterModel.sortFilter.region = {'us-east-1' : true};

      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]}
      ]);
    });

    it('All regions: should show all load balancers', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.region = {'us-east-1' : true, 'us-west-1': true};

      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
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
    it('should filter any load balancers with down instances (based on down) if "Up" checked', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.status = {'Up' : true };
      app.loadBalancers.data[0].instanceCounts.down = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [ { id: 'foo', healthState: 'Up', health: [], launchTime: 0, zone: 'us-east-1a' } ];
      });
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[1] ]}
        ]}
      ]);
    });

    it('should filter any load balancers without down instances (based on down) if "Down" checked', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.status = {'Down' : true };
      app.loadBalancers.data[0].instanceCounts.down = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [ { id: 'foo', healthState: 'Down', health: [], launchTime: 0, zone: 'us-east-1a' } ];
      });
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]},
      ]);
    });

    it('should filter any load balancers with starting instances (based on starting) if "Starting" checked', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.status = {'Starting' : true };
      app.loadBalancers.data[0].instanceCounts.starting = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [ { id: 'foo', healthState: 'Starting', health: [], launchTime: 0, zone: 'us-east-1a' } ];
      });
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]},
      ]);
    });

  });

  describe('filtered by provider type', function () {
    beforeEach(function() {
      app.loadBalancers.data[0].type = 'aws';
      app.loadBalancers.data[1].type = 'gce';
      app.loadBalancers.data[2].type = 'aws';
    });
    it('should filter by aws if checked', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.providerType = {aws : true};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0] ]}
        ]}
      ]);
    });

    it('should not filter if no provider type is selected', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.providerType = undefined;
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]}
      ]);
    });

    it('should not filter if all provider are selected', function () {
      LoadBalancerFilterModel.asFilterModel.sortFilter.providerType = {aws: true, gce: true};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
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
      app.loadBalancers.data[0].stringVal = 'original';
      app.loadBalancers.data[1].stringVal = 'should be deleted';
      LoadBalancerFilterModel.asFilterModel.groups = [
        { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [resultJson[2]] }
        ]},
        { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] }
        ]}
      ];
    });

    it('adds a group when new one provided', function() {
      app.loadBalancers.data.push({
        name: 'elb-1', account: 'management', region: 'us-east-1', serverGroups: [], vpcName: '',
      });
      const newGroup = { heading: 'management', subgroups: [
        { heading: 'elb-1', subgroups: [
          { heading: 'us-east-1', loadBalancer: app.loadBalancers.data[3], serverGroups: [] as ServerGroup[] }
        ]}
      ]};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
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
      app.loadBalancers.data.push({
        name: 'elb-3', account: 'prod', region: 'eu-west-1', serverGroups: [], vpcName: '',
      });
      const newSubGroup = { heading: 'elb-3', subgroups: [{heading: 'eu-west-1', loadBalancer: app.loadBalancers.data[3], serverGroups: [] as ServerGroup[] }]};
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
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
      app.loadBalancers.data.push({
        name: 'elb-2', account: 'test', region: 'eu-west-1', serverGroups: [], vpcName: '',
      });
      const newSubsubGroup = { heading: 'eu-west-1', loadBalancer: app.loadBalancers.data[3], serverGroups: [] as ServerGroup[] };
      service.updateLoadBalancerGroups(app);
      $timeout.flush();
      expect(LoadBalancerFilterModel.asFilterModel.groups).toEqual([
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
