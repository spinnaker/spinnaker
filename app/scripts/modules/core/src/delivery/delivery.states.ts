import { module } from 'angular';

import { INestedState, StateConfigProvider } from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
} from 'core/application/application.state.provider';
import { filterModelConfig } from './filter/executionFilter.model';

export const DELIVERY_STATES = 'spinnaker.core.delivery.states';
module(DELIVERY_STATES, [
  APPLICATION_STATE_PROVIDER
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

  const pipelineConfig: INestedState = {
    name: 'pipelineConfig',
    url: '/configure/:pipelineId',
    views: {
      'pipelines': {
        templateUrl: require('../pipeline/config/pipelineConfig.html'),
        controller: 'PipelineConfigCtrl',
        controllerAs: 'vm',
      },
    },
    data: {
      pageTitleSection: {
        title: 'pipeline config'
      }
    }
  };

  // a specific stage can be deep linked by providing either refId or stageId,
  // which will be resolved to stage or step by the executionDetails controller to stage/step parameters,
  // replacing the URL
  const executionDetails: INestedState = {
    name: 'execution',
    url: '/:executionId?refId&stage&step&details&stageId',
    params: {
      stage: {
        value: '0',
      },
      step: {
        value: '0',
      },
      refId: {
        value: null,
      }
    },
    data: {
      pageTitleDetails: {
        title: 'Execution Details',
        nameParam: 'executionId'
      }
    }
  };

  const singleExecutionDetails: INestedState = {
    name: 'executionDetails',
    url: '/details',
    views: {
      'pipelines': {
        templateUrl: require('./details/singleExecutionDetails.html'),
        controller: 'SingleExecutionDetailsCtrl',
        controllerAs: 'vm',
      },
    },
    abstract: true,
    children: [executionDetails],
  };

  const executions: INestedState = {
    name: 'executions',
    url: `?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
    views: {
      'pipelines': {
        template: '<executions application="$resolve.app"></executions>',
      },
    },
    params: stateConfigProvider.buildDynamicParams(filterModelConfig),
    children: [executionDetails],
    data: {
      pageTitleSection: {
        title: 'Pipeline Executions'
      }
    }
  };

  const pipelines: INestedState = {
    name: 'pipelines',
    url: '/executions',
    abstract: true,
    views: {
      'insight': {
        template: '<div ui-view="pipelines" sticky-headers class="flex-fill"></div>'
      }
    },
    children: [executions, pipelineConfig, singleExecutionDetails]
  };

  applicationStateProvider.addChildState(pipelines);
});
