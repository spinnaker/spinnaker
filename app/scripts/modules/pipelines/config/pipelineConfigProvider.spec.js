'use strict';

describe('authenticationProvider: application startup', function() {
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
      configurer.registerTrigger({type: 'a'});
      configurer.registerTrigger({type: 'b'});
      expect(configurer.$get().getTriggerTypes().length).toBe(2);
    }));

    it('registers stages', window.inject(function() {
      expect(configurer.$get().getStageTypes().length).toBe(0);
      configurer.registerStage({type: 'a'});
      configurer.registerStage({type: 'b'});
      expect(configurer.$get().getStageTypes().length).toBe(2);
    }));

    it('provides only non-synthetic stages', window.inject(function() {
      configurer.registerStage({type: 'a'});
      configurer.registerStage({type: 'b', synthetic: true});
      expect(configurer.$get().getStageTypes().length).toBe(2);
      expect(configurer.$get().getConfigurableStageTypes().length).toBe(1);
    }));
  });

});
