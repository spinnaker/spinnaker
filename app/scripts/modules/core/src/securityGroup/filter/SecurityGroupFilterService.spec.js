'use strict';

import * as State from '../../state';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: securityGroupFilterService', function () {
  var app;
  var resultJson;

  beforeEach(() => State.initialize());

  beforeEach(function () {
    app = {
      securityGroups: {
        data: [
          { name: 'sg-1', region: 'us-east-1', account: 'test', vpcName: '', usages: {} },
          { name: 'sg-1', region: 'us-west-1', account: 'test', vpcName: 'main', usages: {} },
          { name: 'sg-2', region: 'us-east-1', account: 'prod', vpcName: '', usages: {} },
        ],
      },
    };
    resultJson = [
      {
        heading: 'us-east-1',
        vpcName: '',
        securityGroup: app.securityGroups.data[0],
        isManaged: false,
        managedResourceSummary: undefined,
      },
      {
        heading: 'us-west-1 (main)',
        vpcName: 'main',
        securityGroup: app.securityGroups.data[1],
        isManaged: false,
        managedResourceSummary: undefined,
      },
      {
        heading: 'us-east-1',
        vpcName: '',
        securityGroup: app.securityGroups.data[2],
        isManaged: false,
        managedResourceSummary: undefined,
      },
    ];
    State.SecurityGroupState.filterModel.asFilterModel.clearFilters();
  });

  describe('Updating the firewall group', function () {
    it('no filter: should be transformed', function () {
      var expected = [
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ];
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual(expected);
    });

    describe('filter by search', function () {
      it('should add searchField when filter is not prefixed with vpc:', function () {
        expect(app.securityGroups.data.length).toBe(3);
        app.securityGroups.data.forEach((group) => {
          expect(group.searchField).toBeUndefined();
        });
        State.SecurityGroupState.filterModel.asFilterModel.sortFilter.filter = 'main';
        State.SecurityGroupState.filterService.updateSecurityGroups(app);
        app.securityGroups.data.forEach((group) => {
          expect(group.searchField).not.toBeUndefined();
        });
      });
    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', function () {
        State.SecurityGroupState.filterModel.asFilterModel.sortFilter.filter = 'vpc:main';
        State.SecurityGroupState.filterService.updateSecurityGroups(app);
        expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'test',
            subgroups: [
              { heading: 'sg-1', subgroups: [resultJson[1]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
      });

      it('should not match on partial vpc name', function () {
        State.SecurityGroupState.filterModel.asFilterModel.sortFilter.filter = 'vpc:main-old';
        State.SecurityGroupState.filterService.updateSecurityGroups(app);
        expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([]);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function () {
        State.SecurityGroupState.filterModel.asFilterModel.sortFilter.account = { prod: true };
        State.SecurityGroupState.filterService.updateSecurityGroups(app);
        expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
        ]);
      });

      it('All account filters: should show all accounts', function () {
        State.SecurityGroupState.filterModel.asFilterModel.sortFilter.account = { prod: true, test: true };
        State.SecurityGroupState.filterService.updateSecurityGroups(app);
        expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [
              { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            ],
          },
          {
            heading: 'test',
            subgroups: [
              {
                heading: 'sg-1',
                subgroups: [resultJson[0], resultJson[1]],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
          },
        ]);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region', function () {
      State.SecurityGroupState.filterModel.asFilterModel.sortFilter.region = { 'us-east-1': true };

      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            { heading: 'sg-1', subgroups: [resultJson[0]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
      ]);
    });

    it('All regions: should show all load balancers', function () {
      State.SecurityGroupState.filterModel.asFilterModel.sortFilter.region = { 'us-east-1': true, 'us-west-1': true };

      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ]);
    });
  });

  describe('filtered by provider type', function () {
    beforeEach(function () {
      app.securityGroups.data[0].provider = 'aws';
      app.securityGroups.data[1].provider = 'gce';
      app.securityGroups.data[2].provider = 'aws';
    });
    it('should filter by aws if checked', function () {
      State.SecurityGroupState.filterModel.asFilterModel.sortFilter.providerType = { aws: true };
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            { heading: 'sg-1', subgroups: [resultJson[0]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
      ]);
    });

    it('should not filter if no provider type is selected', function () {
      State.SecurityGroupState.filterModel.asFilterModel.sortFilter.providerType = undefined;
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ]);
    });

    it('should not filter if all provider are selected', function () {
      State.SecurityGroupState.filterModel.asFilterModel.sortFilter.providerType = { aws: true, gce: true };
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ]);
    });
  });

  describe('group diffing', function () {
    beforeEach(function () {
      app.securityGroups.data[0].stringVal = 'original';
      app.securityGroups.data[1].stringVal = 'should be deleted';
      State.SecurityGroupState.filterModel.asFilterModel.groups = [
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ];
    });

    it('adds a group when new one provided', function () {
      app.securityGroups.data.push({
        name: 'sg-1',
        account: 'management',
        region: 'us-east-1',
        vpcName: '',
      });
      var newGroup = {
        heading: 'management',
        subgroups: [
          {
            heading: 'sg-1',
            subgroups: [
              {
                heading: 'us-east-1',
                vpcName: '',
                securityGroup: app.securityGroups.data[3],
                isManaged: false,
                managedResourceSummary: undefined,
              },
            ],
            isManaged: false,
            managedResourceSummary: undefined,
          },
        ],
      };
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        newGroup,
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ]);
    });

    it('adds a subgroup when new one provided', function () {
      app.securityGroups.data.push({
        name: 'sg-3',
        account: 'prod',
        region: 'eu-west-1',
        vpcName: '',
      });
      var newSubGroup = {
        heading: 'sg-3',
        subgroups: [
          {
            heading: 'eu-west-1',
            vpcName: '',
            securityGroup: app.securityGroups.data[3],
            isManaged: false,
            managedResourceSummary: undefined,
          },
        ],
        isManaged: false,
        managedResourceSummary: undefined,
      };
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
            newSubGroup,
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
          ],
        },
      ]);
    });

    it('adds a sub-subgroup when new one provided', function () {
      app.securityGroups.data.push({
        name: 'sg-2',
        account: 'test',
        region: 'eu-west-1',
        vpcName: '',
      });
      var newSubsubGroup = {
        heading: 'eu-west-1',
        vpcName: '',
        securityGroup: app.securityGroups.data[3],
        isManaged: false,
        managedResourceSummary: undefined,
      };
      State.SecurityGroupState.filterService.updateSecurityGroups(app);
      expect(State.SecurityGroupState.filterModel.asFilterModel.groups).toEqual([
        {
          heading: 'prod',
          subgroups: [
            { heading: 'sg-2', subgroups: [resultJson[2]], isManaged: false, managedResourceSummary: undefined },
          ],
        },
        {
          heading: 'test',
          subgroups: [
            {
              heading: 'sg-1',
              subgroups: [resultJson[0], resultJson[1]],
              isManaged: false,
              managedResourceSummary: undefined,
            },
            { heading: 'sg-2', subgroups: [newSubsubGroup], isManaged: false, managedResourceSummary: undefined },
          ],
        },
      ]);
    });
  });
});
