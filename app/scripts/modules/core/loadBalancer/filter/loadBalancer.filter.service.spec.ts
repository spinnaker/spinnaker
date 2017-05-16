import { mock } from 'angular';

import { Application } from 'core/application/application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { ILoadBalancer, IServerGroup, ILoadBalancerGroup } from 'core/domain';
import { LOAD_BALANCER_FILTER_SERVICE, LoadBalancerFilterService } from './loadBalancer.filter.service';
import { LoadBalancerFilterModel } from './loadBalancerFilter.model';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: loadBalancerFilterService', function () {
  const debounceTimeout = 30;

  let service: LoadBalancerFilterService,
      loadBalancerFilterModel: LoadBalancerFilterModel,
      app: Application,
      resultJson: any,
      modelBuilder: ApplicationModelBuilder;

  beforeEach(() => {
    mock.module(
      APPLICATION_MODEL_BUILDER,
      LOAD_BALANCER_FILTER_SERVICE
    );
    mock.inject(
      function (applicationModelBuilder: ApplicationModelBuilder, loadBalancerFilterService: LoadBalancerFilterService, _loadBalancerFilterModel_: LoadBalancerFilterModel) {
        modelBuilder = applicationModelBuilder;
        service = loadBalancerFilterService;
        loadBalancerFilterModel = _loadBalancerFilterModel_;
        loadBalancerFilterModel.asFilterModel.groups = [];
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
    loadBalancerFilterModel.asFilterModel.clearFilters();
  });

  describe('Updating the load balancer group', function () {

    it('no filter: should be transformed', function (done) {
      const expected = [
        { heading: 'prod', subgroups: [
          { heading: 'elb-2', subgroups: [ resultJson[2] ]}
        ]},
        { heading: 'test', subgroups: [
          { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
        ]},
      ];
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual(expected);
        done();
      }, debounceTimeout);
    });

    describe('filter by search', function () {
      it('should add searchField when filter is not prefixed with vpc:', function (done) {
        expect(app.loadBalancers.data.length).toBe(3);
        app.loadBalancers.data.forEach((group: ILoadBalancerGroup) => {
          expect(group.searchField).toBeUndefined();
        });
        loadBalancerFilterModel.asFilterModel.sortFilter.filter = 'main';
        service.updateLoadBalancerGroups(app);

        setTimeout(() => {
          app.loadBalancers.data.forEach((group: ILoadBalancerGroup) => {
            expect(group.searchField).not.toBeUndefined();
          });
          done();
        }, debounceTimeout);
      });
    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', function (done) {
        loadBalancerFilterModel.asFilterModel.sortFilter.filter = 'vpc:main';
        service.updateLoadBalancerGroups(app);

        setTimeout(() => {
          expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
            { heading: 'test', subgroups: [
              { heading: 'elb-1', subgroups: [ resultJson[1] ]}
            ]}
          ]);
          done();
        }, debounceTimeout);
      });

      it('should not match on partial vpc name', function (done) {
        loadBalancerFilterModel.asFilterModel.sortFilter.filter = 'vpc:main-old';
        service.updateLoadBalancerGroups(app);
        setTimeout(() => {
          expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function (done) {
        loadBalancerFilterModel.asFilterModel.sortFilter.account = {prod: true};
        service.updateLoadBalancerGroups(app);

        setTimeout(() => {
          expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
            { heading: 'prod', subgroups: [
              { heading: 'elb-2', subgroups: [ resultJson[2] ]}
            ]}
          ]);
          done();
        }, debounceTimeout);
      });

      it('All account filters: should show all accounts', function (done) {
        loadBalancerFilterModel.asFilterModel.sortFilter.account = {prod: true, test: true};
        service.updateLoadBalancerGroups(app);

        setTimeout(() => {
          expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
            { heading: 'prod', subgroups: [
              { heading: 'elb-2', subgroups: [ resultJson[2] ]}
            ]},
            { heading: 'test', subgroups: [
              { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
            ]},
          ]);
          done();
        }, debounceTimeout);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.region = {'us-east-1' : true};
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0] ]}
          ]}
        ]);
        done();
      }, debounceTimeout);
    });

    it('All regions: should show all load balancers', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.region = {'us-east-1' : true, 'us-west-1': true};
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
          ]}
        ]);
        done();
      }, debounceTimeout);
    });
  });
  describe('filter by healthy state', function () {
    it('should filter any load balancers with down instances (based on down) if "Up" checked', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.status = {'Up' : true };
      app.loadBalancers.data[0].instanceCounts.down = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [ { id: 'foo', healthState: 'Up', health: [], launchTime: 0, zone: 'us-east-1a' } ];
      });
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[1] ]}
          ]}
        ]);
        done();
      }, debounceTimeout);
    });

    it('should filter any load balancers without down instances (based on down) if "Down" checked', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.status = {'Down' : true };
      app.loadBalancers.data[0].instanceCounts.down = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [ { id: 'foo', healthState: 'Down', health: [], launchTime: 0, zone: 'us-east-1a' } ];
      });
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0] ]}
          ]},
        ]);
        done();
      }, debounceTimeout);
    });

    it('should filter any load balancers with starting instances (based on starting) if "Starting" checked', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.status = {'Starting' : true };
      app.loadBalancers.data[0].instanceCounts.starting = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [ { id: 'foo', healthState: 'Starting', health: [], launchTime: 0, zone: 'us-east-1a' } ];
      });
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0] ]}
          ]},
        ]);
        done();
      }, debounceTimeout);
    });

  });

  describe('filtered by provider type', function () {
    beforeEach(function() {
      app.loadBalancers.data[0].type = 'aws';
      app.loadBalancers.data[1].type = 'gce';
      app.loadBalancers.data[2].type = 'aws';
    });
    it('should filter by aws if checked', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.providerType = {aws : true};
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0] ]}
          ]}
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if no provider type is selected', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.providerType = undefined;
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
          ]}
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if all provider are selected', function (done) {
      loadBalancerFilterModel.asFilterModel.sortFilter.providerType = {aws: true, gce: true};
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [ resultJson[2] ]}
          ]},
          { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [ resultJson[0], resultJson[1] ]}
          ]}
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('group diffing', function() {
    beforeEach(function() {
      app.loadBalancers.data[0].stringVal = 'original';
      app.loadBalancers.data[1].stringVal = 'should be deleted';
      loadBalancerFilterModel.asFilterModel.groups = [
        { heading: 'prod', subgroups: [
            { heading: 'elb-2', subgroups: [resultJson[2]] }
        ]},
        { heading: 'test', subgroups: [
            { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] }
        ]}
      ];
    });

    it('adds a group when new one provided', function(done) {
      app.loadBalancers.data.push({
        name: 'elb-1', account: 'management', region: 'us-east-1', serverGroups: [], vpcName: '',
      });
      const newGroup = { heading: 'management', subgroups: [
        { heading: 'elb-1', subgroups: [
          { heading: 'us-east-1', loadBalancer: app.loadBalancers.data[3], serverGroups: [] as IServerGroup[] }
        ]}
      ]};
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          newGroup,
          { heading: 'prod', subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]] }
          ]},
          { heading: 'test', subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] },
          ]}
        ]);
        done();
      }, debounceTimeout);
    });

    it('adds a subgroup when new one provided', function(done) {
      app.loadBalancers.data.push({
        name: 'elb-3', account: 'prod', region: 'eu-west-1', serverGroups: [], vpcName: '',
      });
      const newSubGroup = { heading: 'elb-3', subgroups: [{heading: 'eu-west-1', loadBalancer: app.loadBalancers.data[3], serverGroups: [] as IServerGroup[] }]};
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
          { heading: 'prod', subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]] },
              newSubGroup
          ]},
          { heading: 'test', subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[0], resultJson[1]] },
          ]}
        ]);
        done();
      }, debounceTimeout);
    });

    it('adds a sub-subgroup when new one provided', function(done) {
      app.loadBalancers.data.push({
        name: 'elb-2', account: 'test', region: 'eu-west-1', serverGroups: [], vpcName: '',
      });
      const newSubsubGroup = { heading: 'eu-west-1', loadBalancer: app.loadBalancers.data[3], serverGroups: [] as IServerGroup[] };
      service.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(loadBalancerFilterModel.asFilterModel.groups).toEqual([
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
        done();
      }, debounceTimeout);
    });

  });
});
