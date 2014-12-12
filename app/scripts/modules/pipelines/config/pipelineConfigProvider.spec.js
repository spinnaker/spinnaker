'use strict';

describe('authenticationProvider: application startup', function() {
  var configurer;

  describe('registration', function() {
    beforeEach(
      module('deckApp.pipelines', function(pipelineConfigProvider) {
        configurer = pipelineConfigProvider;
      }));

    it('registers triggers', inject(function() {
      expect(configurer.$get().getTriggerTypes().length).toBe(0);
      configurer.registerTrigger({type: 'a'});
      configurer.registerTrigger({type: 'b'});
      expect(configurer.$get().getTriggerTypes().length).toBe(2);
    }));

    it('registers stages', inject(function() {
      expect(configurer.$get().getStageTypes().length).toBe(0);
      configurer.registerStage({type: 'a'});
      configurer.registerStage({type: 'b'});
      expect(configurer.$get().getStageTypes().length).toBe(2);
    }));
  });

});
