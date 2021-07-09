'use strict';
import { FilterModelService } from './FilterModelService';
import { REACT_MODULE, ReactInjector } from '../reactShims';
import { StateConfigProvider } from '../navigation';

describe('Service: FilterModelService', function () {
  var filterModel;
  var filterModelConfig;
  var $uiRouter;
  var $rootScope;

  function configure() {
    FilterModelService.configureFilterModel(filterModel, filterModelConfig);
    filterModel.activate();
  }

  beforeEach(window.module('ui.router', REACT_MODULE));

  beforeEach(
    window.inject(function (_$uiRouter_, _$rootScope_) {
      filterModel = {};
      filterModelConfig = [];
      $uiRouter = _$uiRouter_;
      $rootScope = _$rootScope_;
    }),
  );

  describe('isFilterable', function () {
    it('returns true if there are any properties with a value of true', function () {
      expect(FilterModelService.isFilterable(null)).toBe(false);
      expect(FilterModelService.isFilterable({})).toBe(false);
      expect(FilterModelService.isFilterable({ a: false })).toBe(false);
      expect(FilterModelService.isFilterable({ a: false, b: true })).toBe(true);
    });
  });

  describe('getCheckValues', function () {
    it('returns an array of keys with truthy value', function () {
      expect(FilterModelService.getCheckValues(null)).toEqual([]);
      expect(FilterModelService.getCheckValues({})).toEqual([]);
      expect(FilterModelService.getCheckValues({ a: false })).toEqual([]);
      expect(FilterModelService.getCheckValues({ a: '' })).toEqual([]);
      expect(FilterModelService.getCheckValues({ a: null })).toEqual([]);
      expect(FilterModelService.getCheckValues({ a: undefined })).toEqual([]);
      expect(FilterModelService.getCheckValues({ a: true, b: false })).toEqual(['a']);
      expect(FilterModelService.getCheckValues({ a: true, b: true })).toEqual(['a', 'b']);
    });
  });

  describe('checkAccountFilters', function () {
    beforeEach(configure);
    it('returns true for all items if no accounts selected', function () {
      var items = [{ account: 'test' }, { account: 'prod' }, {}];
      items.forEach(function (item) {
        expect(FilterModelService.checkAccountFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns true for items that have a selected account', function () {
      var items = [{ account: 'test' }, { account: 'test' }];
      filterModel.sortFilter.account = {
        test: true,
      };
      items.forEach(function (item) {
        expect(FilterModelService.checkAccountFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns false for items that do not have a selected account', function () {
      var items = [{ account: 'test' }, { account: 'test' }, {}];
      filterModel.sortFilter.account = {
        prod: true,
      };
      items.forEach(function (item) {
        expect(FilterModelService.checkAccountFilters(filterModel)(item)).toBe(false);
      });
    });
  });

  describe('checkStackFilters', function () {
    beforeEach(configure);
    it('returns true for all items if no stacks selected', function () {
      var items = [{ stack: 'a' }, { stack: 'b' }, {}];
      items.forEach(function (item) {
        expect(FilterModelService.checkStackFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns true for items that have a selected stack', function () {
      var items = [{ stack: 'a' }, { stack: 'b' }, {}];
      filterModel.sortFilter.stack = {
        a: true,
      };
      expect(FilterModelService.checkStackFilters(filterModel)(items[0])).toBe(true);
      expect(FilterModelService.checkStackFilters(filterModel)(items[1])).toBe(false);
      expect(FilterModelService.checkStackFilters(filterModel)(items[2])).toBe(false);
    });

    it('includes items without a stack if (none) is selected', function () {
      var items = [{ stack: 'a' }, { stack: 'b' }, { stack: '' }];
      filterModel.sortFilter.stack = {
        a: true,
        '(none)': true,
      };
      expect(FilterModelService.checkStackFilters(filterModel)(items[0])).toBe(true);
      expect(FilterModelService.checkStackFilters(filterModel)(items[1])).toBe(false);
      expect(FilterModelService.checkStackFilters(filterModel)(items[2])).toBe(true);
    });
  });

  describe('checkRegionFilters', function () {
    beforeEach(configure);
    it('returns true for all items if no regions selected', function () {
      var items = [{ region: 'us-east-1' }, { region: 'us-west-1' }, {}];
      items.forEach(function (item) {
        expect(FilterModelService.checkRegionFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns true for items that have a selected region', function () {
      var items = [{ region: 'us-east-1' }, { region: 'us-east-1' }];
      filterModel.sortFilter.region = {
        'us-east-1': true,
      };
      items.forEach(function (item) {
        expect(FilterModelService.checkRegionFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns false for items that do not have a selected region', function () {
      var items = [{ region: 'us-east-1' }, { region: 'us-east-1' }, {}];
      filterModel.sortFilter.region = {
        'us-west-1': true,
      };
      items.forEach(function (item) {
        expect(FilterModelService.checkRegionFilters(filterModel)(item)).toBe(false);
      });
    });
  });

  describe('checkProviderFilters', function () {
    beforeEach(configure);
    it('returns true for all items if no providerTypes selected', function () {
      var items = [{ type: 'aws' }, { type: 'gce' }, {}];
      items.forEach(function (item) {
        expect(FilterModelService.checkProviderFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns true for items that have a selected providerType', function () {
      var items = [{ type: 'aws' }, { type: 'aws' }];
      filterModel.sortFilter.providerType = {
        aws: true,
      };
      items.forEach(function (item) {
        expect(FilterModelService.checkProviderFilters(filterModel)(item)).toBe(true);
      });
    });

    it('returns false for items that do not have a selected providerType', function () {
      var items = [{ type: 'aws' }, { type: 'aws' }, {}];
      filterModel.sortFilter.providerType = {
        gce: true,
      };
      items.forEach(function (item) {
        expect(FilterModelService.checkProviderFilters(filterModel)(item)).toBe(false);
      });
    });
  });

  describe('checkStatusFilters', function () {
    beforeEach(configure);
    it('returns true if Up is selected and down count is zero', function () {
      var target = { instanceCounts: { down: 0 } };
      filterModel.sortFilter.status = { Up: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it('returns false if Up is selected and down count is greater than zero', function () {
      var target = { instanceCounts: { down: 1 } };
      filterModel.sortFilter.status = { Up: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(false);
    });

    it('returns true if Down is selected and down count is greater than zero', function () {
      var target = { instanceCounts: { down: 1 } };
      filterModel.sortFilter.status = { Down: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it('returns false if Down is selected and down count is zero', function () {
      var target = { instanceCounts: { down: 0 } };
      filterModel.sortFilter.status = { Down: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(false);
    });

    it('returns true if OutOfService is selected and out of service count is greater than zero', function () {
      var target = { instanceCounts: { outOfService: 1 } };
      filterModel.sortFilter.status = { OutOfService: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it('returns true if Starting is selected and starting count is greater than zero', function () {
      var target = { instanceCounts: { starting: 1 } };
      filterModel.sortFilter.status = { Starting: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it('returns true if Disabled is selected and target is disabled', function () {
      var target = { instanceCounts: { down: 1 }, isDisabled: true };
      filterModel.sortFilter.status = { Disabled: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(true);
    });

    it('returns true if any of the above conditions is true', function () {
      var target = { instanceCounts: { down: 1 }, isDisabled: true };
      filterModel.sortFilter.status = { Down: false, Disabled: true };
      expect(FilterModelService.checkStatusFilters(filterModel)(target)).toBe(true);
    });
  });

  describe('tagging', function () {
    describe('object tags', function () {
      beforeEach(function () {
        filterModelConfig = [
          { model: 'account', type: 'trueKeyObject' },
          { model: 'region', type: 'trueKeyObject' },
        ];
      });

      it('should only add tags for true values from sortFilter', function () {
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

      it('should not add tags when no true values are set on object', function () {
        configure();
        filterModel.sortFilter.account.test = false;
        filterModel.addTags();

        expect(filterModel.tags.length).toBe(0);
      });

      it('should delete property from sortFilter object when clear called', function () {
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
          { model: 'minCount', type: 'int', clearValue: 0 },
          { model: 'maxCount', type: 'int' },
        ];
      });

      it('should use clearValue field if provided when removing tag', function () {
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

      it('should create tags for falsy values', function () {
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
          { model: 'status', type: 'trueKeyObject', filterTranslator: { up: 'healthy', down: 'unhealthy' } },
        ];
      });
      it('should translate value based on supplied translator', function () {
        configure();
        filterModel.sortFilter.status.up = true;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(1);
        expect(tags[0].value).toBe('healthy');
      });
      it('should default to actual value if translator does not supply a value', function () {
        configure();
        filterModel.sortFilter.status.unknown = true;
        filterModel.addTags();

        var tags = filterModel.tags;
        expect(tags.length).toBe(1);
        expect(tags[0].value).toBe('unknown');
      });
    });

    describe('custom labels', function () {
      it('should use filterLabel property for label if provided; otherwise, use model', function () {
        filterModelConfig = [
          { model: 'availabilityZone', filterLabel: 'availability zone', type: 'trueKeyObject' },
          { model: 'region', type: 'trueKeyObject' },
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
      it('should create tags in the order they are defined in config', function () {
        filterModelConfig = [
          { model: 'availabilityZone', filterLabel: 'availability zone', type: 'trueKeyObject' },
          { model: 'region', type: 'trueKeyObject' },
          { model: 'account', type: 'trueKeyObject' },
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

  describe('parameter router hooks', function () {
    function go(state, params) {
      $uiRouter.stateService.go(state, params);
      $rootScope.$digest();
    }

    beforeEach(function () {
      filterModelConfig = [
        { model: 'region', type: 'string' },
        { model: 'account', type: 'string' },
      ];
      configure();

      $uiRouter.stateRegistry.register({ name: 'other' });
      $uiRouter.stateRegistry.register({ name: 'application', url: '/applications/:application' });
      $uiRouter.stateRegistry.register({
        name: 'application.filtered',
        url: '/filter?region&account',
        params: new StateConfigProvider().buildDynamicParams(filterModelConfig),
      });
      $uiRouter.stateRegistry.register({ name: 'application.otherchild' });

      FilterModelService.registerRouterHooks(filterModel, 'application.filtered');
    });

    it('should restore the latest filters when reactivating a filter state in the same application', function () {
      go('application.filtered', { application: 'myapp', region: 'west', account: 'prod' });
      expect($uiRouter.globals.params.region).toBe('west');
      expect($uiRouter.globals.params.account).toBe('prod');

      go('application.otherchild');
      expect($uiRouter.globals.params.region).toBe(undefined);
      expect($uiRouter.globals.params.account).toBe(undefined);

      go('application.filtered');
      expect($uiRouter.globals.params.region).toBe('west');
      expect($uiRouter.globals.params.account).toBe('prod');
    });

    it('should not restore the latest filters when switching apps', function () {
      go('application.filtered', { application: 'foo', region: 'west', account: 'prod' });
      expect($uiRouter.globals.params.region).toBe('west');
      expect($uiRouter.globals.params.account).toBe('prod');

      go('application.otherchild');
      expect($uiRouter.globals.params.region).toBe(undefined);
      expect($uiRouter.globals.params.account).toBe(undefined);

      go('application.filtered', { application: 'otherapp' });
      expect($uiRouter.globals.params.region).toBe(undefined);
      expect($uiRouter.globals.params.account).toBe(undefined);
    });
  });

  describe('restore state', function () {
    beforeEach(function () {
      filterModelConfig = [{ model: 'account', type: 'trueKeyObject' }];
    });

    describe('default param naming', function () {
      it('should default param value to model if not supplied', function () {
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
      it('should remove any values that are not displayOptions', function () {
        filterModelConfig = [
          { model: 'account', type: 'trueKeyObject' },
          { model: 'showInstances', type: 'string', displayOption: true },
        ];
        configure();
        filterModel.sortFilter.account.prod = true;
        filterModel.displayOptions.showInstances = true;

        filterModel.clearFilters();

        expect(filterModel.sortFilter.account).toBeUndefined();
        expect(filterModel.displayOptions.showInstances).toBe(true);
      });

      it('should use a clearValue if supplied', function () {
        filterModelConfig = [
          { model: 'account', type: 'trueKeyObject' },
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
      it('should start a transition with params for all configured fields', function () {
        const spy = spyOn(ReactInjector.$state, 'go');
        filterModelConfig = [
          { model: 'showInstances', type: 'boolean', displayOption: true },
          { model: 'search', type: 'string', param: 'q' },
        ];
        configure();
        filterModel.sortFilter.search = 'deck';
        filterModel.sortFilter.showInstances = true;
        filterModel.applyParamsToUrl();
        expect(spy).toHaveBeenCalledWith('.', { q: 'deck', showInstances: true });
      });
    });
  });
});
