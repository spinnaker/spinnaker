import { PipelineTemplatesV2 } from './PipelineTemplatesV2';
import type { INestedState } from '../../../../navigation';
import { registerRootState } from '../../../../navigation/rootState.registration';

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
