'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.configProvider', [
  require('../../config/settings.js'),
])
  .provider('pipelineConfig', function(settings) {

    var triggerTypes = [],
        stageTypes = [],
        transformers = [];

    function registerTrigger(triggerConfig) {
      if (settings && settings.triggerTypes) {
        if (settings.triggerTypes.indexOf(triggerConfig.key) >= 0) {
          triggerTypes.push(triggerConfig);
        }
      } else {
        triggerTypes.push(triggerConfig);
      }
    }

    function registerTransformer(transformer) {
      transformers.push(transformer);
    }

    function registerStage(stageConfig) {
      stageTypes.push(stageConfig);
      normalizeStageTypes();
    }

    function normalizeStageTypes() {
      stageTypes
        .filter((stageType) => { return stageType.provides; })
        .forEach((stageType) => {
          var parent = stageTypes.filter((parentType) => {
            return parentType.key === stageType.provides && !parentType.provides;
          });
          if (parent.length) {
            stageType.label = stageType.label || parent[0].label;
            stageType.description = stageType.description || parent[0].description;
            stageType.key = stageType.key || parent[0].key;
          }
        });
    }

    function getExecutionTransformers() {
      return transformers;
    }

    function getTriggerTypes() {
      return angular.copy(triggerTypes);
    }

    function getStageTypes() {
      return angular.copy(stageTypes);
    }

    function getConfigurableStageTypes(providers) {
      let allStageTypes = getStageTypes();
      let configurableStageTypes = allStageTypes.filter(stageType => !stageType.synthetic && !stageType.provides);
      if (!providers) {
        return configurableStageTypes;
      }
      configurableStageTypes.forEach(type => type.cloudProviders = getCloudProvidersForStage(type, allStageTypes, providers));
      getCloudProvidersForStage(allStageTypes, configurableStageTypes, providers);
      return configurableStageTypes
        .filter(stageType => stageType.cloudProviders.length)
        .sort((a, b) => a.label.localeCompare(b.label));
    }

    function getCloudProvidersForStage(type, allStageTypes, providers) {
      let cloudProviders = [];
      if (type.providesFor) {
        cloudProviders = type.providesFor;
      } else if (type.cloudProvider) {
        cloudProviders = [type.cloudProvider];
      } else if (type.useBaseProvider) {
        const stageProviders = allStageTypes.filter(s => s.provides === type.key);
        stageProviders.forEach(sp => {
          if (sp.providesFor) {
            cloudProviders = cloudProviders.concat(sp.providesFor);
          } else {
            cloudProviders.push(sp.cloudProvider);
          }
        });
      } else {
        cloudProviders = providers;
      }

      // Docker Bake is wedged in here because it doesn't really fit our existing cloud provider paradigm
      let dockerBakeEnabled = _.get(settings, 'feature.dockerBake') && type.key === 'bake';

      if (dockerBakeEnabled) {
        providers = angular.copy(providers);
        providers.push('docker');
      }

      return _.intersection(providers, cloudProviders);
    }

    function getProvidersFor(key) {
      // because the key might be the implementation itself, determine the base key, then get every provider for it
      let baseKey = key,
          stageTypes = getStageTypes();
      let candidates = stageTypes.filter(function(stageType) {
        return stageType.provides && (stageType.provides === key || stageType.key === key || stageType.alias === key);
      });
      if (candidates.length) {
        baseKey = candidates[0].provides;
      }
      return getStageTypes().filter(function(stageType) {
        return stageType.provides && stageType.provides === baseKey;
      });
    }

    let getStageConfig = _.memoize(function (stage) {
      if (!stage || !stage.type) {
        return null;
      }
      var matches = getStageTypes().filter((stageType) => {
        return stageType.key === stage.type || stageType.provides === stage.type || stageType.alias === stage.type;
      });
      if (matches.length > 1) {
        var provider = stage.cloudProvider || stage.cloudProviderType || 'aws';
        var matchesForStageCloudProvider = matches.filter((stageType) => {
          return stageType.cloudProvider === provider;
        });

        if (!matchesForStageCloudProvider.length) {
          return matches.find((stageType) => {
            return stageType.cloudProvider || stageType.cloudProviderType;
          }) || null;
        }
        return matchesForStageCloudProvider[0];
      }
      return matches.length ? matches[0] : null;
    }, (stage) => [stage ? stage.type : '', stage ? stage.cloudProvider || stage.cloudProviderType || 'aws' : ''].join(':'));

    function getTriggerConfig(type) {
      var matches = getTriggerTypes().filter(function(triggerType) { return triggerType.key === type; });
      return matches.length ? matches[0] : null;
    }

    this.registerTrigger = registerTrigger;
    this.registerStage = registerStage;

    this.$get = function($injector, $log) {

      function getManualExecutionHandlerForTriggerType(triggerType) {
        let triggerConfig = getTriggerConfig(triggerType);
        if (triggerConfig && triggerConfig.manualExecutionHandler) {
          if ($injector.has(triggerConfig.manualExecutionHandler)) {
            return $injector.get(triggerConfig.manualExecutionHandler);
          }
        }
        return null;
      }

      function hasManualExecutionHandlerForTriggerType(triggerType) {
        let hasHandler = false;
        let triggerConfig = getTriggerConfig(triggerType);
        if (triggerConfig && triggerConfig.manualExecutionHandler) {
          hasHandler = $injector.has(triggerConfig.manualExecutionHandler);
          if (!hasHandler) {
            $log.warn(`Could not find execution handler '${triggerConfig.manualExecutionHandler}' for trigger type '${triggerType}'`);
          }
        }
        return hasHandler;
      }

      return {
        getManualExecutionHandlerForTriggerType: getManualExecutionHandlerForTriggerType,
        hasManualExecutionHandlerForTriggerType: hasManualExecutionHandlerForTriggerType,
        getTriggerTypes: getTriggerTypes,
        getStageTypes: getStageTypes,
        getProvidersFor: getProvidersFor,
        getTriggerConfig: getTriggerConfig,
        getStageConfig: getStageConfig,
        getConfigurableStageTypes: getConfigurableStageTypes,
        getExecutionTransformers: getExecutionTransformers,
        registerTransformer: registerTransformer,
      };
    };

  }
);
