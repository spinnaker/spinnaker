'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.filterModel.service', [
    require('angular-ui-router'),
  ])
.factory('filterModelService', function ($location, $state, $stateParams, $urlRouter, $timeout) {

    function isFilterable(sortFilterModel) {
      return _.size(sortFilterModel) > 0 && _.some(sortFilterModel);
    }

    function getCheckValues(sortFilterModel) {
      return _.reduce(sortFilterModel, function(acc, val, key) {
        if (val) {
          acc.push(key);
        }
        return acc;
      }, []).sort();
    }

    function checkAccountFilters(model) {
      return function(target) {
        if(isFilterable(model.sortFilter.account)) {
          var checkedAccounts = getCheckValues(model.sortFilter.account);
          return _.includes(checkedAccounts, target.account);
        } else {
          return true;
        }
      };
    }

    function checkRegionFilters(model) {
      return function(target) {
        if(isFilterable(model.sortFilter.region)) {
          var checkedRegions = getCheckValues(model.sortFilter.region);
          return _.includes(checkedRegions, target.region);
        } else {
          return true;
        }
      };
    }

    function checkStackFilters(model) {
      return function(target) {
        if (isFilterable(model.sortFilter.stack)) {
          var checkedStacks = getCheckValues(model.sortFilter.stack);
          if (checkedStacks.includes('(none)')) {
            checkedStacks.push('');
          }
          return _.includes(checkedStacks, target.stack);
        } else {
          return true;
        }
      };
    }

    function checkStatusFilters(model) {
      return function(target) {
        if (isFilterable(model.sortFilter.status)) {
          var checkedStatus = getCheckValues(model.sortFilter.status);
          return _.includes(checkedStatus, 'Up') && target.instanceCounts.down === 0 ||
            _.includes(checkedStatus, 'Down') && target.instanceCounts.down > 0 ||
            _.includes(checkedStatus, 'OutOfService') && target.instanceCounts.outOfService > 0 ||
            _.includes(checkedStatus, 'Starting') && target.instanceCounts.starting > 0 ||
            _.includes(checkedStatus, 'Disabled') && target.isDisabled ||
            _.includes(checkedStatus, 'Unknown') && target.instanceCounts.unknown > 0;
        }
        return true;
      };
    }

    function checkCategoryFilters(model) {
      return function(target) {
        if (isFilterable(model.sortFilter.category)) {
          var checkedCategories = getCheckValues(model.sortFilter.category);
          return _.includes(checkedCategories, target.type) || _.includes(checkedCategories, target.category);
        } else {
          return true;
        }
      };
    }

    function checkProviderFilters(model) {
      return function(target) {
        if (isFilterable(model.sortFilter.providerType)) {
          var checkedProviderTypes = getCheckValues(model.sortFilter.providerType);
          return _.includes(checkedProviderTypes, target.type) || _.includes(checkedProviderTypes, target.provider);
        } else {
          return true;
        }
      };
    }

    function getParamVal(property) {
      return $location.search()[property.param] || property.defaultValue;
    }

    function addTagsForSection(model, property) {
      var key = property.model,
          label = property.filterLabel || property.model,
          translator = property.filterTranslator || {},
          clearValue = property.clearValue;
      translator = translator || {};
      var tags = model.tags;
      var modelVal = model.sortFilter[key];
      if (property.type === 'object') {
        _.forOwn(modelVal, function (isActive, value) {
          if (isActive) {
            tags.push({
              key: key,
              label: label,
              value: translator[value] || value,
              clear: function () {
                delete model.sortFilter[key][value];
                model.applyParamsToUrl();
              }
            });
          }
        });
      } else {
        if (modelVal !== null && modelVal !== undefined && modelVal !== '' && modelVal !== false) {
          tags.push({
            key: key,
            label: label,
            value: translator[modelVal] || modelVal,
            clear: function () {
              model.sortFilter[key] = clearValue;
              model.applyParamsToUrl();
            }
          });
        }
      }
      return tags;
    }

    var converters = {
      'object': {
        toParam: function(filterModel, property) {
          var obj = filterModel.sortFilter[property.model];
          if (obj) {
            return _.chain(obj)
              .map(function (val, key) {
                if (val) {
                  // replace any commas in the string with their uri-encoded version ('%2c'), since
                  // we use commas as our separator in the URL
                  return key.replace(/,/g, '%2c');
                }
              })
              .remove(undefined)
              .value()
              .sort()
              .join(',') || null;
          }
        },
        toModel: function(filterModel, property) {
          var paramList = getParamVal(property);
          if (paramList) {
            return _.reduce(paramList.split(','), function (acc, value) {
              // replace any uri-encoded commas in the string ('%2c') with actual commas, since
              // we use commas as our separator in the URL
              acc[value.replace(/%2c/g, ',')] = true;
              return acc;
            }, {});
          } else {
            return {};
          }
        }
      },
      'string': {
        toParam: function(filterModel, property) {
          var val = filterModel.sortFilter[property.model];
          return val && val !== property.defaultValue ? val : null;
        },
        toModel: function(filterModel, property) {
          var val = getParamVal(property);
          return val ? val : '';
        }
      },
      'boolean': {
        toParam: function(filterModel, property) {
          var val = filterModel.sortFilter[property.model];
          return val ? val.toString() : null;
        },
        toModel: function(filterModel, property) {
          var val = getParamVal(property);
          return Boolean(val);
        }
      },
      'inverse-boolean': {
        toParam: function(filterModel, property) {
          var val = filterModel.sortFilter[property.model];
          return val ? null : 'true';
        },
        toModel: function(filterModel, property) {
          var val = getParamVal(property);
          return !Boolean(val);
        }
      },
      'number': {
        toParam: function(filterModel, property) {
          var val = filterModel.sortFilter[property.model];
          return isNaN(val) ? null : property.defaultValue === val ? null : val;
        },
        toModel: function(filterModel, property) {
          var val = getParamVal(property);
          return isNaN(val) ? null : Number(val);
        }
      },
      'sortKey': {
        toParam: function(filterModel, property) {
          var val = filterModel.sortFilter[property.model].key;
          return val === property.defaultValue ? null : val;
        },
        toModel: function(filterModel, property) {
          var val = getParamVal(property);
          return { key: val };
        }
      }
    };

    function configureFilterModel(filterModel, filterModelConfig) {
      filterModel.groups = [];
      filterModel.tags = [];
      filterModel.displayOptions = {};
      filterModel.savedState = {};
      filterModel.sortFilter = {};

      filterModelConfig.forEach(function (property) {
        property.param = property.param || property.model;
      });

      filterModel.addTags = function() {
        filterModel.tags.length = 0;
        filterModelConfig
          .filter(function (property) {
            return !property.displayOption;
          })
          .forEach(function (property) {
            addTagsForSection(filterModel, property);
          });
      };

      filterModel.saveState = function(state, params, filters) {
        if (params.application) {
          filters = filters || $location.search();
          filterModel.savedState[params.application] = {
            filters: angular.copy(filters),
            state: state,
            params: params,
          };
        }
      };

      filterModel.restoreState = function(toParams) {
        var application = toParams.application;
        var savedState = filterModel.savedState[application];
        if (savedState) {
          angular.copy(savedState.params, $stateParams);
          var currentParams = $location.search();
          // clear any shared params between states, e.g. previous state set 'acct', which this state also uses,
          // but this state does not have that field set, so angular.extend will not overwrite it
          _.forOwn(currentParams, function(val, key) {
            if (Object.hasOwnProperty(savedState.filters, key)) {
              delete currentParams[key];
            }
          });
          $timeout(function() {
            $location.search(angular.extend(currentParams, savedState.filters));
            filterModel.activate();
            $location.replace();
          });
        }
      };

      filterModel.hasSavedState = function(toParams) {
        var application = toParams.application;
        return filterModel.savedState[application] !== undefined && filterModel.savedState[application].params !== undefined;
      };

      filterModel.clearFilters = function () {
        filterModelConfig.forEach(function (property) {
          if (!property.displayOption) {
            filterModel.sortFilter[property.model] = property.clearValue;
          }
        });
      };

      filterModel.activate = function() {
        filterModelConfig.forEach(function (property) {
          filterModel.sortFilter[property.model] = converters[property.type].toModel(filterModel, property);
        });
      };

      filterModel.applyParamsToUrl = function() {
        filterModelConfig.forEach(function (property) {
          $location.search(property.param, converters[property.type].toParam(filterModel, property));
        });
        $urlRouter.update(true);
      };
    }

    return {
      configureFilterModel: configureFilterModel,
      isFilterable: isFilterable,
      getCheckValues: getCheckValues,
      checkAccountFilters: checkAccountFilters,
      checkRegionFilters: checkRegionFilters,
      checkStackFilters: checkStackFilters,
      checkStatusFilters: checkStatusFilters,
      checkProviderFilters: checkProviderFilters,
      checkCategoryFilters: checkCategoryFilters,
    };

  });
