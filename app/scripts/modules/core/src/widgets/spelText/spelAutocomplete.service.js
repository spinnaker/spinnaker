'use strict';

import { EXECUTION_SERVICE } from 'core/pipeline/service/execution.service';
import { SpelAutocompleteService } from './SpelAutocompleteService';

import { module } from 'angular';

export const CORE_WIDGETS_SPELTEXT_SPELAUTOCOMPLETE_SERVICE = 'spinnaker.core.widget.spelAutocomplete';
export const name = CORE_WIDGETS_SPELTEXT_SPELAUTOCOMPLETE_SERVICE; // for backwards compatibility
module(CORE_WIDGETS_SPELTEXT_SPELAUTOCOMPLETE_SERVICE, [EXECUTION_SERVICE]).factory('spelAutocomplete', [
  '$q',
  'executionService',
  ($q, executionService) => {
    const autocomplete = new SpelAutocompleteService($q, executionService);
    return {
      textcompleteConfig: autocomplete.textcompleteConfig,
      addPipelineInfo: (pipelineConfig) => autocomplete.addPipelineInfo(pipelineConfig),
    };
  },
]);
