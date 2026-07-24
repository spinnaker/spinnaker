import type { Transition } from '@uirouter/core';
import { UIView } from '@uirouter/react';
import { module } from 'angular';
import React from 'react';

import type { ApplicationStateProvider } from '../application/application.state.provider';
import { registerApplicationState } from '../application/applicationState.registration';
import { PipelineConfigPage } from './config/PipelineConfigPage';
import { SingleExecutionDetails } from './details/SingleExecutionDetails';
import { ExecutionNotFound } from './executions/ExecutionNotFound';
import { Executions } from './executions/Executions';
import { filterModelConfig } from './filter/ExecutionFilterModel';
import { registerRootState } from '../navigation/rootState.registration';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';

export const PIPELINE_STATES = 'spinnaker.core.pipeline.states';

const PipelineInsightView = ({ className }: { className?: string }) =>
  React.createElement(
    'div',
    { className: ['flex-fill', className].filter(Boolean).join(' ') },
    React.createElement(
      'div',
      { className: 'flex-container-h flex-grow' },
      React.createElement(UIView, { name: 'pipelines', className: 'flex-fill' }),
    ),
  );

module(PIPELINE_STATES, []);

registerApplicationState(
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    const pipelineConfig: INestedState = {
      name: 'pipelineConfig',
      url: '/configure/:pipelineId?executionId&new',
      views: {
        pipelines: {
          component: PipelineConfigPage,
          $type: 'react',
        },
      },
      data: {
        pageTitleSection: {
          title: 'pipeline config',
        },
      },
    };

    // a specific stage can be deep linked by providing either refId or stageId,
    // which will be resolved to stage or step by the executionDetails controller to stage/step parameters,
    // replacing the URL
    const executionDetails: INestedState = {
      name: 'execution',
      url: '/:executionId?refId&stage&subStage&step&details&stageId',
      params: {
        stage: {
          value: '0',
        },
        step: {
          value: '0',
        },
      },
      data: {
        pageTitleDetails: {
          title: 'Execution Details',
          nameParam: 'executionId',
        },
      },
    };

    const singleExecutionDetails: INestedState = {
      name: 'executionDetails',
      url: '/details',
      views: {
        pipelines: { component: SingleExecutionDetails, $type: 'react' },
      },
      params: {
        executionParams: null,
      },
      abstract: true,
      children: [executionDetails],
    };

    const executions: INestedState = {
      name: 'executions',
      url: `?startManualExecution&${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
      views: {
        pipelines: { component: Executions, $type: 'react' },
      },
      params: stateConfigProvider.buildDynamicParams(filterModelConfig),
      children: [executionDetails],
      data: {
        pageTitleSection: {
          title: 'Pipeline Executions',
        },
      },
    };

    const pipelines: INestedState = {
      name: 'pipelines',
      url: '/executions',
      redirectTo: (trans: Transition) => `${trans.to().name}.executions`,
      views: {
        insight: {
          component: PipelineInsightView,
          $type: 'react',
        },
      },
      children: [executions, pipelineConfig, singleExecutionDetails],
    };

    applicationStateProvider.addChildState(pipelines);
  },
);

registerRootState((stateConfigProvider) => {
  const { executionService } = stateConfigProvider.runtimeServices;
  const executionsLookup: INestedState = {
    name: 'executionLookup',
    url: '/executions/:executionId?refId&stage&subStage&step&details&stageId',
    params: {
      executionId: { dynamic: true },
    },
    redirectTo: (transition) => {
      const { executionId, refId, stage, subStage, step, details, stageId } = transition.params();
      if (!executionId) {
        return undefined;
      }

      return Promise.resolve(executionService.getExecution(executionId))
        .then((execution) =>
          transition.router.stateService.target('home.applications.application.pipelines.executionDetails.execution', {
            application: execution.application,
            executionId: execution.id,
            refId,
            stage,
            subStage,
            step,
            details,
            stageId,
          }),
        )
        .catch(() => {});
    },
    views: {
      'main@': { component: ExecutionNotFound, $type: 'react' },
    },
  };

  stateConfigProvider.addToRootState(executionsLookup);
});
