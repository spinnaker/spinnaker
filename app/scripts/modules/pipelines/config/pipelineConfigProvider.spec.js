'use strict';

describe('pipelineConfigProvider: API', function() {
  var configurer;

  describe('registration', function() {
    beforeEach(
      window.module(
        require('./pipelineConfigProvider.js'),
        function(pipelineConfigProvider) {
          configurer = pipelineConfigProvider;
        }
      )
    );

    it('registers triggers', window.inject(function() {
      expect(configurer.$get().getTriggerTypes().length).toBe(0);
      configurer.registerTrigger({key: 'a'});
      configurer.registerTrigger({key: 'b'});
      expect(configurer.$get().getTriggerTypes().length).toBe(2);
    }));

    it('registers stages', window.inject(function() {
      expect(configurer.$get().getStageTypes().length).toBe(0);
      configurer.registerStage({key: 'a'});
      configurer.registerStage({key: 'b'});
      expect(configurer.$get().getStageTypes().length).toBe(2);
    }));

    it('provides only non-synthetic stages, non-provider-specific stages', window.inject(function() {
      configurer.registerStage({key: 'a'});
      configurer.registerStage({key: 'b', synthetic: true});
      configurer.registerStage({key: 'c', useBaseProvider: true});
      configurer.registerStage({key: 'd', provides: 'c'});
      expect(configurer.$get().getStageTypes().length).toBe(4);
      expect(configurer.$get().getConfigurableStageTypes().length).toBe(2);
    }));

    it('returns providers for a stage key', window.inject(function() {
      configurer.registerStage({key: 'a'});
      configurer.registerStage({key: 'b', synthetic: true});
      configurer.registerStage({key: 'c', useBaseProvider: true});
      configurer.registerStage({key: 'd', provides: 'c'});
      configurer.registerStage({key: 'e', provides: 'c'});
      expect(configurer.$get().getProvidersFor('c').length).toBe(2);
    }));

    it('returns providers of base stage for child key', window.inject(function() {
      configurer.registerStage({key: 'c', useBaseProvider: true});
      configurer.registerStage({nameToCheckInTest: 'a', key: 'd', provides: 'c'});
      configurer.registerStage({nameToCheckInTest: 'b', provides: 'c'});
      var providers = configurer.$get().getProvidersFor('d');
      expect(providers.length).toBe(2);
      expect(_.pluck(providers, 'nameToCheckInTest').sort()).toEqual(['a', 'b']);
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
      var stageTypes = configurer.$get().getStageTypes();
      expect(stageTypes).toEqual([ baseStage, augmentedA, augmentedB, augmentedC ]);
      expect(configurer.$get().getStageConfig({type: 'd'})).toEqual(augmentedA);
    }));

    it('allows provider stages to override of label, description', window.inject(function() {
      configurer.registerStage({key: 'a', useBaseProvider: true, description: 'a1', label: 'aa'});
      configurer.registerStage({key: 'b', provides: 'a', description: 'b1', label: 'bb'});
      configurer.registerStage({key: 'c', provides: 'a'});
      expect(configurer.$get().getStageTypes()).toEqual([
        {key: 'a', useBaseProvider: true, description: 'a1', label: 'aa'},
        {key: 'b', provides: 'a', description: 'b1', label: 'bb'},
        {key: 'c', provides: 'a', description: 'a1', label: 'aa'}
      ]);
    }));

    it('returns stage config when an alias is supplied', window.inject(function() {
      let config = {key: 'a', alias: 'a1'};
      configurer.registerStage(config);
      expect(configurer.$get().getStageConfig({type: 'a'})).toEqual(config);
      expect(configurer.$get().getStageConfig({type: 'a1'})).toEqual(config);
      expect(configurer.$get().getStageConfig({type: 'b'})).toBe(null);
    }));
  });

});
