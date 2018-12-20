import { StateParams } from '@uirouter/core';
import { cloneDeep, size, some, reduce, forOwn, includes, chain } from 'lodash';
import { $location, $timeout } from 'ngimport';

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

    filterModel.groups = [];
    filterModel.tags = [];
    filterModel.displayOptions = {};
    filterModel.savedState = {};
    filterModel.sortFilter = {} as ISortFilter;

    filterModelConfig.forEach(property => (property.param = property.param || property.model));

    filterModel.addTags = () => {
      filterModel.tags = [];
      filterModelConfig
        .filter(property => !property.displayOption)
        .forEach(property => this.addTagsForSection(filterModel, property));
    };

    filterModel.saveState = (state, params, filters) => {
      if (params.application) {
        filters = filters || $location.search();
        filterModel.savedState[params.application] = {
          filters: cloneDeep(filters),
          state,
          params,
        };
      }
    };

    filterModel.restoreState = toParams => {
      const application = toParams.application;
      const savedState = filterModel.savedState[application];
      if (savedState) {
        Object.keys(ReactInjector.$stateParams).forEach(k => delete ReactInjector.$stateParams[k]);
        Object.assign(ReactInjector.$stateParams, cloneDeep(savedState.params));
        const currentParams = $location.search();
        // clear any shared params between states, e.g. previous state set 'acct', which this state also uses,
        // but this state does not have that field set, so angular.extend will not overwrite it
        forOwn(currentParams, function(_val, key) {
          if (savedState.filters.hasOwnProperty(key)) {
            delete currentParams[key];
          }
        });
        $timeout(function() {
          Object.assign(currentParams, savedState.filters);
          $location.search(currentParams);
          filterModel.activate();
          $location.replace();
        });
      }
    };

    filterModel.hasSavedState = toParams => {
      const application = toParams.application;
      const serverGroup = toParams.serverGroup;

      const savedStateForApplication = filterModel.savedState[application];

      return (
        savedStateForApplication !== undefined &&
        savedStateForApplication.params !== undefined &&
        (!serverGroup ||
          (savedStateForApplication.params.serverGroup && savedStateForApplication.params.serverGroup === serverGroup))
      );
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

    filterModel.applyParamsToUrl = () => {
      const newFilters = Object.keys(ReactInjector.$stateParams).reduce(
        (acc, paramName) => {
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

      ReactInjector.$state.go('.', newFilters, { inherit: false });
    };

    return filterModel;
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
