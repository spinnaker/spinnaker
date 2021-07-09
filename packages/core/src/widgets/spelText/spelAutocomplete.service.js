'use strict';

import { module } from 'angular';

import { SpelAutocompleteService } from './SpelAutocompleteService';
import { EXECUTION_SERVICE } from '../../pipeline/service/execution.service';

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
