import { auto, mock } from 'angular';
import { map } from 'lodash';

import { IStage, ITriggerTypeConfig, IStageTypeConfig } from 'core/domain';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from './pipelineConfigProvider';

describe('pipelineConfigProvider: API', function() {
  let configurer: PipelineConfigProvider,
      service: PipelineConfigProvider;

  beforeEach(function() {
    mock.module(
      PIPELINE_CONFIG_PROVIDER,
      function(pipelineConfigProvider: PipelineConfigProvider) {
        configurer = pipelineConfigProvider;
      }
    );

    mock.inject(function($injector: auto.IInjectorService) {
      service = configurer.$get($injector);
    })
  });

  describe('registration', function() {
    it('registers triggers', mock.inject(function() {
      expect(service.getTriggerTypes().length).toBe(0);
      configurer.registerTrigger({key: 'cron'} as ITriggerTypeConfig);
      configurer.registerTrigger({key: 'pipeline'} as ITriggerTypeConfig);
      expect(service.getTriggerTypes().length).toBe(2);
    }));

    it('registers stages', mock.inject(function() {
      expect(service.getStageTypes().length).toBe(0);
      configurer.registerStage({key: 'a'} as IStageTypeConfig);
      configurer.registerStage({key: 'b'} as IStageTypeConfig);
      expect(service.getStageTypes().length).toBe(2);
    }));

    it('provides only non-synthetic stages, non-provider-specific stages', mock.inject(function() {
      configurer.registerStage({key: 'a'} as IStageTypeConfig);
      configurer.registerStage({key: 'b', synthetic: true} as IStageTypeConfig);
      configurer.registerStage({key: 'c', useBaseProvider: true} as IStageTypeConfig);
      configurer.registerStage({key: 'd', provides: 'c'} as IStageTypeConfig);
      expect(service.getStageTypes().length).toBe(4);
      expect(service.getConfigurableStageTypes().length).toBe(2);
    }));

    it('returns providers for a stage key', mock.inject(function() {
      configurer.registerStage({key: 'a'} as IStageTypeConfig);
      configurer.registerStage({key: 'b', synthetic: true} as IStageTypeConfig);
      configurer.registerStage({key: 'c', useBaseProvider: true} as IStageTypeConfig);
      configurer.registerStage({key: 'd', provides: 'c'} as IStageTypeConfig);
      configurer.registerStage({key: 'e', provides: 'c'} as IStageTypeConfig);
      expect(service.getProvidersFor('c').length).toBe(2);
    }));

    it('returns providers of base stage for child key', mock.inject(function() {
      configurer.registerStage({key: 'c', useBaseProvider: true} as IStageTypeConfig);
      configurer.registerStage({nameToCheckInTest: 'a', key: 'd', provides: 'c'} as IStageTypeConfig);
      configurer.registerStage({nameToCheckInTest: 'b', provides: 'c'} as IStageTypeConfig);
      const providers = service.getProvidersFor('d');
      expect(providers.length).toBe(2);
      expect(map(providers, 'nameToCheckInTest').sort()).toEqual(['a', 'b']);
    }));

    it('augments provider stages with parent keys, labels, manualExecutionHandlers, and descriptions', mock.inject(function() {
      const baseStage = {key: 'c', useBaseProvider: true, description: 'c description', label: 'the c', manualExecutionHandler: 'a'},
          augmentedA = {key: 'd', provides: 'c', description: 'c description', label: 'the c', manualExecutionHandler: 'a'} as any,
          augmentedB = {key: 'e', provides: 'c', description: 'c description', label: 'the c', manualExecutionHandler: 'a'},
          augmentedC = {key: 'c', provides: 'c', description: 'c description', label: 'the c', manualExecutionHandler: 'a'};
      configurer.registerStage(baseStage as IStageTypeConfig);
      configurer.registerStage({key: 'd', provides: 'c'} as IStageTypeConfig);
      configurer.registerStage({key: 'e', provides: 'c'} as IStageTypeConfig);
      configurer.registerStage({provides: 'c'} as IStageTypeConfig);
      const stageTypes = service.getStageTypes();
      expect(stageTypes as any[]).toEqual([ baseStage, augmentedA, augmentedB, augmentedC ]);
      expect(service.getStageConfig({type: 'd'} as any)).toEqual(augmentedA);
    }));

    it('allows provider stages to override of label, description, manualExecutionHandler', mock.inject(function() {
      configurer.registerStage({key: 'a', useBaseProvider: true, description: 'a1', label: 'aa', manualExecutionHandler: 'a'} as IStageTypeConfig);
      configurer.registerStage({key: 'b', provides: 'a', description: 'b1', label: 'bb', manualExecutionHandler: 'b'} as IStageTypeConfig);
      configurer.registerStage({key: 'c', provides: 'a'} as IStageTypeConfig);
      expect(service.getStageTypes() as any[]).toEqual([
        {key: 'a', useBaseProvider: true, description: 'a1', label: 'aa', manualExecutionHandler: 'a'},
        {key: 'b', provides: 'a', description: 'b1', label: 'bb', manualExecutionHandler: 'b'},
        {key: 'c', provides: 'a', description: 'a1', label: 'aa', manualExecutionHandler: 'a'}
      ]);
    }));

    it('returns stage config when an alias is supplied', mock.inject(function() {
      const config: IStageTypeConfig = {key: 'a', alias: 'a1'} as IStageTypeConfig;
      configurer.registerStage(config);
      expect(service.getStageConfig({type: 'a'} as IStage)).toEqual(config);
      expect(service.getStageConfig({type: 'a1'} as IStage)).toEqual(config);
      expect(service.getStageConfig({type: 'b'} as IStage)).toBe(null);
    }));
  });

  describe('stage type retrieval', function () {
    describe('no provider configured', function () {
      it('adds all providers to stages that do not have any provider configuration', mock.inject(function () {
        configurer.registerStage({key: 'a'} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['aws', 'gcp']) as any[]).toEqual([{key: 'a', cloudProviders: ['aws', 'gcp']}]);
      }));
    });

    describe('cloud providers configured on stage', function () {
      it('preserves providers that match passed in providers if configured with cloudProviders', mock.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws']} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['aws', 'gcp']) as any[]).toEqual([{key: 'a', providesFor: ['aws'], cloudProviders: ['aws']}]);
      }));

      it('filters providers to those passed in', mock.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws', 'gcp']} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['gcp']) as any[]).toEqual([{key: 'a', providesFor: ['aws', 'gcp'], cloudProviders: ['gcp']}]);
      }));

      it('filters out stages that do not support passed in providers', mock.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws', 'gcp']} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['titus'])).toEqual([]);
      }));

      it('filters out stages that do not support passed in providers', mock.inject(function () {
        configurer.registerStage({key: 'a', providesFor: ['aws', 'gcp']} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['titus'])).toEqual([]);
      }));
    });

    describe('single cloud provider configured on stage', function () {
      it('retains cloud providers when matching passed in providers', mock.inject(function () {
        configurer.registerStage({key: 'a', cloudProvider: 'aws'} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['aws']) as any[]).toEqual([{key: 'a', cloudProvider: 'aws', cloudProviders: ['aws']}]);
      }));

      it('filters stages when provider does not match', mock.inject(function () {
        configurer.registerStage({key: 'a', cloudProvider: 'aws'} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['gcp'])).toEqual([]);
      }));
    });

    describe('base stages', function () {
      it('returns stage implementation providers that match based on cloud provider', mock.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true} as IStageTypeConfig);
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws'} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['aws']) as any[]).toEqual([{key: 'a', useBaseProvider: true, cloudProviders: ['aws']}]);
      }));

      it('filters stage implementations with no matching cloud provider', mock.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true} as IStageTypeConfig);
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws'} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['gcp'])).toEqual([]);
      }));

      it('aggregates and filters cloud providers', mock.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true} as IStageTypeConfig);
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws'} as IStageTypeConfig);
        configurer.registerStage({key: 'c', provides: 'a', cloudProvider: 'gcp'} as IStageTypeConfig);
        configurer.registerStage({key: 'd', provides: 'a', cloudProvider: 'titus'} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['aws', 'titus']) as any[]).toEqual([{key: 'a', useBaseProvider: true, cloudProviders: ['aws', 'titus']}]);
      }));

      it('prefers providesFor to cloudProvider when configured on an implementing stage', mock.inject(function () {
        configurer.registerStage({key: 'a', useBaseProvider: true} as IStageTypeConfig);
        configurer.registerStage({key: 'b', provides: 'a', cloudProvider: 'aws', providesFor: ['aws', 'gcp', 'titus']} as IStageTypeConfig);
        expect(service.getConfigurableStageTypes(['aws', 'titus']) as any[]).toEqual([{key: 'a', useBaseProvider: true, cloudProviders: ['aws', 'titus']}]);
      }));
    });

  });

  describe('manualExecutionHandlers', function () {
    it ('hasManualExecutionHandlerForTriggerType returns false if nothing configured', function () {
      configurer.registerTrigger({key: 'a'} as ITriggerTypeConfig);
      expect(service.hasManualExecutionHandlerForTriggerType('a')).toBe(false);
      expect(service.hasManualExecutionHandlerForTriggerType('b')).toBe(false);
    });

    it('hasManualExecutionHandlerForTriggerType returns false if declared but not configured', function () {
      configurer.registerTrigger({key: 'a', manualExecutionHandler: 'someHandlerThatDoesNotExist'} as ITriggerTypeConfig);
      expect(service.hasManualExecutionHandlerForTriggerType('a')).toBe(false);
    });

    it('hasManualExecutionHandlerForTriggerType returns true if declared and available', function () {
      configurer.registerTrigger({key: 'cron', manualExecutionHandler: 'pipelineConfig'} as ITriggerTypeConfig);
      expect(service.hasManualExecutionHandlerForTriggerType('cron')).toBe(true);
    });

    it('getManualExecutionHandlerForTriggerType returns null if nothing configured', function () {
      configurer.registerTrigger({key: 'a'} as ITriggerTypeConfig);
      expect(service.getManualExecutionHandlerForTriggerType('a')).toBe(null);
      expect(service.getManualExecutionHandlerForTriggerType('b')).toBe(null);
    });

    it('getManualExecutionHandlerForTriggerType returns null if declared but not configured', function () {
      configurer.registerTrigger({key: 'a', manualExecutionHandler: 'someHandlerThatDoesNotExist'} as ITriggerTypeConfig);
      expect(service.getManualExecutionHandlerForTriggerType('a')).toBe(null);
      expect(service.getManualExecutionHandlerForTriggerType('b')).toBe(null);
    });

    it('hasManualExecutionHandlerForTriggerType returns handler if declared and available', function () {
      configurer.registerTrigger({key: 'cron', manualExecutionHandler: 'pipelineConfig'} as ITriggerTypeConfig);
      expect(service.getManualExecutionHandlerForTriggerType('cron')).not.toBe(null);
      expect(Object.keys(service.getManualExecutionHandlerForTriggerType('cron'))).toEqual(Object.keys(service));
    });
  });
});
