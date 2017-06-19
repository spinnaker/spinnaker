import {mock} from 'angular';
import * as _ from 'lodash';
import {CLUSTER_FILTER_SERVICE, ClusterFilterService} from 'core/cluster/filter/clusterFilter.service';
import {CLUSTER_SERVICE} from 'core/cluster/cluster.service';
import {Application} from 'core/application/application.model';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {CLUSTER_FILTER_MODEL} from './clusterFilter.model';

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests now
describe('Service: clusterFilterService', function () {
  const debounceTimeout = 30;

  let service: ClusterFilterService;
  let clusterService: any;
  let ClusterFilterModel: any;
  let MultiselectModel: any;
  let applicationJSON: any;
  let groupedJSON: any;
  let applicationModelBuilder: ApplicationModelBuilder;
  let application: Application;

  beforeEach(function() {
    mock.module(
      CLUSTER_FILTER_SERVICE,
      APPLICATION_MODEL_BUILDER,
      CLUSTER_FILTER_MODEL,
      CLUSTER_SERVICE,
      require('./mockApplicationData.js')
    );
    mock.inject(
      function (clusterFilterService: ClusterFilterService, _ClusterFilterModel_: any, _MultiselectModel_: any,
                _applicationJSON_: any, _groupedJSON_: any, _clusterService_: any,
                _applicationModelBuilder_: ApplicationModelBuilder) {
        service = clusterFilterService;
        clusterService = _clusterService_;
        ClusterFilterModel = _ClusterFilterModel_;
        MultiselectModel = _MultiselectModel_;
        applicationModelBuilder = _applicationModelBuilder_;
        ClusterFilterModel.groups = [];

        applicationJSON = _applicationJSON_;
        groupedJSON = _groupedJSON_;
        groupedJSON[0].subgroups[0].cluster = applicationJSON.clusters[0];
        groupedJSON[1].subgroups[0].cluster = applicationJSON.clusters[1];
      }
    );

    this.buildApplication = (json: any) => {
      const app = applicationModelBuilder.createApplication('app', {key: 'serverGroups', lazy: true});
      if (json.serverGroups) {
        app.getDataSource('serverGroups').data = _.cloneDeep(json.serverGroups.data);
      }
      if (json.clusters) {
        app.clusters = json.clusters;
      }
      return app;
    };

    this.verifyTags = function(expectedTags: any[]) {
      const actual: any = ClusterFilterModel.tags;
      expect(actual.length).toBe(expectedTags.length);
      expectedTags.forEach(function(expected: any) {
        expect(actual.some(function(test: any) {
          return test.key === expected.key && test.label === expected.label && test.value === expected.value;
        })).toBe(true);
      });
    };

    application = this.buildApplication(applicationJSON);

  });

  describe('Updating the cluster group', function () {
    it('no filter: should be transformed', function (done) {
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        done();
      }, debounceTimeout);
    });

    describe('filter by cluster', function () {
      it('should filter by cluster name as an exact match', function (done) {
        ClusterFilterModel.sortFilter.filter = 'cluster:in-us-west-1-only';
        const expected: any = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      });

      it('should not match on partial cluster name', function (done) {
        ClusterFilterModel.sortFilter.filter = 'cluster:in-us-west-1';
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      });

    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', function (done) {
        ClusterFilterModel.sortFilter.filter = 'vpc:main';
        const expected: any = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      });

      it('should not match on partial vpc name', function (done) {
        ClusterFilterModel.sortFilter.filter = 'vpc:main-old';
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      });
    });

    describe('filter by clusters', function () {
      it('should filter by cluster names as an exact match', function (done) {
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only';
        const expected: any = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      });

      it('should not match on partial cluster name', function (done) {
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1';
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      });

      it('should perform an OR match on comma separated list, ignoring spaces', function (done) {
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only, in-eu-east-2-only';
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual(groupedJSON);
          done();
        }, debounceTimeout);
      });
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function (done) {
        ClusterFilterModel.sortFilter.account = {prod: true};
        const expectedProd: any = _.filter(groupedJSON, {heading: 'prod'});
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual(expectedProd);
          this.verifyTags([
            { key: 'account', label: 'account', value: 'prod' }
          ]);
          done();
        }, debounceTimeout);
      });

      it('All account filters: should show all accounts', function (done) {
        ClusterFilterModel.sortFilter.account = {prod: true, test: true};
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups).toEqual(groupedJSON);
          this.verifyTags([
            { key: 'account', label: 'account', value: 'prod' },
            { key: 'account', label: 'account', value: 'test' },
          ]);
          done();
        }, debounceTimeout);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region ', function (done) {
      ClusterFilterModel.sortFilter.region = {'us-west-1' : true};
      const expected: any = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'region', label: 'region', value: 'us-west-1' },
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filter by healthy status', function () {
    it('should filter by health if checked', function (done) {
      ClusterFilterModel.sortFilter.status = {healthy : true };
      const expected: any = _.filter(groupedJSON,
        {
          subgroups: [{
            subgroups: [{
              serverGroups: [{
                instances: [ { health: [{state: 'Up'}]}]
              }]
            }]
          }]
        }
      );
      service.updateClusterGroups(application)
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'status', label: 'status', value: 'healthy' },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter by healthy if unchecked', function (done) {
      ClusterFilterModel.sortFilter.status = {healthy : false};
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filter by unhealthy status', function () {
    it('should filter by unhealthy status if checked', function (done) {
      ClusterFilterModel.sortFilter.status = {unhealthy: true};
      const expected: any = _.filter(groupedJSON,
        {
          subgroups: [{
            subgroups: [{
              serverGroups: [{
                instances: [ { health: [{state: 'Down'}]}]
              }]
            }]
          }]
        }
      );

      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'status', label: 'status', value: 'unhealthy' },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter by unhealthy if unchecked', function (done) {
      ClusterFilterModel.sortFilter.status = {unhealthy : false};
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([]);
        done();
      }, debounceTimeout);
    });

  });

  describe('filter by both healthy and unhealthy status', function () {
    it('should not filter by healthy if unchecked', function (done) {
      ClusterFilterModel.sortFilter.status = {unhealthy : true, healthy: true};
      const expected: any = _.filter(groupedJSON,
        {
          subgroups: [{
            subgroups: [{
              serverGroups: [{
                instances: [ { health: [{state: 'Down'}]}]
              }]
            }]
          }]
        }
      );
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'status', label: 'status', value: 'healthy' },
          { key: 'status', label: 'status', value: 'unhealthy' },
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filter by disabled status', function () {
    it('should filter by disabled status if checked', function (done) {
      ClusterFilterModel.sortFilter.status = {Disabled: true};
      const expected: any = _.filter(groupedJSON,
        {
          subgroups: [{
            subgroups: [{
              serverGroups: [{
                isDisabled: true
              }]
            }]
          }]
        }
      );
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'status', label: 'status', value: 'Disabled' },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if the status is unchecked', function (done) {
      ClusterFilterModel.sortFilter.status = { Disabled: false };
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filter by starting status', function() {
    it('should filter by starting status if checked', function(done) {
      const starting: any = { healthState: 'Unknown'},
        serverGroup = application.getDataSource('serverGroups').data[0];
      serverGroup.instances.push(starting);

      ClusterFilterModel.sortFilter.status = {Starting: true};
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual([]);

        starting.healthState = 'Starting';
        serverGroup.instanceCounts.starting = 1;
        service.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterFilterModel.groups.length).toBe(1);
          this.verifyTags([
            { key: 'status', label: 'status', value: 'Starting' },
          ]);
          done();
        }, debounceTimeout);
      }, debounceTimeout);
    });
  });

  describe('filter by out of service status', function() {
    it('should filter out by out of service status if checked', function(done) {
      const starting: any = { healthState: 'Unknown' },
        serverGroup = application.getDataSource('serverGroups').data[0];
      serverGroup.instances.push(starting);

      ClusterFilterModel.sortFilter.status = {OutOfService: true};
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual([]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filter by unknown status', function() {
    it('should filter out by unknown status if checked', function(done) {
      const starting: any = { healthState: 'Up' },
        serverGroup = application.getDataSource('serverGroups').data[0];
      serverGroup.instances.push(starting);

      ClusterFilterModel.sortFilter.status = {Unknown: true};
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual([]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filtered by provider type', function () {
    it('should filter by aws if checked', function (done) {
      ClusterFilterModel.sortFilter.providerType = {aws: true};
      const expected: any = _.filter(groupedJSON,
        {
          subgroups: [{
            subgroups: [{
              serverGroups: [{
                type: 'aws'
              }]
            }]
          }]
        }
      );
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'providerType', label: 'provider', value: 'aws' },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if no provider type is selected', function (done) {
      ClusterFilterModel.sortFilter.providerType = undefined;
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if all provider are selected', function (done) {
      ClusterFilterModel.sortFilter.providerType = {aws: true, gce: true};
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([
          { key: 'providerType', label: 'provider', value: 'aws' },
          { key: 'providerType', label: 'provider', value: 'gce' },
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filtered by instance type', function () {
    it('should filter by m3.large if checked', function (done) {
      ClusterFilterModel.sortFilter.instanceType = {'m3.large': true};
      const expected: any = _.filter(groupedJSON,
        {
          subgroups: [{
            subgroups: [{
              serverGroups: [{
                instanceType: 'm3.large'
              }]
            }]
          }]
        }
      );
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(expected);
        this.verifyTags([
          { key: 'instanceType', label: 'instance type', value: 'm3.large' },
        ]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if no instance type selected', function (done) {
      ClusterFilterModel.sortFilter.instanceType = undefined;
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([]);
        done();
      }, debounceTimeout);
    });

    it('should not filter if the instance type is unchecked', function (done) {
      ClusterFilterModel.sortFilter.instanceType = {'m3.large' : false};
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual(groupedJSON);
        this.verifyTags([]);
        done();
      }, debounceTimeout);
    });
  });

  describe('filter by instance counts', function () {

    it('should filter by min instances', function (done) {
      ClusterFilterModel.sortFilter.minInstances = 1;
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual([groupedJSON[1]]);
        this.verifyTags([
          { key: 'minInstances', label: 'instance count (min)', value: 1 }
        ]);
        done();
      }, debounceTimeout);
    });

    it('should filter by max instances', function(done) {
      ClusterFilterModel.sortFilter.maxInstances = 0;
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups).toEqual([groupedJSON[0]]);
        this.verifyTags([
          { key: 'maxInstances', label: 'instance count (max)', value: 0 }
        ]);
        done();
      }, debounceTimeout);
    });
  });

  describe('multiInstance filtering', function () {
    beforeEach(function() {
      this.navigationSynced = false;
      ClusterFilterModel.sortFilter.multiselect = true;
      spyOn(MultiselectModel, 'syncNavigation').and.callFake(() => this.navigationSynced = true);
    });

    it('should remove all instanceIds if server group is no longer visible, and add back when visible again', function (done) {
      ClusterFilterModel.sortFilter.listInstances = true;
      const serverGroup = application.getDataSource('serverGroups').data[0],
          multiselectGroup = MultiselectModel.getOrCreateInstanceGroup(serverGroup);

      serverGroup.instances.push({id: 'i-1234'});
      MultiselectModel.toggleSelectAll(serverGroup, ['i-1234']);
      expect(multiselectGroup.instanceIds).toEqual(['i-1234']);

      ClusterFilterModel.sortFilter.region['us-east-3'] = true;
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(multiselectGroup.instanceIds).toEqual([]);

        ClusterFilterModel.sortFilter.region['us-east-3'] = false;
        service.updateClusterGroups(application);

        setTimeout(() => {
        expect(multiselectGroup.instanceIds).toEqual(['i-1234']);
          done();
        }, debounceTimeout);
      }, debounceTimeout);
    });

    it('should remove instances that are no longer visible', function (done) {
      ClusterFilterModel.sortFilter.listInstances = true;
      const serverGroup = application.getDataSource('serverGroups').data[0];

      MultiselectModel.toggleInstance(serverGroup, 'i-1234');
      MultiselectModel.toggleInstance(serverGroup, 'i-2345');
      serverGroup.instances.push({id: 'i-1234'});

      expect(MultiselectModel.instanceIsSelected(serverGroup, 'i-1234')).toBe(true);
      expect(MultiselectModel.instanceIsSelected(serverGroup, 'i-2345')).toBe(true);

      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(MultiselectModel.instanceIsSelected(serverGroup, 'i-1234')).toBe(true);
        expect(MultiselectModel.instanceIsSelected(serverGroup, 'i-2345')).toBe(false);

        expect(this.navigationSynced).toBe(true);
        done();
      }, debounceTimeout);


    });

    it('should add all instances when selectAll is selected and new instances appear in server group', function (done) {
      ClusterFilterModel.sortFilter.listInstances = true;
      const serverGroup = application.getDataSource('serverGroups').data[0];

      MultiselectModel.getOrCreateInstanceGroup(serverGroup).selectAll = true;
      MultiselectModel.toggleInstance(serverGroup, 'i-1234');
      serverGroup.instances.push({id: 'i-1234'});
      serverGroup.instances.push({id: 'i-2345'});

      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(MultiselectModel.instanceIsSelected(serverGroup, 'i-1234')).toBe(true);
        expect(MultiselectModel.instanceIsSelected(serverGroup, 'i-2345')).toBe(true);

        expect(this.navigationSynced).toBe(true);
        done();
      }, debounceTimeout);
    });

    it('should remove all instance groups when listInstances is false', function (done) {
      ClusterFilterModel.sortFilter.listInstances = false;
      const serverGroup = application.getDataSource('serverGroups').data[0];

      MultiselectModel.toggleInstance(serverGroup, 'i-1234');

      expect(MultiselectModel.instanceGroups.length).toBe(1);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(MultiselectModel.instanceGroups.length).toBe(0);
        expect(this.navigationSynced).toBe(true);
        done();
      }, debounceTimeout);
    });
  });

  describe('clear all filters', function () {

    beforeEach(function () {
      ClusterFilterModel.sortFilters = undefined;
    });

    it('should clear set providerType filter', function () {
      ClusterFilterModel.sortFilter.providerType = {aws: true};
      expect(ClusterFilterModel.sortFilter.providerType).toBeDefined();
      service.clearFilters();
      expect(ClusterFilterModel.sortFilter.providerType).toBeUndefined();
      this.verifyTags([]);
    });

  });

  describe('group diffing', function() {
    beforeEach(function() {
      this.clusterA = { account: 'prod', category: 'serverGroup', name: 'cluster-a' };
      this.clusterB = { account: 'prod', category: 'serverGroup', name: 'cluster-b' };
      this.serverGroup001 = { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original', category: 'serverGroup', instances: [] };
      this.serverGroup000 = { cluster: 'cluster-a', name: 'cluster-a-v000', account: 'prod', region: 'us-east-1', stringVal: 'should be deleted', category: 'serverGroup', instances: [] };
      ClusterFilterModel.groups = [
        {
          heading: 'prod',
          key: 'prod',
          subgroups: [
            {
              heading: 'cluster-a',
              key: 'cluster-a:serverGroup',
              category: 'serverGroup',
              cluster: { name: 'cluster-a' },
              subgroups: [
                {
                  heading: 'us-east-1',
                  category: 'serverGroup',
                  key: 'us-east-1:serverGroup',
                  serverGroups: [
                    this.serverGroup000,
                    this.serverGroup001,
                  ]
                }
              ]
            },
          ],
        },
      ];
    });

    it('adds a group when new one provided', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'test', region: 'us-east-1', stringVal: 'new', category: 'serverGroup' },
        ]},
        clusters: [
          this.clusterA,
          { name: 'cluster-a', account: 'test', category: 'serverGroup' },
        ]
      });
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(2);
        expect(ClusterFilterModel.groups[1].heading).toBe('test');
        expect(ClusterFilterModel.groups[1].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[1].subgroups[0].heading).toBe('cluster-a');
        expect(ClusterFilterModel.groups[1].subgroups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[1].subgroups[0].subgroups[0].heading).toBe('us-east-1');
        expect(ClusterFilterModel.groups[1].subgroups[0].subgroups[0].serverGroups.length).toBe(1);
        expect(ClusterFilterModel.groups[1].subgroups[0].subgroups[0].serverGroups[0].name).toBe('cluster-a-v003');
        done();
      }, debounceTimeout);
    });

    it('adds a subgroup when new one provided', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-b', name: 'cluster-a-v003', account: 'prod', region: 'us-east-1', stringVal: 'new', category: 'serverGroup' },
        ]},
        clusters: [
          this.clusterA,
          this.clusterB,
        ]
      });
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups.length).toBe(2);
        expect(ClusterFilterModel.groups[0].subgroups[1].heading).toBe('cluster-b');
        expect(ClusterFilterModel.groups[0].subgroups[1].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[1].subgroups[0].heading).toBe('us-east-1');
        expect(ClusterFilterModel.groups[0].subgroups[1].subgroups[0].serverGroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[1].subgroups[0].serverGroups[0].name).toBe('cluster-a-v003');
        done();
      }, debounceTimeout);
    });

    it('adds a sub-subgroup when new one provided', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'prod', region: 'us-west-1', stringVal: 'new', category: 'serverGroup', instances: [] },
        ]},
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(2);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[1].heading).toBe('us-west-1');
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[1].serverGroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[1].serverGroups[0].name).toBe('cluster-a-v003');
        done();
      }, debounceTimeout);
    });

    it('adds a server group when new one provided in same sub-sub-group', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'prod', region: 'us-east-1', stringVal: 'new', category: 'serverGroup', instances: [] },
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);
      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups.length).toBe(3);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[2].name).toBe('cluster-a-v003');
        done();
      }, debounceTimeout);
    });

    it('removes a group when one goes away', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'test', region: 'us-east-1', stringVal: 'new', category: 'serverGroup', instances: [] },
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(2);
        application.getDataSource('serverGroups').data.splice(0, 2);
        service.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterFilterModel.groups.length).toBe(1);
          expect(ClusterFilterModel.groups[0].heading).toBe('test');
          done();
        }, debounceTimeout);
      }, debounceTimeout);
    });

    it('removes a subgroup when one goes away', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-b', name: 'cluster-a-v003', account: 'prod', region: 'us-east-1', stringVal: 'new', category: 'serverGroup', instances: [] },
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups.length).toBe(2);

        application.getDataSource('serverGroups').data.splice(0, 2);
        service.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterFilterModel.groups.length).toBe(1);
          expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
          expect(ClusterFilterModel.groups[0].subgroups[0].heading).toBe('cluster-b');
          done();
        }, debounceTimeout);
      }, debounceTimeout);
    });

    it('removes a sub-subgroup when one goes away', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'prod', region: 'us-west-1', stringVal: 'new', category: 'serverGroup', instances: [] },
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(2);

        application.getDataSource('serverGroups').data.splice(0, 2);
        service.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterFilterModel.groups.length).toBe(1);
          expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
          expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
          expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].heading).toBe('us-west-1');
          done();
        }, debounceTimeout);
      }, debounceTimeout);
    });

    it('removes a server group when one goes away', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          this.serverGroup001,
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups.length).toBe(1);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0].name).toBe('cluster-a-v001');
        done();
      }, debounceTimeout);
    });

    it('leaves server groups alone when stringVal does not change', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          { cluster: 'cluster-a', name: 'cluster-a-v000', account: 'prod', region: 'us-east-1', stringVal: 'should be deleted', category: 'serverGroup', instances: [] },
          { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original', category: 'serverGroup', instances: [] },
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(this.serverGroup000);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[1]).toBe(this.serverGroup001);
        done();
      }, debounceTimeout);
    });

    it('replaces server group when stringVal changes', function(done) {
      application = this.buildApplication({
        serverGroups: { data: [
          { cluster: 'cluster-a', name: 'cluster-a-v000', account: 'prod', region: 'us-east-1', stringVal: 'mutated', category: 'serverGroup', instances: [] },
          { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original', category: 'serverGroup', instances: [] },
        ]}
      });
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).not.toBe(this.serverGroup000);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(application.getDataSource('serverGroups').data[0]);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[1]).toBe(this.serverGroup001);
        done();
      }, debounceTimeout);
    });

    it('adds executions and running tasks, even when stringVal does not change', function (done) {
      const runningTasks: any = [ { name: 'a' } ],
          executions: any = [ { name: 'b' } ];
      application = this.buildApplication({
        serverGroups: { data: [
          { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original',
            category: 'serverGroup', instances: []
          },
        ]}
      });
      application.getDataSource('serverGroups').data[0].runningTasks = runningTasks;
      application.getDataSource('serverGroups').data[0].runningExecutions = executions;
      application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
      service.updateClusterGroups(application);

      setTimeout(() => {
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(this.serverGroup001);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0].runningTasks).toBe(runningTasks);
        expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0].runningExecutions).toBe(executions);
        done();
      }, debounceTimeout);
    });
  });
});
