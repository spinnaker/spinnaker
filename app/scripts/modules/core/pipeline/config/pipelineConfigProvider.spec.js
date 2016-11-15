'use strict';

describe('pipelineConfigProvider: API', function() {
  var configurer,
      service;

  beforeEach(
    window.module(
      require('./pipelineConfigProvider.js'),
      function(pipelineConfigProvider) {
        configurer = pipelineConfigProvider;
      }
    )
  );

  beforeEach(window.inject(function($injector, $log) {
    service = configurer.$get[2]($injector, $log);
  }));

  describe('registration', function() {
    it('registers triggers', window.inject(function() {
      expect(service.getTriggerTypes().length).toBe(0);
      configurer.registerTrigger({key: 'cron'});
      configurer.registerTrigger({key: 'pipeline'});
      expect(service.getTriggerTypes().length).toBe(2);
    }));

    it('registers stages', window.inject(function() {
      expect(service.getStageTypes().length).toBe(0);
      configurer.registerStage({key: 'a'});
      configurer.registerStage({key: 'b'});
      expect(service.getStageTypes().length).toBe(2);
    }));

    it('provides only non-synthetic stages, non-provider-specific stages', window.inject(function() {
      configurer.registerStage({key: 'a'});
      configurer.registerStage({key: 'b', synthetic: true});
      configurer.registerStage({key: 'c', useBaseProvider: true});
      configurer.registerStage({key: 'd', provides: 'c'});
      expect(service.getStageTypes().length).toBe(4);
      expect(service.getConfigurableStageTypes().length).toBe(2);
    }));

    it('returns providers for a stage key', window.inject(function() {
      configurer.registerStage({key: 'a'});
      configurer.registerStage({key: 'b', synthetic: true});
      configurer.registerStage({key: 'c', useBaseProvider: true});
      configurer.registerStage({key: 'd', provides: 'c'});
      configurer.registerStage({key: 'e', provides: 'c'});
      expect(service.getProvidersFor('c').length).toBe(2);
    }));

    it('returns providers of base stage for child key', window.inject(function() {
      configurer.registerStage({key: 'c', useBaseProvider: true});
      configurer.registerStage({nameToCheckInTest: 'a', key: 'd', provides: 'c'});
      configurer.registerStage({nameToCheckInTest: 'b', provides: 'c'});
      var providers = service.getProvidersFor('d');
      expect(providers.length).toBe(2);
      expect(_.map(providers, 'nameToCheckInTest').sort()).toEqual(['a', 'b']);
    }));

    it('augments provider stages with parent keys, labels, and descriptions', window.inject(function() {
      var baseStage = {key: 'c', useBaseProvider: true, description: 'c description', label: 'the c'},
          augmentedA = {key: 'd', provides: 'c', description: 'c description', label: 'the c'},
          augmentedB = {key: 'e', provides: 'c', description: 'c description', label: 'the c'},
          augmentedC = {key: 'c', provides: 'c', description: 'c description', label: 'the c'};
      configurer.registerStage(baseStage);
      configurer.registerStage({key: 'd', provides: 'c'});
      configurer.registerStage({key: 'e', provides: 'c'});
      configurer.registerStage({provides: 'c'});
      var stageTypes = service.getStageTypes();
      expect(stageTypes).toEqual([ baseStage, augmentedA, augmentedB, augmentedC ]);
      expect(service.getStageConfig({type: 'd'})).toEqual(augmentedA);
    }));

    it('allows provider stages to override of label, description', window.inject(function() {
      configurer.registerStage({key: 'a', useBaseProvider: true, description: 'a1', label: 'aa'});
      configurer.registerStage({key: 'b', provides: 'a', description: 'b1', label: 'bb'});
      configurer.registerStage({key: 'c', provides: 'a'});
      expect(service.getStageTypes()).toEqual([
        {key: 'a', useBaseProvider: true, description: 'a1', label: 'aa'},
        {key: 'b', provides: 'a', description: 'b1', label: 'bb'},
        {key: 'c', provides: 'a', description: 'a1', label: 'aa'}
      ]);
    }));

    it('returns stage config when an alias is supplied', window.inject(function() {
      let config = {key: 'a', alias: 'a1'};
      configurer.registerStage(config);
      expect(service.getStageConfig({type: 'a'})).toEqual(config);
      expect(service.getStageConfig({type: 'a1'})).toEqual(config);
      expect(service.getStageConfig({type: 'b'})).toBe(null);
    }));
  });

  describe('stage type retrieval', function () {
    describe('no provider configured', function () {
      it('adds all providers to stages that do not have any provider configuration', window.inject(function () {
        configurer.registerStage({key: 'a'});
        expect(service.getConfigurableStageTypes(['aws', 'gcp'])).toEqual([{key: 'a', cloudProviders: ['aws', 'gcp']}]);
      }));
    });

    describe('cloud providers configured on stage', function () {
      it('preserves providers that match passed in providers if configured with cloudProviders', window.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws']});
        expect(service.getConfigurableStageTypes(['aws', 'gcp'])).toEqual([{key: 'a', providesFor: ['aws'], cloudProviders: ['aws']}]);
      }));

      it('filters providers to those passed in', window.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws', 'gcp']});
        expect(service.getConfigurableStageTypes(['gcp'])).toEqual([{key: 'a', providesFor: ['aws', 'gcp'], cloudProviders: ['gcp']}]);
      }));

      it('filters out stages that do not support passed in providers', window.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws', 'gcp']});
        expect(service.getConfigurableStageTypes(['titus'])).toEqual([]);
      }));

      it('filters out stages that do not support passed in providers', window.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws', 'gcp']});
        expect(service.getConfigurableStageTypes(['titus'])).toEqual([]);
      }));
    });

    describe('single cloud provider configured on stage', function () {
      it('retains cloud providers when matching passed in providers', window.inject(function () {
        configurer.registerStage({key: 'a', cloudProvider: 'aws'});
        expect(service.getConfigurableStageTypes(['aws'])).toEqual([{key: 'a', cloudProvider: 'aws', cloudProviders: ['aws']}]);
      }));

      it('filters stages when provider does not match', window.inject(function () {
        configurer.registerStage({key: 'a', cloudProvider: 'aws'});
        expect(service.getConfigurableStageTypes(['gcp'])).toEqual([]);
      }));
    });

    describe('base stages', function () {
      it('returns stage implementation providers that match based on cloud provider', window.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true});
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws'});
        expect(service.getConfigurableStageTypes(['aws'])).toEqual([{key: 'a', useBaseProvider: true, cloudProviders: ['aws']}]);
      }));

      it('filters stage implementations with no matching cloud provider', window.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true});
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws'});
        expect(service.getConfigurableStageTypes(['gcp'])).toEqual([]);
      }));

      it('aggregates and filters cloud providers', window.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true});
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws'});
        configurer.registerStage({key: 'c', provides: 'a', cloudProvider: 'gcp'});
        configurer.registerStage({key: 'd', provides: 'a', cloudProvider: 'titus'});
        expect(service.getConfigurableStageTypes(['aws', 'titus'])).toEqual([{key: 'a', useBaseProvider: true, cloudProviders: ['aws', 'titus']}]);
      }));

      it('prefers providesFor to cloudProvider when configured on an implementing stage', window.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true});
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws', providesFor: ['aws', 'gcp', 'titus']});
        expect(service.getConfigurableStageTypes(['aws', 'titus'])).toEqual([{key: 'a', useBaseProvider: true, cloudProviders: ['aws', 'titus']}]);
      }));
    });

  });

  describe('manualExecutionHandlers', function () {
    it ('hasManualExecutionHandlerForTriggerType returns false if nothing configured', function () {
      configurer.registerTrigger({key: 'a'});
      expect(service.hasManualExecutionHandlerForTriggerType('a')).toBe(false);
      expect(service.hasManualExecutionHandlerForTriggerType('b')).toBe(false);
    });

    it('hasManualExecutionHandlerForTriggerType returns false if declared but not configured', function () {
      configurer.registerTrigger({key: 'a', manualExecutionHandler: 'someHandlerThatDoesNotExist'});
      expect(service.hasManualExecutionHandlerForTriggerType('a')).toBe(false);
    });

    it('hasManualExecutionHandlerForTriggerType returns true if declared and available', function () {
      configurer.registerTrigger({key: 'cron', manualExecutionHandler: 'pipelineConfig'});
      expect(service.hasManualExecutionHandlerForTriggerType('cron')).toBe(true);
    });

    it('getManualExecutionHandlerForTriggerType returns null if nothing configured', function () {
      configurer.registerTrigger({key: 'a'});
      expect(service.getManualExecutionHandlerForTriggerType('a')).toBe(null);
      expect(service.getManualExecutionHandlerForTriggerType('b')).toBe(null);
    });

    it('getManualExecutionHandlerForTriggerType returns null if declared but not configured', function () {
      configurer.registerTrigger({key: 'a', manualExecutionHandler: 'someHandlerThatDoesNotExist'});
      expect(service.getManualExecutionHandlerForTriggerType('a')).toBe(null);
      expect(service.getManualExecutionHandlerForTriggerType('b')).toBe(null);
    });

    it('hasManualExecutionHandlerForTriggerType returns handler if declared and available', function () {
      configurer.registerTrigger({key: 'cron', manualExecutionHandler: 'pipelineConfig'});
      expect(service.getManualExecutionHandlerForTriggerType('cron')).not.toBe(null);
      expect(Object.keys(service.getManualExecutionHandlerForTriggerType('cron'))).toEqual(Object.keys(service));
    });
  });
});
