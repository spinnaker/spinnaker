'use strict';


describe('Service: clusterFilterService', function () {


  var service;
  var $location;
  var ClusterFilterModel;
  var applicationJSON;
  var groupedJSON;
  var _;

  beforeEach(
    window.module(
      require('utils/lodash.js'),
      require('./clusterFilterService.js'),
      require('./clusterFilterModel.js'),
      require('../../../../test/mock/mockApplicationData.js')
    )
  );

  beforeEach(
    window.inject(
      function (_$location_, clusterFilterService, _ClusterFilterModel_, ___) {
        _ = ___;
        service = clusterFilterService;
        $location = _$location_;
        ClusterFilterModel = _ClusterFilterModel_;
      }
    )
  );

  beforeEach(
    window.inject(
      function (_applicationJSON_, _groupedJSON_) {
        applicationJSON = _applicationJSON_;
        groupedJSON = _groupedJSON_;
        groupedJSON[0].subgroups[0].cluster = applicationJSON.clusters[0];
        groupedJSON[1].subgroups[0].cluster = applicationJSON.clusters[1];
      }
    )
  );

  beforeEach(function() {
    this.verifyTags = function(expectedTags) {
      var actual = ClusterFilterModel.sortFilter.tags;
      expect(actual.length).toBe(expectedTags.length);
      expectedTags.forEach(function(expected) {
        expect(actual.some(function(test) {
          return test.key === expected.key && test.label === expected.label && test.value === expected.value;
        })).toBe(true);
      });
    };
  });

  it('should have the service injected in the test', function () {
    expect(service).toBeDefined();
    expect($location).toBeDefined();
  });


  describe('update query params', function () {

    describe('Account Params:', function () {

      beforeEach(function () {
        ClusterFilterModel.sortFilter.account = undefined;
      });

      it('0 accounts: should add nothing to the query param if there is nothing on the model', function () {
        service.updateQueryParams();
        expect($location.search().acct).toBeUndefined();
      });

      it('1 accounts: should add the account name to the acct query string ', function () {
        ClusterFilterModel.sortFilter.account = { prod: true };
        service.updateQueryParams();
        expect($location.search().acct).toEqual('prod');
      });

      it('N accounts: should add multiple account names to the acct query string ', function () {
        ClusterFilterModel.sortFilter.account = { prod: true, test: true };
        service.updateQueryParams();
        expect($location.search().acct).toEqual('prod,test');
      });

      it('False accounts; should only add account names that are flagged as true to the query string', function () {
        ClusterFilterModel.sortFilter.account = { prod: true, test: false};
        service.updateQueryParams();
        expect($location.search().acct).toEqual('prod');
      });

    });

    describe('Region Params', function () {

      beforeEach(function() {
        ClusterFilterModel.sortFilter.region = undefined;
      });

      it('0 regions: should add nothing to the query params', function () {
        service.updateQueryParams();
        expect($location.search().reg).toBeUndefined();
      });

      it('1 region: should add the region name to the reg query string', function () {
        ClusterFilterModel.sortFilter.region = {'us-west-1': true};
        service.updateQueryParams();
        expect($location.search().reg).toEqual('us-west-1');
      });

      it('N regions: should add the region names to the reg query string', function () {
        ClusterFilterModel.sortFilter.region = {'us-west-1': true, 'eu-east-2': true};
        service.updateQueryParams();
        expect($location.search().reg).toEqual('us-west-1,eu-east-2');
      });

      it('False regions: should only add the region names flagged as true to the reg query string', function () {
        ClusterFilterModel.sortFilter.region = {'us-west-1': true, 'eu-east-2': false};
        service.updateQueryParams();
        expect($location.search().reg).toEqual('us-west-1');
      });
    });

    describe('Status Params:', function () {
      beforeEach(function () {
        ClusterFilterModel.sortFilter.status = undefined;
      });

      describe('Healthy', function () {
        it('should add the "healthy" status to the query string if selected', function () {
          ClusterFilterModel.sortFilter.status = {healthy: true};
          service.updateQueryParams();
          expect($location.search().status).toEqual('healthy');
        });
      });
    });

    describe('Provider Type Params', function () {
      beforeEach(function () {
        ClusterFilterModel.sortFilter.providerType = undefined;
      });

      describe('AWS', function () {
        it('should add aws to the query string if selected', function () {
          ClusterFilterModel.sortFilter.providerType = {aws: true};
          service.updateQueryParams();
          expect($location.search().providerType).toEqual('aws');
        });

        it('should add all types to the query string if multiple selected', function () {
          ClusterFilterModel.sortFilter.providerType= {aws: true, gce: true};
          service.updateQueryParams();
          expect($location.search().providerType).toEqual('aws,gce');
        });

        it('should not add types to the query string if de-selected', function () {
          ClusterFilterModel.sortFilter.providerType = {aws: false, gce: false};
          service.updateQueryParams();
          expect($location.search().providerType).toBeUndefined();
        });
      });
    });

    describe('Instance Type Params', function () {
      beforeEach(function () {
        ClusterFilterModel.sortFilter.instanceType = undefined;
      });

      describe('check instance type', function () {
        it('should add the instance type to the query string', function () {
          ClusterFilterModel.sortFilter.instanceType = {'m3.large': true};
          service.updateQueryParams();
          expect($location.search().instanceType).toEqual('m3.large');
        });

        it('should add multiple instance types to the query string if checked', function () {
          ClusterFilterModel.sortFilter.instanceType = {'m3.large': true, 'm3.xlarge': true};
          service.updateQueryParams();
          expect($location.search().instanceType).toEqual('m3.large,m3.xlarge');
        });

        it('should remove instance types to the query string if unchecked', function () {
          ClusterFilterModel.sortFilter.instanceType = {'m3.large': false, 'm3.xlarge': false};
          service.updateQueryParams();
          expect($location.search().instanceType).toBeUndefined();
        });
      });
    });

    describe('Instance Count Params', function () {
      beforeEach(function () {
        ClusterFilterModel.sortFilter.minInstances = undefined;
        ClusterFilterModel.sortFilter.maxInstances = undefined;
      });

      describe('check instance count', function () {
        it('should add instance count whenever it is numeric', function () {
          ClusterFilterModel.sortFilter.minInstances = 3;
          ClusterFilterModel.sortFilter.maxInstances = 4;
          service.updateQueryParams();
          expect($location.search().minInstances).toEqual('3');
          expect($location.search().maxInstances).toEqual('4');
        });

        it('should include zero, even though it is falsy', function () {
          ClusterFilterModel.sortFilter.minInstances = 0;
          ClusterFilterModel.sortFilter.maxInstances = 0;
          service.updateQueryParams();
          expect($location.search().minInstances).toEqual('0');
          expect($location.search().maxInstances).toEqual('0');
        });

        it('should not add non-numeric values', function () {
          ClusterFilterModel.sortFilter.minInstances = 'nan';
          ClusterFilterModel.sortFilter.maxInstances = 'also nan';
          service.updateQueryParams();
          expect($location.search().minInstances).toBeUndefined();
          expect($location.search().maxInstances).toBeUndefined();
        });
      });
    });

  });


  describe('Updating the cluster group', function () {

    it('no filter: should be transformed', function () {
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
    });

    describe('filter by cluster', function () {
      it('should filter by cluster name as an exact match', function () {
        ClusterFilterModel.sortFilter.filter = 'cluster:in-us-west-1-only';
        var expected = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
        expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      });

      it('should not match on partial cluster name', function () {
        ClusterFilterModel.sortFilter.filter = 'cluster:in-us-west-1';
        expect(service.updateClusterGroups(applicationJSON)).toEqual([]);
      });

    });

    describe('filter by clusters', function () {
      it('should filter by cluster names as an exact match', function () {
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only';
        var expected = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
        expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      });

      it('should not match on partial cluster name', function () {
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1';
        expect(service.updateClusterGroups(applicationJSON)).toEqual([]);
      });

      it('should perform an OR match on comma separated list, ignoring spaces', function () {
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only, in-eu-east-2-only';
        expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
        ClusterFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only,in-eu-east-2-only';
        expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
        ClusterFilterModel.sortFilter.filter = 'clusters: in-us-west-1-only,in-eu-east-2-only';
        expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
        ClusterFilterModel.sortFilter.filter = 'clusters: in-us-west-1-only, in-eu-east-2-only';
        expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      });

    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function () {
        ClusterFilterModel.sortFilter.account = {prod: true};
        var expectedProd = _.filter(groupedJSON, {heading:'prod'});
        expect(service.updateClusterGroups(applicationJSON)).toEqual(expectedProd);
        this.verifyTags([
          { key: 'account', label: 'account', value: 'prod' }
        ]);
      });

      it('All account filters: should show all accounts', function () {
        ClusterFilterModel.sortFilter.account = {prod: true, test: true};
        expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
        this.verifyTags([
          { key: 'account', label: 'account', value: 'prod' },
          { key: 'account', label: 'account', value: 'test' },
        ]);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region ', function () {
      ClusterFilterModel.sortFilter.region = {'us-west-1' : true};
      var expected = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'region', label: 'region', value: 'us-west-1' },
      ]);
    });
  });

  describe('filter by healthy status', function () {
    it('should filter by health if checked', function () {
      ClusterFilterModel.sortFilter.status = {healthy : true };
      var expected = _.filter(groupedJSON,
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

      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'status', label: 'status', value: 'healthy' },
      ]);
    });

    it('should not filter by healthy if unchecked', function () {
      ClusterFilterModel.sortFilter.status = {healthy : false};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
    });
  });

  describe('filter by unhealthy status', function () {
    it('should filter by unhealthy status if checked', function () {
      ClusterFilterModel.sortFilter.status = {unhealthy: true};
      var expected = _.filter(groupedJSON,
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

      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'status', label: 'status', value: 'unhealthy' },
      ]);
    });

    it('should not filter by unhealthy if unchecked', function () {
      ClusterFilterModel.sortFilter.status = {unhealthy : false};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
    });

  });

  describe('filter by both healthy and unhealthy status', function () {
    it('should not filter by healthy if unchecked', function () {
      ClusterFilterModel.sortFilter.status = {unhealthy : true, healthy: true};
      var expected = _.filter(groupedJSON,
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
      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'status', label: 'status', value: 'healthy' },
        { key: 'status', label: 'status', value: 'unhealthy' },
      ]);
    });
  });

  describe('filter by disabled status', function () {
    it('should filter by disabled status if checked', function () {
      ClusterFilterModel.sortFilter.status = {Disabled: true};
      var expected = _.filter(groupedJSON,
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
      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'status', label: 'status', value: 'Disabled' },
      ]);
    });

    it('should not filter if the status is unchecked', function () {
      ClusterFilterModel.sortFilter.status = { Disabled: false };
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
    });
  });

  describe('filter by starting status', function() {
    it('should filter by starting status if checked', function() {
      var appCopy = _.cloneDeep(applicationJSON);
      var starting = { healthState: 'Unknown'},
        serverGroup = appCopy.serverGroups[0];
      serverGroup.instances.push(starting);

      ClusterFilterModel.sortFilter.status = {Starting: true};
      expect(service.updateClusterGroups(appCopy)).toEqual([]);

      starting.healthState = 'Starting';
      serverGroup.startingCount = 1;
      expect(service.updateClusterGroups(appCopy).length).toBe(1);
      this.verifyTags([
        { key: 'status', label: 'status', value: 'Starting' },
      ]);
    });
  });

  describe('filter by out of service status', function() {
    it('should filter by out of service status if checked', function() {
      var appCopy = _.cloneDeep(applicationJSON);
      var starting = { healthState: 'Unknown' },
        serverGroup = appCopy.serverGroups[0];
      serverGroup.instances.push(starting);

      ClusterFilterModel.sortFilter.status = {OutOfService: true};
      expect(service.updateClusterGroups(appCopy)).toEqual([]);

      starting.healthState = 'OutOfService';
      serverGroup.outOfServiceCount = 1;
      expect(service.updateClusterGroups(appCopy).length).toBe(1);
      this.verifyTags([
        { key: 'status', label: 'status', value: 'Out of Service' },
      ]);
    });
  });

  describe('filtered by provider type', function () {
    it('should filter by aws if checked', function () {
      ClusterFilterModel.sortFilter.providerType = {aws : true};
      var expected = _.filter(groupedJSON,
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
      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'providerType', label: 'provider', value: 'aws' },
      ]);
    });

    it('should not filter if no provider type is selected', function () {
      ClusterFilterModel.sortFilter.providerType = undefined;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
    });

    it('should not filter if all provider are selected', function () {
      ClusterFilterModel.sortFilter.providerType = {aws: true, gce: true};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([
        { key: 'providerType', label: 'provider', value: 'aws' },
        { key: 'providerType', label: 'provider', value: 'gce' },
      ]);
    });
  });

  describe('filtered by instance type', function () {
    it('should filter by m3.large if checked', function () {
      ClusterFilterModel.sortFilter.instanceType = {'m3.large': true};
      var expected = _.filter(groupedJSON,
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
      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
      this.verifyTags([
        { key: 'instanceType', label: 'instance type', value: 'm3.large' },
      ]);
    });

    it('should not filter if no instance type selected', function () {
      ClusterFilterModel.sortFilter.instanceType = undefined;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
    });

    it('should not filter if the instance type is unchecked', function () {
      ClusterFilterModel.sortFilter.instanceType = {'m3.large' : false};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
    });
  });

  describe('filter by instance counts', function () {

    it('should filter by min instances', function () {
      ClusterFilterModel.sortFilter.minInstances = 1;
      var result = service.updateClusterGroups(applicationJSON);
      expect(result.length).toEqual(1);
      expect(result[0]).toEqual(groupedJSON[0]);
      this.verifyTags([
        { key: 'minInstances', label: 'instance count (min)', value: 1 }
      ]);

      ClusterFilterModel.sortFilter.minInstances = 0;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([
        { key: 'minInstances', label: 'instance count (min)', value: 0 }
      ]);

      ClusterFilterModel.sortFilter.minInstances = 2;
      expect(service.updateClusterGroups(applicationJSON)).toEqual([]);
      this.verifyTags([
        { key: 'minInstances', label: 'instance count (min)', value: 2 }
      ]);
    });

    it('should filter by max instances', function() {
      ClusterFilterModel.sortFilter.maxInstances = 0;
      var result = service.updateClusterGroups(applicationJSON);
      expect(result.length).toEqual(1);
      expect(result[0]).toEqual(groupedJSON[1]);
      this.verifyTags([
        { key: 'maxInstances', label: 'instance count (max)', value: 0 }
      ]);

      ClusterFilterModel.sortFilter.maxInstances = 1;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([
        { key: 'maxInstances', label: 'instance count (max)', value: 1 }
      ]);

      ClusterFilterModel.sortFilter.maxInstances = null;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      this.verifyTags([]);
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
      this.serverGroup001 = { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original' };
      this.serverGroup000 = { cluster: 'cluster-a', name: 'cluster-a-v000', account: 'prod', region: 'us-east-1', stringVal: 'should be deleted' };
      ClusterFilterModel.groups = [
        {
          heading: 'prod',
          subgroups: [
            {
              heading: 'cluster-a',
              cluster: { name: 'cluster-a' },
              subgroups: [
                {
                  heading: 'us-east-1',
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

    it('adds a group when new one provided', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'test', region: 'us-east-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(2);
      expect(ClusterFilterModel.groups[1].heading).toBe('test');
      expect(ClusterFilterModel.groups[1].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[1].subgroups[0].heading).toBe('cluster-a');
      expect(ClusterFilterModel.groups[1].subgroups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[1].subgroups[0].subgroups[0].heading).toBe('us-east-1');
      expect(ClusterFilterModel.groups[1].subgroups[0].subgroups[0].serverGroups.length).toBe(1);
      expect(ClusterFilterModel.groups[1].subgroups[0].subgroups[0].serverGroups[0].name).toBe('cluster-a-v003');
    });

    it('adds a subgroup when new one provided', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-b', name: 'cluster-a-v003', account: 'prod', region: 'us-east-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(2);
      expect(ClusterFilterModel.groups[0].subgroups[1].heading).toBe('cluster-b');
      expect(ClusterFilterModel.groups[0].subgroups[1].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[1].subgroups[0].heading).toBe('us-east-1');
      expect(ClusterFilterModel.groups[0].subgroups[1].subgroups[0].serverGroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[1].subgroups[0].serverGroups[0].name).toBe('cluster-a-v003');
    });

    it('adds a sub-subgroup when new one provided', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'prod', region: 'us-west-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(2);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[1].heading).toBe('us-west-1');
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[1].serverGroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[1].serverGroups[0].name).toBe('cluster-a-v003');
    });

    it('adds a server group when new one provided in same sub-sub-group', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'prod', region: 'us-east-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups.length).toBe(3);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[2].name).toBe('cluster-a-v003');
    });

    it('removes a group when one goes away', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'test', region: 'us-east-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(2);

      application.serverGroups.splice(0, 2);
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].heading).toBe('test');
    });

    it('removes a subgroup when one goes away', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-b', name: 'cluster-a-v003', account: 'prod', region: 'us-east-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(2);

      application.serverGroups.splice(0, 2);
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].heading).toBe('cluster-b');
    });

    it('removes a sub-subgroup when one goes away', function() {
      var application = {
        serverGroups: [
          this.serverGroup000,
          this.serverGroup001,
          { cluster: 'cluster-a', name: 'cluster-a-v003', account: 'prod', region: 'us-west-1', stringVal: 'new' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(2);

      application.serverGroups.splice(0, 2);
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].heading).toBe('us-west-1');
    });

    it('removes a server group when one goes away', function() {
      var application = {
        serverGroups: [
          this.serverGroup001,
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups.length).toBe(1);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0].name).toBe('cluster-a-v001');
    });

    it('leaves server groups alone when stringVal does not change', function() {
      var application = {
        serverGroups: [
          { cluster: 'cluster-a', name: 'cluster-a-v000', account: 'prod', region: 'us-east-1', stringVal: 'should be deleted' },
          { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(this.serverGroup000);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[1]).toBe(this.serverGroup001);
    });

    it('replaces server group when stringVal changes', function() {
      var application = {
        serverGroups: [
          { cluster: 'cluster-a', name: 'cluster-a-v000', account: 'prod', region: 'us-east-1', stringVal: 'mutated' },
          { cluster: 'cluster-a', name: 'cluster-a-v001', account: 'prod', region: 'us-east-1', stringVal: 'original' },
        ]
      };
      service.updateClusterGroups(application);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).not.toBe(this.serverGroup000);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(application.serverGroups[0]);
      expect(ClusterFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[1]).toBe(this.serverGroup001);
    });
  });
});
