import { cloneDeep, forOwn, includes, isNil, pick, reduce, size, some } from 'lodash';

import { IFilterConfig, IFilterModel, ITrueKeyModel } from './IFilterModel';
import { ReactInjector } from '../reactShims';

export class FilterModelService {
  public static configureFilterModel(filterModel: IFilterModel, filterModelConfig: IFilterConfig[]) {
    filterModelConfig.forEach((property) => (property.param = property.param || property.model));
    filterModel.config = filterModelConfig;
    filterModel.groups = [];
    filterModel.tags = [];
    filterModel.displayOptions = {};
    filterModel.sortFilter = FilterModelService.mapRouterParamsToSortFilter(filterModel, {});

    filterModel.addTags = () => {
      filterModel.tags = [];
      filterModelConfig
        .filter((property) => !property.displayOption)
        .forEach((property) => this.addTagsForSection(filterModel, property));
    };

    filterModel.clearFilters = () => {
      filterModelConfig.forEach(function (property) {
        if (!property.displayOption) {
          (filterModel.sortFilter[property.model] as any) = property.clearValue;
        }
      });
    };

    // TODO: Remove all calls to activate
    filterModel.activate = () => {};

    // Apply any mutations to the current sortFilter values as ui-router state params
    filterModel.applyParamsToUrl = () => {
      const toParams = FilterModelService.mapSortFilterToRouterParams(filterModel);
      ReactInjector.$state.go('.', toParams);
    };

    return filterModel;
  }

  // Maps the sortFilter data from an IFilterModel object to router params
  public static mapSortFilterToRouterParams(filterModel: IFilterModel) {
    const { sortFilter, config } = filterModel;
    return config.reduce((acc, filter) => ({ ...acc, [filter.param]: sortFilter[filter.model] }), {});
  }

  // Maps router param values to sortFilter values, applying known default values if the parameter is missing
  public static mapRouterParamsToSortFilter(filterModel: IFilterModel, params: any) {
    const filterTypeDefaults: { [key: string]: any } = {
      trueKeyObject: {},
      string: '',
      boolean: false,
      'inverse-boolean': true,
    };

    const iFilterConfigs = filterModel.config;

    return iFilterConfigs.reduce((acc, filter) => {
      const valueIfNil = filterTypeDefaults[filter.type];
      const rawValue = params[filter.param];
      const paramValue = isNil(rawValue) ? valueIfNil : rawValue;
      // Clone deep so angularjs mutations happen on a different object reference
      return { ...acc, [filter.model]: cloneDeep(paramValue) };
    }, {} as any);
  }

  public static registerRouterHooks(filterModel: IFilterModel, stateGlob: string) {
    const { transitionService } = ReactInjector.$uiRouter;
    const filterParams = filterModel.config.map((cfg) => cfg.param);
    let savedParamsForScreen: any = {};

    // When exiting the screen but staying in the app, save the filters for that screen
    transitionService.onSuccess({ exiting: stateGlob, retained: '**.application' }, (trans) => {
      const fromParams = trans.params('from');
      savedParamsForScreen = pick(fromParams, filterParams);
    });

    // When entering the screen and staying in the app, restore the filters for that screen
    transitionService.onBefore({ entering: stateGlob, retained: '**.application' }, (trans) => {
      const toParams = trans.params();
      const hasFilters = filterParams.some((key) => !isNil(toParams[key]));

      const savedParams = savedParamsForScreen;
      const hasSavedFilters = filterParams.some((key) => !isNil(savedParams[key]));

      // Don't restore the saved filters if there are already filters specified (via url, ui-sref, etc)
      const shouldRedirectWithSavedParams = !hasFilters && hasSavedFilters;
      return shouldRedirectWithSavedParams ? trans.targetState().withParams(savedParams) : null;
    });

    // When switching apps, clear the saved state
    transitionService.onStart({ exiting: '**.application' }, () => {
      savedParamsForScreen = {};
    });

    // Map transition param values to sortFilter values and save on the filterModel before each transition
    // In the future, we should remove  the AngularJS code that watches for mutations on the sortFilter object
    transitionService.onBefore({ to: stateGlob }, (trans) => {
      const toParams = trans.params();
      Object.assign(filterModel.sortFilter, FilterModelService.mapRouterParamsToSortFilter(filterModel, toParams));
    });
  }

  public static isFilterable(sortFilterModel: { [key: string]: boolean }): boolean {
    return size(sortFilterModel) > 0 && some(sortFilterModel);
  }

  public static getCheckValues(sortFilterModel: { [key: string]: boolean }) {
    return reduce(
      sortFilterModel,
      function (acc, val, key) {
        if (val) {
          acc.push(key);
        }
        return acc;
      },
      [],
    ).sort();
  }

  public static checkAccountFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.account)) {
        const checkedAccounts = this.getCheckValues(model.sortFilter.account);
        return includes(checkedAccounts, target.account);
      } else {
        return true;
      }
    };
  }

  public static checkRegionFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.region)) {
        const checkedRegions = this.getCheckValues(model.sortFilter.region);
        return includes(checkedRegions, target.region);
      } else {
        return true;
      }
    };
  }

  public static checkStackFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.stack)) {
        const checkedStacks = this.getCheckValues(model.sortFilter.stack);
        if (checkedStacks.includes('(none)')) {
          checkedStacks.push(''); // TODO: remove when moniker is source of truth for naming
          checkedStacks.push(null);
          checkedStacks.push(undefined);
        }
        return includes(checkedStacks, target.stack);
      } else {
        return true;
      }
    };
  }

  public static checkDetailFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.detail)) {
        const checkedDetails = this.getCheckValues(model.sortFilter.detail);
        if (checkedDetails.includes('(none)')) {
          checkedDetails.push(''); // TODO: remove when moniker is source of truth for naming
          checkedDetails.push(null);
          checkedDetails.push(undefined);
        }
        return includes(checkedDetails, target.detail);
      } else {
        return true;
      }
    };
  }

  public static checkStatusFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.status)) {
        const checkedStatus = this.getCheckValues(model.sortFilter.status);
        return (
          (includes(checkedStatus, 'Up') && target.instanceCounts.down === 0) ||
          (includes(checkedStatus, 'Down') && target.instanceCounts.down > 0) ||
          (includes(checkedStatus, 'OutOfService') && target.instanceCounts.outOfService > 0) ||
          (includes(checkedStatus, 'Starting') && target.instanceCounts.starting > 0) ||
          (includes(checkedStatus, 'Disabled') && target.isDisabled) ||
          (includes(checkedStatus, 'Unknown') && target.instanceCounts.unknown > 0)
        );
      }
      return true;
    };
  }

  public static checkProviderFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.providerType)) {
        const checkedProviderTypes = this.getCheckValues(model.sortFilter.providerType);
        return includes(checkedProviderTypes, target.type) || includes(checkedProviderTypes, target.provider);
      } else {
        return true;
      }
    };
  }

  private static addTagsForSection(model: IFilterModel, property: IFilterConfig) {
    const key = property.model;
    const label = property.filterLabel || property.model;
    const translator = property.filterTranslator || {};
    const clearValue = property.clearValue;
    const tags = model.tags;
    const modelVal = model.sortFilter[key];

    if (property.type === 'trueKeyObject') {
      forOwn(modelVal, (isActive, value) => {
        if (isActive) {
          tags.push({
            key,
            label,
            value: translator[value] || value,
            clear() {
              // do not reuse the modelVal variable - it's possible it has been reassigned since the tag was created
              const toClearFrom: ITrueKeyModel = model.sortFilter[key] as ITrueKeyModel;
              delete toClearFrom[value];
              model.applyParamsToUrl();
            },
          });
        }
      });
    } else {
      if (modelVal !== null && modelVal !== undefined && modelVal !== '' && modelVal !== false) {
        tags.push({
          key,
          label,
          value: translator[modelVal as string] || modelVal,
          clear() {
            (model.sortFilter[key] as any) = clearValue;
            model.applyParamsToUrl();
          },
        });
      }
    }
    return tags;
  }
}
