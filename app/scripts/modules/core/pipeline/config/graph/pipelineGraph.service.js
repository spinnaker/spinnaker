'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.graph.pipelineGraph.service', [

  ])
  .factory('pipelineGraphService', function () {

    function generateExecutionGraph(execution, viewState) {
      let nodes = [];
      (execution.stageSummaries || []).forEach(function(stage, idx) {
        var node = {
          id: stage.refId,
          name: stage.name,
          index: idx,
          parentIds: angular.copy(stage.requisiteStageRefIds || []),
          stage: stage,
          masterStage: stage.masterStage,
          labelTemplateUrl: stage.labelTemplateUrl,
          parents: [],
          children: [],
          parentLinks: [],
          childLinks: [],
          isActive: viewState.activeStageId === stage.index && viewState.executionId === execution.id,
          isHighlighted: false,
          status: stage.status,
          executionStage: true,
          hasNotStarted: stage.hasNotStarted,
          executionId: execution.id,
        };
        if (!node.parentIds.length) {
          node.root = true;
        }
        nodes.push(node);
      });

      return nodes;
    }

    function generateConfigGraph(pipeline, viewState) {
      let nodes = [];
      var configNode = {
            name: 'Configuration',
            phase: 0,
            id: -1,
            section: 'triggers',
            parentIds: [],
            parents: [],
            children: [],
            parentLinks: [],
            childLinks: [],
            root: true,
            isActive: viewState.section === 'triggers',
            isHighlighted: false,
          };
      nodes.push(configNode);

      pipeline.stages.forEach(function(stage, idx) {
        var node = {
          id: stage.refId,
          name: stage.name || '[new stage]',
          section: 'stage',
          index: idx,
          parentIds: angular.copy(stage.requisiteStageRefIds || []),
          parents: [],
          children: [],
          parentLinks: [],
          childLinks: [],
          color: null,
          isActive: viewState.stageIndex === idx && viewState.section === 'stage',
          isHighlighted: false,
        };
        if (!node.parentIds.length) {
          node.parentIds.push(configNode.id);
        }
        nodes.push(node);
      });

      return nodes;
    }

    return {
      generateExecutionGraph: generateExecutionGraph,
      generateConfigGraph: generateConfigGraph,
    };

  });
