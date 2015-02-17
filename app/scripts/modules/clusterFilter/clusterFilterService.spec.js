'use strict';

describe('Service: clusterFilterService', function () {

  var service;
  var $location;
  var ClusterFilterModel;
  var applicationJSON;
  var groupedJSON;

  beforeEach(module('cluster.filter.service', 'cluster.test.data'));
  beforeEach(
    inject(
      function (_$location_, clusterFilterService, _ClusterFilterModel_) {
        service = clusterFilterService;
        $location = _$location_;
        ClusterFilterModel = _ClusterFilterModel_;
      }
    )
  );

  beforeEach(
    inject(
      function (_applicationJSON_, _groupedJSON_) {
        applicationJSON = _applicationJSON_;
        groupedJSON = _groupedJSON_;
      }
    )
  );

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
        expect($location.search().reg).toBeUndefined()
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
          expect($location.search().providerType).toBeUndefined()
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

  });


  describe('Updating the cluster group', function () {

    it('no filter: should be transformed', function () {
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function () {
        ClusterFilterModel.sortFilter.account = {prod: true};
        var expectedProd = _.filter(groupedJSON, {heading:'prod'});
        expect(service.updateClusterGroups(applicationJSON)).toEqual(expectedProd);
      });

      it('All account filters: should show all accounts', function () {
        ClusterFilterModel.sortFilter.account = {prod: true, test: true};
        expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region ', function () {
      ClusterFilterModel.sortFilter.region = {'us-west-1' : true};
      var expected = _.filter(groupedJSON, {subgroups: [{heading: 'in-us-west-1-only' }]});
      expect(service.updateClusterGroups(applicationJSON)).toEqual(expected);
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
    });

    it('should not filter by healthy if unchecked', function () {
      ClusterFilterModel.sortFilter.status = {healthy : false};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
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
    });

    it('should not filter by unhealthy if unchecked', function () {
      ClusterFilterModel.sortFilter.status = {unhealthy : false};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
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
    });
  });

  xdescribe('filter by disabled status', function () {
    it('should filter by disabled status if checked', function () {
      ClusterFilterModel.sortFilter.status = {disabled: true};
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
    });

    it('should not filter if the status is unchecked', function () {
      ClusterFilterModel.sortFilter.status = { disabled: false };
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
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
    });

    it('should not filter if no provider type is selected', function () {
      ClusterFilterModel.sortFilter.providerType = undefined;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
    });

    it('should not filter if all provider are selected', function () {
      ClusterFilterModel.sortFilter.providerType = {aws: true, gce: true};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
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
    });

    it('should not filter if no instance type selected', function () {
      ClusterFilterModel.sortFilter.instanceType = undefined;
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
    });

    it('should not filter if the instance type is unchecked', function () {
      ClusterFilterModel.sortFilter.instanceType = {'m3.large' : false};
      expect(service.updateClusterGroups(applicationJSON)).toEqual(groupedJSON);
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
    });

  });
});
