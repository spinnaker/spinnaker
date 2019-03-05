'use strict';

import { EXECUTION_SERVICE } from 'core/pipeline/service/execution.service';
import { SpelAutocompleteService } from './SpelAutocompleteService';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widget.spelAutocomplete', [EXECUTION_SERVICE])
  .factory('spelAutocomplete', ($q, executionService) => {
    const autocomplete = new SpelAutocompleteService($q, executionService);
    return {
      textcompleteConfig: autocomplete.textcompleteConfig,
      addPipelineInfo: pipelineConfig => autocomplete.addPipelineInfo(pipelineConfig),
    };
  });
