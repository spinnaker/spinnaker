'use strict';

describe('Model: ClusterFilterModel', function () {


  var ClusterFilterModel;
  var $location;
  var searchParams;

  beforeEach(module('cluster.filter.model'));
  beforeEach(
    inject(
      function (_ClusterFilterModel_, _$location_) {
        ClusterFilterModel = _ClusterFilterModel_;
        $location = _$location_;
        spyOn($location, 'search').and.callFake(function(key, val) {
          if (key) {
            searchParams[key] = val;
          } else {
            return searchParams;
          }
        });
        searchParams = {};
      }
    )
  );

  it('should nave an empty account model if there are no query string for account', function () {
    expect(ClusterFilterModel.sortFilter.account).toBeUndefined();
  });


  describe('account filtering scenarios', function () {
    describe('zero accounts in the query string', function () {
      it('should set nothing to the model', function () {
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.account).toBeUndefined();
      });
    });

    describe('only one account in the query string', function () {
      it('should set the prod account to the model', function () {
        searchParams.acct = 'prod';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.account).toEqual({'prod' : true});
      });
    });

    describe('multiple accounts in the query string', function () {
      it('should set the prod account to the model', function () {
        searchParams.acct = 'prod,test';
        ClusterFilterModel.activate()
        expect(ClusterFilterModel.sortFilter.account).toEqual({'prod' : true, test: true});
      });
    });
  });

  describe('region filtering scenarios', function () {
    describe('zero regions in the query string', function () {
      it('should set nothing to the model', function () {
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.region).toBeUndefined();
      });
    });

    describe('one regions in the query string', function () {
      it('should set the us-west-1 region on the model', function () {
        searchParams.reg = 'us-west-1';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.region).toEqual({'us-west-1' : true})
      });
    });

    describe('multiple regions in the query string', function () {
      it('should set the all the regions region on the model', function () {
        searchParams.reg = 'us-west-1,eu-west-1,us-east-2';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.region).toEqual({'us-west-1' : true, 'eu-west-1': true, 'us-east-2': true})
      });
    });
  });


  describe('status filters', function () {

    beforeEach(function () {
      searchParams.status = undefined;
    });

    describe('status healthy server groups', function () {
      describe('healthy as status on query string', function () {
        it('should set the healthy status on the model', function () {
          searchParams.status = 'healthy';
          ClusterFilterModel.activate();
          expect(ClusterFilterModel.sortFilter.status).toEqual({'healthy': true});
        });
      });

      describe('healthy not on the query string', function () {
        it('should NOT set the healthy status on the model', function () {
          ClusterFilterModel.activate();
          expect(ClusterFilterModel.sortFilter.status).toBeUndefined();
        });
      });
    });


    describe('status: unhealthy server groups', function () {
      describe('healthy as status on query string', function () {
        it('should set the unhealthy status on the model', function () {
          searchParams.status = 'unhealthy';
          ClusterFilterModel.activate();
          expect(ClusterFilterModel.sortFilter.status).toEqual({'unhealthy': true});
        });
      });

      describe('healthy not on the query string', function () {
        it('should NOT set the unhealthy status on the model', function () {
          ClusterFilterModel.activate();
          expect(ClusterFilterModel.sortFilter.status).toBeUndefined();
        });
      });
    });

    describe('status: disabled ', function () {
      it('should set the disabled status on the model', function () {
        searchParams.status = 'disabled';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.status).toEqual({disabled: true});
      });
    });

    describe('status: unknown', function () {
      it('should set the unknown status on the model', function () {
        searchParams.status = 'unknown';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.status).toEqual({unknown: true});
      });
    });
  });

  describe('provider types', function () {
    beforeEach(function () {
      searchParams.providerType = undefined;
    });

    describe('setting aws', function () {
      it('should set the provider type of aws on the model', function () {
        searchParams.providerType = 'aws';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.providerType).toEqual({aws: true});
      });

      it('should not set the provider type if nothing is selected', function () {
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.providerType).toBeUndefined();
      });
    });

    describe('setting multiple provider types', function () {
      it('should set all selected provider types on the model', function () {
        searchParams.providerType = 'aws,gce';
        ClusterFilterModel.activate();
        expect(ClusterFilterModel.sortFilter.providerType).toEqual({aws: true, gce: true});
      });
    });
  });


  describe('instance types', function () {
    beforeEach(function () {
      searchParams.instanceType = undefined;
    });

    it('should set the instance type of m3.large on the model', function () {
      searchParams.instanceType = 'm3.large';
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.instanceType).toEqual({'m3.large': true});
    });

    it('should not set the instance name if nothing is selected', function () {
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.instanceType).toBeUndefined();
    });

    it('should set multiple instance types on the model', function () {
      searchParams.instanceType = 'm3.large,m3.medium';
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.instanceType).toEqual({'m3.large': true, 'm3.medium': true});
    });
  });


  describe('activate sets the sortFilter state', function () {

    it('should set the filter to an empty string if nothing has been typed', function () {
      expect(ClusterFilterModel.sortFilter.filter).toBe('');
    });

    it('should set the filter to what is on the query string', function () {
      searchParams.q = 'us-west';
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.filter).toBe('us-west');
    });

    it('should show all the instances if the param is NOT on the query string', function () {
      expect(ClusterFilterModel.sortFilter.showAllInstances).toBe(true);
    });

    it('should NOT show all the instances if the param IS on the query string', function () {
      searchParams.hideInstances  = true
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.showAllInstances).toBe(false);
    });

    it('should hide the health clusters if the param is on the query string', function () {
      searchParams.hideHealthy = true;
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.hideHealthy).toBe(true);
    });

    it('should show the healthy clustes if the param is NOT on the query string', function () {
      searchParams.hideHealthy = false;
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.hideHealthy).toBe(false);
    });

    it('should not hide the disabled if the param is not on the query string', function () {
      expect(ClusterFilterModel.sortFilter.hideDisabled).toBe(false);
    });

    it('should hide the disabled if the param is on the query string', function () {
      searchParams.hideDisabled = true;
      ClusterFilterModel.activate();
      expect(ClusterFilterModel.sortFilter.hideDisabled).toBe(true);
    });


  });



});
