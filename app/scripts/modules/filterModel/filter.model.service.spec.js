'use strict';

describe('Service: FilterModelService', function () {


  var service;
  var $location;
  var $stateParams;
  var searchParams;
  var filterModel;
  var filterModelConfig;

  function configure() {
    service.configureFilterModel(filterModel, filterModelConfig);
    filterModel.activate();

  }

  beforeEach(
    window.module(
      require('./filter.model.service.js')
    )
  );

  beforeEach(
    window.inject(
      function (filterModelService, _$location_, _$stateParams_) {
        service = filterModelService;
        $location = _$location_;
        $stateParams = _$stateParams_;
        spyOn($location, 'search').and.callFake(function(key, val) {
          if (key) {
            searchParams[key] = val;
          } else {
            return searchParams;
          }
        });
        searchParams = {};
        filterModel = {};
        filterModelConfig = [];
      }
    )
  );

  describe('isFilterable', function () {
    it ('returns true if there are any properties with a value of true', function () {
      expect(service.isFilterable(null)).toBe(false);
      expect(service.isFilterable({})).toBe(false);
      expect(service.isFilterable({ a: false })).toBe(false);
      expect(service.isFilterable({ a: false, b: true })).toBe(true);
    });
  });

  describe('getCheckValues', function () {
    it ('returns an array of keys with truthy value', function () {
      expect(service.getCheckValues(null)).toEqual([]);
      expect(service.getCheckValues({})).toEqual([]);
      expect(service.getCheckValues({ a: false })).toEqual([]);
      expect(service.getCheckValues({ a: '' })).toEqual([]);
      expect(service.getCheckValues({ a: null })).toEqual([]);
      expect(service.getCheckValues({ a: undefined })).toEqual([]);
      expect(service.getCheckValues({ a: true, b: false })).toEqual(['a']);
      expect(service.getCheckValues({ a: true, b: true })).toEqual(['a', 'b']);
    });
  });

  describe('checkAccountFilters', function () {
    beforeEach(configure);
    it ('returns true for all items if no accounts selected', function () {
      var items = [ { account: 'test' }, { account: 'prod' }, {}];
      items.forEach(function(item) {
        expect(service.checkAccountFilters(filterModel)(item)).toBe(true);
      });
    });

    it ('returns true for items that have a selected account', function () {
      var items = [ { account: 'test' }, { account: 'test' }];
      filterModel.sortFilter.account = {
        test: true
      };
      items.forEach(function(item) {
        expect(service.checkAccountFilters(filterModel)(item)).toBe(true);
      });
    });

    it ('returns false for items that do not have a selected account', function () {
      var items = [ { account: 'test' }, { account: 'test' }, {}];
      filterModel.sortFilter.account = {
        prod: true
      };
      items.forEach(function(item) {
        expect(service.checkAccountFilters(filterModel)(item)).toBe(false);
      });
    });
  });

  describe('checkRegionFilters', function () {
    beforeEach(configure);
    it ('returns true for all items if no regions selected', function () {
      var items = [ { region: 'us-east-1' }, { region: 'us-west-1' }, {}];
      items.forEach(function(item) {
        expect(service.checkRegionFilters(filterModel)(item)).toBe(true);
      });
    });

    it ('returns true for items that have a selected region', function () {
      var items = [ { region: 'us-east-1' }, { region: 'us-east-1' }];
      filterModel.sortFilter.region = {
        'us-east-1': true
      };
      items.forEach(function(item) {
        expect(service.checkRegionFilters(filterModel)(item)).toBe(true);
      });
    });

    it ('returns false for items that do not have a selected region', function () {
      var items = [ { region: 'us-east-1' }, { region: 'us-east-1' }, {}];
      filterModel.sortFilter.region = {
        'us-west-1': true
      };
      items.forEach(function(item) {
        expect(service.checkRegionFilters(filterModel)(item)).toBe(false);
      });
    });
  });

  describe('checkProviderFilters', function () {
    beforeEach(configure);
    it ('returns true for all items if no providerTypes selected', function () {
      var items = [ { type: 'aws' }, { type: 'gce' }, {}];
      items.forEach(function(item) {
        expect(service.checkProviderFilters(filterModel)(item)).toBe(true);
      });
    });

    it ('returns true for items that have a selected providerType', function () {
      var items = [ { type: 'aws' }, { type: 'aws' }];
      filterModel.sortFilter.providerType = {
        aws: true
      };
      items.forEach(function(item) {
        expect(service.checkProviderFilters(filterModel)(item)).toBe(true);
      });
    });

    it ('returns false for items that do not have a selected providerType', function () {
      var items = [ { type: 'aws' }, { type: 'aws' }, {}];
      filterModel.sortFilter.providerType = {
        gce: true
      };
      items.forEach(function(item) {
        expect(service.checkProviderFilters(filterModel)(item)).toBe(false);
      });
    });
  });

  describe('checkStatusFilters', function () {
    beforeEach(configure);
    it ('returns true if Up is selected and down count is zero', function () {
      var target = { downCount: 0 };
      filterModel.sortFilter.status = { 'Up': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it ('returns false if Up is selected and down count is greater than zero', function () {
      var target = { downCount: 1 };
      filterModel.sortFilter.status = { 'Up': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(false);
    });

    it ('returns true if Down is selected and down count is greater than zero', function () {
      var target = { downCount: 1 };
      filterModel.sortFilter.status = { 'Down': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it ('returns false if Down is selected and down count is zero', function () {
      var target = { downCount: 0 };
      filterModel.sortFilter.status = { 'Down': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(false);
    });

    it ('returns true if OutOfService is selected and out of service count is greater than zero', function () {
      var target = { outOfServiceCount: 1 };
      filterModel.sortFilter.status = { 'OutOfService': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it ('returns true if Starting is selected and starting count is greater than zero', function () {
      var target = { startingCount: 1 };
      filterModel.sortFilter.status = { 'Starting': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it ('returns true if Disabled is selected and target is disabled', function () {
      var target = { isDisabled: true };
      filterModel.sortFilter.status = { 'Disabled': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it ('returns true if any of the above conditions is true', function () {
      var target = { downCount: 1, isDisabled: true };
      filterModel.sortFilter.status = { 'Down': false, 'Disabled': true };
      expect(service.checkStatusFilters(filterModel)(target)).toBe(true);
    });
  });
  
  
  describe('tagging', function () {

    describe('object tags', function () {

      beforeEach(function () {
        filterModelConfig = [
          { model: 'account', type: 'object' },
          { model: 'region', type: 'object' }
        ];
      });

      it ('should only add tags for true values from sortFilter', function () {
        configure();
        filterModel.sortFilter.account.prod = true;
        filterModel.sortFilter.account.test = false;
        filterModel.sortFilter.account.staging = null;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(1);
        expect(tags[0].key).toBe('account');
        expect(tags[0].label).toBe('account');
        expect(tags[0].value).toBe('prod');
      });

      it ('should not add tags when no true values are set on object', function () {
        configure();
        filterModel.sortFilter.account.test = false;
        filterModel.addTags();

        expect(filterModel.tags.length).toBe(0);
      });

      it ('should delete property from sortFilter object when clear called', function () {
        configure();
        filterModel.sortFilter.account.test = true;
        filterModel.addTags();

        expect(filterModel.tags.length).toBe(1);

        filterModel.tags[0].clear();

        expect(filterModel.sortFilter.account.test).toBeUndefined();
      });
    });

    describe('non-object tags', function () {

      beforeEach(function () {
        filterModelConfig = [
          { model: 'search', type: 'string', clearValue: '' },
          { model: 'minCount', type: 'number', clearValue: 0 },
          { model: 'maxCount', type: 'number' },
        ];
      });

      it ('should use clearValue field if provided when removing tag', function () {
        configure();
        filterModel.sortFilter.search = 'v000';
        filterModel.sortFilter.minCount = 2;
        filterModel.sortFilter.maxCount = 2;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(3);

        tags[0].clear();
        tags[1].clear();
        tags[2].clear();

        expect(filterModel.sortFilter.search).toBe('');
        expect(filterModel.sortFilter.minCount).toBe(0);
        expect(filterModel.sortFilter.maxCount).toBeUndefined();

      });

      it ('should create tags for falsy values', function () {
        configure();
        filterModel.sortFilter.minCount = 0;
        filterModel.sortFilter.search = '';
        filterModel.addTags();

        expect(filterModel.tags.length).toBe(1);
      });
    });

    describe('translators', function () {
      beforeEach(function () {
        filterModelConfig = [
          { model: 'status', type: 'object', filterTranslator: { 'up' : 'healthy', 'down' : 'unhealthy' }}
        ];
      });
      it ('should translate value based on supplied translator', function () {
        configure();
        filterModel.sortFilter.status.up = true;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(1);
        expect(tags[0].value).toBe('healthy');
      });
      it ('should default to actual value if translator does not supply a value', function () {
        configure();
        filterModel.sortFilter.status.unknown = true;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(1);
        expect(tags[0].value).toBe('unknown');
      });
    });

    describe('custom labels', function () {
      it ('should use filterLabel property for label if provided; otherwise, use model', function () {
        filterModelConfig = [
          { model: 'availabilityZone', filterLabel: 'availability zone', type: 'object' },
          { model: 'region', type: 'object' },
        ];
        configure();
        filterModel.sortFilter.availabilityZone['us-east-1c'] = true;
        filterModel.sortFilter.region['us-east-1'] = true;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(2);
        expect(tags[0].label).toBe('availability zone');
        expect(tags[1].label).toBe('region');
      });
    });

    describe('tag order', function () {
      it ('should create tags in the order they are defined in config', function () {
        filterModelConfig = [
          { model: 'availabilityZone', filterLabel: 'availability zone', type: 'object' },
          { model: 'region', type: 'object' },
          { model: 'account', type: 'object' }
        ];
        configure();
        filterModel.sortFilter.account.prod = true;
        filterModel.sortFilter.availabilityZone['us-east-1c'] = true;
        filterModel.sortFilter.region['us-east-1'] = true;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(3);
        expect(tags[0].label).toBe('availability zone');
        expect(tags[1].label).toBe('region');
        expect(tags[2].label).toBe('account');
      });
    });
  });

  describe('saving state', function () {
    var params;
    beforeEach(function () {
      filterModelConfig = [
        { model: 'region', type: 'object' },
        { model: 'account', type: 'object' }
      ];
      params = {application: 'deck'};
    });
    it ('should set filters based on location.search', function () {
      $location.search('region', 'us-east-1,us-west-1');
      $location.search('account', 'prod');
      configure();

      filterModel.saveState('application.clusters', params);
      var savedState = filterModel.savedState['deck'];

      expect(savedState.filters).toEqual({ region: 'us-east-1,us-west-1', account: 'prod' });
      expect(savedState.state).toEqual('application.clusters');
      expect(savedState.params).toBe(params);
    });

    it ('should overwrite existing saved state', function () {
      $location.search('region', 'us-east-1,us-west-1');
      $location.search('account', 'prod');
      configure();

      filterModel.saveState('application.clusters', params);
      var savedState = filterModel.savedState;

      expect(savedState.deck.filters).toEqual({ region: 'us-east-1,us-west-1', account: 'prod' });
      expect(savedState.deck.state).toEqual('application.clusters');
      expect(savedState.deck.params).toBe(params);

      $location.search('account', 'test');
      delete searchParams.region;

      var newParams = { application: 'deck', cluster: 'deck-main' };
      filterModel.saveState('application.cluster', newParams);

      expect(savedState.deck.filters).toEqual({ account: 'test' });
      expect(savedState.deck.state).toEqual('application.cluster');
      expect(savedState.deck.params).toBe(newParams);

    });
  });

  describe('restore state', function () {

    beforeEach(function () {
      filterModelConfig = [
        { model: 'account', type: 'object' }
      ];
    });

    it ('should do nothing if no state saved for application', function () {
      configure();
      expect($location.search.calls.count()).toBe(1);

      filterModel.restoreState({ application: 'deck' });

      expect($location.search.calls.count()).toBe(1);
    });

    it ('should overwrite any $stateParams with those in saved state', function () {
      configure();
      filterModel.saveState('application.clusters', { application: 'deck', cluster: 'deck-prestaging', region: 'us-west-1' });
      $stateParams.application = 'deck';
      $stateParams.account = 'prod';
      $stateParams.cluster = 'deck-main';
      filterModel.restoreState({ application: 'deck' });

      expect($stateParams.application).toBe('deck');
      expect($stateParams.account).toBeUndefined();
      expect($stateParams.region).toBe('us-west-1');
    });

    it ('should remove current params if they are configured for model but not saved', function () {
      configure();
      $location.search('account', 'prod');
      filterModel.savedState.deck = {
        filters: {}
      };
      filterModel.restoreState({ application: 'deck' });
      expect($location.search('account')).toBeUndefined();
    });

    describe('default param naming', function () {
      it ('should default param value to model if not supplied', function () {
        filterModelConfig = [
          { model: 'search', type: 'string' },
          { model: 'aliased', param: 'alias', type: 'string' },
        ];
        configure();
        expect(filterModelConfig[0].param).toBe('search');
        expect(filterModelConfig[1].param).toBe('alias');
      });
    });

    describe('clear filters', function () {
      it ('should remove any values that are not displayOptions', function () {
        filterModelConfig = [
          { model: 'account', type: 'object' },
          { model: 'showInstances', type: 'string', displayOption: true },
        ];
        configure();
        filterModel.sortFilter.account.prod = true;
        filterModel.displayOptions.showInstances = true;

        filterModel.clearFilters();

        expect(filterModel.sortFilter.account).toBeUndefined();
        expect(filterModel.displayOptions.showInstances).toBe(true);
      });

      it ('should use a clearValue if supplied', function () {
        filterModelConfig = [
          { model: 'account', type: 'object' },
          { model: 'search', type: 'string', clearValue: '' },
        ];
        configure();

        filterModel.sortFilter.account.prod = true;
        filterModel.sortFilter.search = 'deck';

        filterModel.clearFilters();

        expect(filterModel.sortFilter.account).toBeUndefined();
        expect(filterModel.sortFilter.search).toBe('');
      });
    });

    describe('applyParamsToUrl', function () {
      it ('should set params on all configured fields', function () {
        filterModelConfig = [
          { model: 'showInstances', type: 'boolean', displayOption: true },
          { model: 'search', type: 'string'}
        ];
        configure();
        filterModel.sortFilter.search = 'deck';
        filterModel.sortFilter.showInstances = true;

        filterModel.applyParamsToUrl();
        expect(searchParams.search).toBe('deck');
        expect(searchParams.showInstances).toBe('true');
      });

      it ('should set objects based on true keys as comma-separate list of keys, sorted alphabetically', function () {
        filterModelConfig = [
          { model: 'account', type: 'object' },
          { model: 'region', type: 'object'}
        ];
        configure();
        filterModel.sortFilter.account.prod = true;
        filterModel.sortFilter.account.test = true;
        filterModel.sortFilter.account.management = true;
        filterModel.sortFilter.region['us-east-1'] = true;

        filterModel.applyParamsToUrl();
        expect(searchParams.account).toBe('management,prod,test');
        expect(searchParams.region).toBe('us-east-1');
      });

      it ('should set numeric values, including zero', function () {
        filterModelConfig = [
          { model: 'min', type: 'number' },
          { model: 'max', type: 'number'}
        ];
        configure();
        filterModel.sortFilter.min = 0;
        filterModel.sortFilter.max = 3;

        filterModel.applyParamsToUrl();
        expect(searchParams.min).toBe(0);
        expect(searchParams.max).toBe(3);
      });

      it ('should not set numeric fields if they are not numbers', function () {
        filterModelConfig = [
          { model: 'min', type: 'number' },
        ];
        configure();
        filterModel.sortFilter.min = 'boo';

        filterModel.applyParamsToUrl();
        expect(searchParams.min).toBeNull();
      });

      it ('should not set an empty string', function () {
        filterModelConfig = [
          { model: 'search', type: 'string' },
        ];
        configure();
        filterModel.sortFilter.search = '';

        filterModel.applyParamsToUrl();
        expect(searchParams.search).toBeNull();
      });

      it ('should set sortKey fields based on key value', function () {
        filterModelConfig = [
          { model: 'instanceSort', type: 'sortKey' },
        ];
        configure();
        filterModel.sortFilter.instanceSort = { key: '-launchTime' };

        filterModel.applyParamsToUrl();
        expect(searchParams.instanceSort).toBe('-launchTime');
      });

      it ('should only set boolean values if they are true', function () {
        filterModelConfig = [
          { model: 'listInstances', type: 'boolean' },
        ];
        configure();
        filterModel.sortFilter.listInstances = false;

        filterModel.applyParamsToUrl();
        expect(searchParams.listInstances).toBeNull();

        filterModel.sortFilter.listInstances = true;

        filterModel.applyParamsToUrl();
        expect(searchParams.listInstances).toBe('true');
      });

      it ('should only set inverse-boolean values if they are false', function () {
        filterModelConfig = [
          { model: 'hideInstances', type: 'inverse-boolean' },
        ];
        configure();
        filterModel.sortFilter.hideInstances = false;

        filterModel.applyParamsToUrl();
        expect(searchParams.hideInstances).toBe('true');

        filterModel.sortFilter.hideInstances = true;

        filterModel.applyParamsToUrl();
        expect(searchParams.hideInstances).toBeNull();
      });
    });

    describe('activate', function () {
      it ('should set objects by splitting parameter on comma', function () {
        filterModelConfig = [
          { model: 'account', type: 'object' },
        ];
        $location.search('account', 'prod,test');
        configure();

        expect(filterModel.sortFilter.account).toEqual({prod: true, test: true});
      });

      it ('should set numeric values, including zero', function () {
        filterModelConfig = [
          { model: 'min', type: 'number' },
        ];
        $location.search('min', '3');
        configure();

        expect(filterModel.sortFilter.min).toBe(3);
      });

      it ('should not set numeric fields if they are not numbers', function () {
        filterModelConfig = [
          { model: 'min', type: 'number' },
        ];
        $location.search('min', 'foo');
        configure();

        expect(filterModel.sortFilter.min).toBe(null);
      });

      it ('should set an empty string if no value present', function () {
        filterModelConfig = [
          { model: 'search', type: 'string' },
        ];
        configure();

        expect(filterModel.sortFilter.search).toBe('');
      });

      it ('should set sortKey key fields based on value', function () {
        filterModelConfig = [
          { model: 'instanceSort', type: 'sortKey' },
        ];
        $location.search('instanceSort', 'zone');
        configure();

        expect(filterModel.sortFilter.instanceSort.key).toBe('zone');
      });

      it ('should set to default value if provided', function () {
        filterModelConfig = [
          { model: 'instanceSort', type: 'sortKey', defaultValue: 'launchTime' },
        ];
        configure();

        expect(filterModel.sortFilter.instanceSort.key).toBe('launchTime');
      });

      it ('should set boolean values', function () {
        filterModelConfig = [
          { model: 'showInstances', type: 'boolean' },
        ];
        $location.search('showInstances', 'true');
        configure();

        expect(filterModel.sortFilter.showInstances).toBe(true);
      });

      it ('should set inverse-boolean values if not in params', function () {
        filterModelConfig = [
          { model: 'hideInstances', type: 'inverse-boolean' },
        ];
        configure();

        expect(filterModel.sortFilter.hideInstances).toBe(true);
      });
    });
  });

});
