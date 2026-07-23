import { module } from 'angular';

import { PipelineTemplatesV2 } from './PipelineTemplatesV2';
import type { INestedState } from '../../../../navigation';
import { registerRootState } from '../../../../navigation/rootState.registration';

export const PIPELINE_TEMPLATES_V2_STATES_CONFIG = 'spinnaker.core.pipeline.templates.v2.states.config';

module(PIPELINE_TEMPLATES_V2_STATES_CONFIG, []);

registerRootState((stateConfigProvider) => {
  const pipelineTemplateDetail: INestedState = {
    name: 'pipeline-templates-detail',
    url: '/:templateId',
    data: {
      pageTitleMain: {
        label: 'Pipeline Templates',
      },
    },
  };

  const pipelineTemplatesList: INestedState = {
    name: 'pipeline-templates',
    url: '/pipeline-templates',
    views: {
      'main@': {
        component: PipelineTemplatesV2,
        $type: 'react',
      },
    },
    data: {
      pageTitleMain: {
        label: 'Pipeline Templates',
      },
    },
    children: [pipelineTemplateDetail],
  };

  stateConfigProvider.addToRootState(pipelineTemplatesList);
});
