import { Application } from '../../application/application.model';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { ILoadBalancer, IServerGroup, ILoadBalancerGroup, IManagedResourceSummary } from '../../domain';
import { LoadBalancerState } from '../../state';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: loadBalancerFilterService', function () {
  const debounceTimeout = 30;

  let app: Application, resultJson: any;

  beforeEach(() => {
    LoadBalancerState.filterModel.asFilterModel.groups = [];
  });

  beforeEach(function () {
    app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'loadBalancers',
      lazy: true,
      defaultData: [],
    });
    app.getDataSource('loadBalancers').data = [
      {
        name: 'elb-1',
        region: 'us-east-1',
        account: 'test',
        vpcName: '',
        serverGroups: [],
        instanceCounts: { down: 0, starting: 0, outOfService: 0 },
        usages: {},
      },
      {
        name: 'elb-1',
        region: 'us-west-1',
        account: 'test',
        vpcName: 'main',
        serverGroups: [],
        instanceCounts: { down: 0, starting: 0, outOfService: 0 },
        usages: {},
      },
      {
        name: 'elb-2',
        region: 'us-east-1',
        account: 'prod',
        vpcName: '',
        serverGroups: [],
        instanceCounts: { down: 0, starting: 0, outOfService: 0 },
        usages: {},
      },
    ];

    resultJson = [
      {
        heading: 'us-east-1',
        loadBalancer: app.loadBalancers.data[0],
        serverGroups: [],
        isManaged: false,
        managedResourceSummary: undefined,
      },
      {
        heading: 'us-west-1',
        loadBalancer: app.loadBalancers.data[1],
        serverGroups: [],
        isManaged: false,
        managedResourceSummary: undefined,
      },
      {
        heading: 'us-east-1',
        loadBalancer: app.loadBalancers.data[2],
        serverGroups: [],
        isManaged: false,
        managedResourceSummary: undefined,
      },
    ];
    LoadBalancerState.filterModel.asFilterModel.clearFilters();
  });

  describe('Updating the load balancer group', function () {
    it('no filter: should be transformed', function (done) {
      const expected = [
        {
          heading: 'prod',
          subgroups: [
            {
              heading: 'elb-2',
              subgroups: [resultJson[2]],
              isManaged: false,
              managedResourceSummary: undefined as IManagedResourceSummary,
            },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'elb-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ];
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual(expected);
        done();
      }, debounceTimeout);
    });

    describe('filter by search', function () {
      it('should add searchField when filter is not prefixed with vpc:', function (done) {
        expect(app.loadBalancers.data.length).toBe(3);
        app.loadBalancers.data.forEach((group: ILoadBalancerGroup) => {
          expect(group.searchField).toBeUndefined();
        });
        LoadBalancerState.filterModel.asFilterModel.sortFilter.filter = 'main';
        LoadBalancerState.filterService.updateLoadBalancerGroups(app);

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
        LoadBalancerState.filterModel.asFilterModel.sortFilter.filter = 'vpc:main';
        LoadBalancerState.filterService.updateLoadBalancerGroups(app);

        setTimeout(() => {
          expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
            {
              heading: 'test',
              subgroups: [
                { heading: 'elb-1', subgroups: [resultJson[1]], isManaged: false, managedResourceSummary: undefined },
              ],
            },
          ]);
          done();
        }, debounceTimeout);
      });

      it('should not match on partial vpc name', function (done) {
        LoadBalancerState.filterModel.asFilterModel.sortFilter.filter = 'vpc:main-old';
        LoadBalancerState.filterService.updateLoadBalancerGroups(app);
        setTimeout(() => {
          expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function (done) {
        LoadBalancerState.filterModel.asFilterModel.sortFilter.account = { prod: true };
        LoadBalancerState.filterService.updateLoadBalancerGroups(app);

        setTimeout(() => {
          expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
            {
              heading: 'prod',
              subgroups: [
                { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
              ],
            },
          ]);
          done();
        }, debounceTimeout);
      });

      it('All account filters: should show all accounts', function (done) {
        LoadBalancerState.filterModel.asFilterModel.sortFilter.account = { prod: true, test: true };
        LoadBalancerState.filterService.updateLoadBalancerGroups(app);

        setTimeout(() => {
          expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
            {
              heading: 'prod',
              subgroups: [
                { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
              ],
            },
            {
              heading: 'test',
              subgroups: [
                {
                  heading: 'elb-1',
                  subgroups: [resultJson[0], resultJson[1]],
                  isManaged: false,
                  managedResourceSummary: undefined,
                },
              ],
            },
          ]);
          done();
        }, debounceTimeout);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.region = { 'us-east-1': true };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[0]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('All regions: should show all load balancers', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.region = { 'us-east-1': true, 'us-west-1': true };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'elb-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });
  });
  describe('filter by healthy state', function () {
    it('should filter any load balancers with down instances (based on down) if "Up" checked', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.status = { Up: true };
      app.loadBalancers.data[0].instanceCounts.down = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [
          { name: 'foo', id: 'foo', healthState: 'Up', health: [], launchTime: 0, zone: 'us-east-1a' },
        ];
      });
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[1]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should filter any load balancers without down instances (based on down) if "Down" checked', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.status = { Down: true };
      app.loadBalancers.data[0].instanceCounts.down = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [
          { name: 'foo', id: 'foo', healthState: 'Down', health: [], launchTime: 0, zone: 'us-east-1a' },
        ];
      });
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'test',
            subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[0]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should filter any load balancers with starting instances (based on starting) if "Starting" checked', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.status = { Starting: true };
      app.loadBalancers.data[0].instanceCounts.starting = 1;
      app.loadBalancers.data.forEach((loadBalancer: ILoadBalancer) => {
        loadBalancer.instances = [
          { name: 'foo', id: 'foo', healthState: 'Starting', health: [], launchTime: 0, zone: 'us-east-1a' },
        ];
      });
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'test',
            subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[0]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filtered by provider type', function () {
    beforeEach(function () {
      app.loadBalancers.data[0].type = 'aws';
      app.loadBalancers.data[1].type = 'gce';
      app.loadBalancers.data[2].type = 'aws';
    });
    it('should filter by aws if checked', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.providerType = { aws: true };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              { heading: 'elb-1', subgroups: [resultJson[0]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if no provider type is selected', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.providerType = undefined;
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'elb-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if all provider are selected', function (done) {
      LoadBalancerState.filterModel.asFilterModel.sortFilter.providerType = { aws: true, gce: true };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'elb-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('group diffing', function () {
    beforeEach(function () {
      app.loadBalancers.data[0].stringVal = 'original';
      app.loadBalancers.data[1].stringVal = 'should be deleted';
      LoadBalancerState.filterModel.asFilterModel.groups = [
        {
          heading: 'prod',
          subgroups: [
            { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'elb-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ];
    });

    it('adds a group when new one provided', function (done) {
      app.loadBalancers.data.push({
        name: 'elb-1',
        account: 'management',
        region: 'us-east-1',
        serverGroups: [],
        vpcName: '',
      });
      const newGroup = {
        heading: 'management',
        subgroups: [
          {
            heading: 'elb-1',
            subgroups: [
              {
                heading: 'us-east-1',
                loadBalancer: app.loadBalancers.data[3],
                serverGroups: [] as IServerGroup[],
                isManaged: false,
                managedResourceSummary: undefined as IManagedResourceSummary,
              },
            ],
            isManaged: false,
            managedResourceSummary: undefined as IManagedResourceSummary,
          },
        ],
      };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          newGroup,
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'elb-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('adds a subgroup when new one provided', function (done) {
      app.loadBalancers.data.push({
        name: 'elb-3',
        account: 'prod',
        region: 'eu-west-1',
        serverGroups: [],
        vpcName: '',
      });
      const newSubGroup = {
        heading: 'elb-3',
        subgroups: [
          {
            heading: 'eu-west-1',
            loadBalancer: app.loadBalancers.data[3],
            serverGroups: [] as IServerGroup[],
            isManaged: false,
            managedResourceSummary: undefined as IManagedResourceSummary,
          },
        ],
        isManaged: false,
        managedResourceSummary: undefined as IManagedResourceSummary,
      };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'elb-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
              newSubGroup,
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'elb-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('adds a sub-subgroup when new one provided', function (done) {
      app.loadBalancers.data.push({
        name: 'elb-2',
        account: 'test',
        region: 'eu-west-1',
        serverGroups: [],
        vpcName: '',
      });
      const newSubsubGroup = {
        heading: 'eu-west-1',
        loadBalancer: app.loadBalancers.data[3],
        serverGroups: [] as IServerGroup[],
        isManaged: false,
        managedResourceSummary: undefined as IManagedResourceSummary,
      };
      LoadBalancerState.filterService.updateLoadBalancerGroups(app);

      setTimeout(() => {
        expect(LoadBalancerState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              {
                heading: 'elb-2',
                subgroups: [resultJson[2]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'elb-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
              { heading: 'elb-2', subgroups: [newSubsubGroup], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });
  });
});
