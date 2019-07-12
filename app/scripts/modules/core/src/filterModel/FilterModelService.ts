import { StateParams } from '@uirouter/core';
import { cloneDeep, size, some, isNil, reduce, forOwn, includes, chain, pick } from 'lodash';
import { $location } from 'ngimport';

import { IFilterModel, IFilterConfig, ISortFilter } from './IFilterModel';
import { ReactInjector } from 'core/reactShims';

export interface IParamConverter {
  toParam: (filterModel: IFilterModel, property: IFilterConfig) => any;
  toModel: (filterModel: IFilterModel, property: IFilterConfig) => any;
}

export class FilterModelServiceConverters {
  public trueKeyObject: IParamConverter = {
    toParam: (filterModel: IFilterModel, property: IFilterConfig) => {
      const obj = filterModel.sortFilter[property.model];
      return (
        chain(obj || {})
          .map(function(val: any, key: string) {
            if (val) {
              // replace any commas in the string with their uri-encoded version ('%2c'), since
              // we use commas as our separator in the URL
              return key.replace(/,/g, '%2c');
            }
            return undefined;
          })
          .remove(undefined)
          .value()
          .sort()
          .join(',') || null
      );
    },
    toModel: (_filterModel: IFilterModel, property: IFilterConfig) => {
      const paramList = this.getParamVal(property);
      if (paramList) {
        return reduce(
          paramList.split(','),
          function(acc, value: string) {
            // replace any uri-encoded commas in the string ('%2c') with actual commas, since
            // we use commas as our separator in the URL
            acc[value.replace(/%2c/g, ',')] = true;
            return acc;
          },
          {} as { [key: string]: boolean },
        );
      } else {
        return {};
      }
    },
  };

  public string: IParamConverter = {
    toParam: (filterModel: IFilterModel, property: IFilterConfig) => {
      const val = filterModel.sortFilter[property.model];
      return val && val !== property.defaultValue ? val : null;
    },
    toModel: (_filterModel: IFilterModel, property: IFilterConfig) => {
      const val = this.getParamVal(property);
      return val ? val : '';
    },
  };

  public boolean: IParamConverter = {
    toParam: (filterModel: IFilterModel, property: IFilterConfig) => {
      const val = filterModel.sortFilter[property.model];
      return val ? val.toString() : null;
    },
    toModel: (_filterModel: IFilterModel, property: IFilterConfig) => {
      const val = this.getParamVal(property);
      return Boolean(val);
    },
  };

  public 'inverse-boolean': IParamConverter = {
    toParam: (filterModel: IFilterModel, property: IFilterConfig) => {
      const val = filterModel.sortFilter[property.model];
      return val ? null : 'true';
    },
    toModel: (_filterModel: IFilterModel, property: IFilterConfig) => {
      const val = this.getParamVal(property);
      return !val;
    },
  };

  public int: IParamConverter = {
    toParam: (filterModel: IFilterModel, property: IFilterConfig) => {
      const val: any = filterModel.sortFilter[property.model];
      return isNaN(val) ? null : property.defaultValue === val ? null : val;
    },
    toModel: (_filterModel: IFilterModel, property: IFilterConfig) => {
      const val = this.getParamVal(property);
      return isNaN(val) ? null : Number(val);
    },
  };

  private getParamVal(property: IFilterConfig) {
    return $location.search()[property.param] || property.defaultValue;
  }
}

export class FilterModelService {
  private static converters = new FilterModelServiceConverters();

  public static configureFilterModel(filterModel: IFilterModel, filterModelConfig: IFilterConfig[]) {
    const { converters } = this;

    filterModelConfig.forEach(property => (property.param = property.param || property.model));
    filterModel.config = filterModelConfig;
    filterModel.groups = [];
    filterModel.tags = [];
    filterModel.displayOptions = {};
    filterModel.sortFilter = {} as ISortFilter;

    filterModel.addTags = () => {
      filterModel.tags = [];
      filterModelConfig
        .filter(property => !property.displayOption)
        .forEach(property => this.addTagsForSection(filterModel, property));
    };

    filterModel.clearFilters = () => {
      filterModelConfig.forEach(function(property) {
        if (!property.displayOption) {
          filterModel.sortFilter[property.model] = property.clearValue;
        }
      });
    };

    filterModel.activate = () => {
      filterModelConfig.forEach(function(property) {
        filterModel.sortFilter[property.model] = converters[property.type].toModel(filterModel, property);
      });
    };

    // What is this trying to do?

    // The filter model is stored as keys/values on the `sortFilter` object
    // When the user modifies the filter, the ng-model binding is updated to the `sortFilter`
    // After the ng-model binding is updated, this function is called to update the URL.

    // The values in `sortFilter` are sometimes non-primitive nested objects such as `region: { east: true, west: true }`
    // To represent these values in the URL, they are encoded as strings using a custom ui-router parameter types.
    // In addition, there is also parameter encode/decode logic in the `converters` registry that predates the ui-router parameter types.
    filterModel.applyParamsToUrl = () => {
      // Get the current state parameters
      const params = ReactInjector.$stateParams;
      const newFilters = Object.keys(params).reduce(
        // Iterate over each state parameter
        (acc, paramName) => {
          // Find the filter model config for that parameter
          // If there is a model config:
          // - Try to convert the param's object model into a query parameter string
          // - If the string is nil, set the accumulator to null
          // - Otherwise, copy the sortfilter value for that param to the accumulator
          // If there isn't a model config:
          // - clone the current state parameter value and store on the accumulator
          const modelConfig = filterModelConfig.find(c => c.param === paramName);
          if (modelConfig) {
            const converted = converters[modelConfig.type].toParam(filterModel, modelConfig);
            if (converted === null || converted === undefined) {
              acc[paramName] = null;
            } else {
              acc[paramName] = cloneDeep(filterModel.sortFilter[modelConfig.model]);
            }
          } else {
            acc[paramName] = cloneDeep(ReactInjector.$stateParams[paramName]);
          }
          return acc;
        },
        {} as StateParams,
      );

      // Finally, apply the accumulator as the new state parameters
      const promise = ReactInjector.$state.go('.', newFilters, { inherit: false });
      const handleResult = () => {
        if (promise.transition.success) {
          console.log(
            'applyParamsToUrl transition was successful and changed the following params: ',
            JSON.stringify(promise.transition.paramsChanged()),
          );
        } else {
          console.log('applyParamsToUrl had no effect');
        }
      };
      promise.then(handleResult, handleResult);
    };

    return filterModel;
  }

  public static registerSaveAndRestoreRouterHooks(filterModel: IFilterModel, stateGlob: string) {
    const { transitionService } = ReactInjector.$uiRouter;
    const filterParams = filterModel.config.map(cfg => cfg.param);
    let savedParamsForScreen: any = {};

    // When exiting the screen, save the filters for that screen
    transitionService.onSuccess({ exiting: stateGlob, retained: '**.application' }, trans => {
      const fromParams = trans.params('from');
      savedParamsForScreen = pick(fromParams, filterParams);
    });

    // When entering the screen, restore the filters for that screen
    transitionService.onBefore({ entering: stateGlob, retained: '**.application' }, trans => {
      const toParams = trans.params();
      const hasFilters = filterParams.some(key => !isNil(toParams[key]));

      const savedParams = savedParamsForScreen;
      const hasSavedFilters = filterParams.some(key => !isNil(savedParams[key]));

      // Don't restore the saved filters if there are already filters specified (via url, ui-sref, etc)
      const shouldRedirectWithSavedParams = !hasFilters && hasSavedFilters;
      return shouldRedirectWithSavedParams ? trans.targetState().withParams(savedParams) : null;
    });

    // When switching apps, clear the saved state
    transitionService.onStart({ exiting: '**.application' }, () => {
      savedParamsForScreen = {};
    });
  }

  public static isFilterable(sortFilterModel: { [key: string]: boolean }): boolean {
    return size(sortFilterModel) > 0 && some(sortFilterModel);
  }

  public static getCheckValues(sortFilterModel: { [key: string]: boolean }) {
    return reduce(
      sortFilterModel,
      function(acc, val, key) {
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

  public static checkCategoryFilters(model: IFilterModel) {
    return (target: any) => {
      if (this.isFilterable(model.sortFilter.category)) {
        const checkedCategories = this.getCheckValues(model.sortFilter.category);
        return includes(checkedCategories, target.type) || includes(checkedCategories, target.category);
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
              delete (modelVal as any)[value];
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
            model.sortFilter[key] = clearValue;
            model.applyParamsToUrl();
          },
        });
      }
    }
    return tags;
  }
}
