'use strict';

angular.module('deckApp.pipelines.graph.directive', [
  'deckApp.utils.d3',
  'deckApp.utils.lodash',
])
  .directive('pipelineGraph', function (d3Service, _) {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        viewState: '=',
        onNodeClick: '=',
      },
      templateUrl: 'scripts/modules/pipelines/config/graph/pipelineGraph.directive.html',
      link: function (scope, elem) {

        function applyPhases() {
          var allPhasesResolved = true;
          scope.pipeline.stages.forEach(function(stage) {
            var phaseResolvable = true,
              phase = 1;
            if (!stage.requisiteStageRefIds || !stage.requisiteStageRefIds.length) {
              stage.phase = phase;
            } else {
              stage.requisiteStageRefIds.forEach(function(parentId) {
                var parent = _.find(scope.pipeline.stages, { refId: parentId });
                if (!parent.phase) {
                  phaseResolvable = false;
                } else {
                  phase = Math.max(phase, parent.phase);
                  parent.hasChildren = true;
                }
              });
              if (phaseResolvable) {
                stage.phase = phase + 1;
              } else {
                allPhasesResolved = false;
              }
            }
          });
          if (!allPhasesResolved) {
            applyPhases();
          } else {
            var terminalPhase = _.max(scope.pipeline.stages, 'phase').phase;
            scope.pipeline.stages.forEach(function(stage) {
              if (!stage.hasChildren) {
                stage.phase = terminalPhase;
              }
            });
          }
        }

        var d3 = d3Service;

        function drawGraph() {

          applyPhases();

          elem.find('g').empty();
          var stages = scope.pipeline.stages;

          var phaseCount = _.max(stages, 'phase').phase + 1;

          var graphWidth = 800;

          var maxLabelWidth = graphWidth / phaseCount - 16;

          var rowPadding = 30;

          var circleRadius = 8;

          var rowHeights = [];

          function setRow(node, idx) {
            node.row = idx;
          }

          function setPosition(node) {
            if (!node.x) {
              var row = node.row;
              if (row === 0) {
                node.y = 0;
              } else {
                node.y = rowHeights[row-1].offset + rowHeights[row-1].height + rowPadding;
              }
              node.x = ((node.phase) / phaseCount) * 800;
            }
          }

          var triggersNode = {
            name: 'Triggers (' + scope.pipeline.triggers.length + ')',
            phase: 0,
            section: 'triggers',
            isActive: scope.viewState.section === 'triggers',
          };

          var vis = d3.select(elem.find('g')[0]).attr('transform', 'translate(50, 50)');

          var diagonal = d3.svg.diagonal()
            .source(function(d) { return {'x': d.source.y, 'y': d.source.x}; })
            .target(function(d) { return {'x': d.target.y, 'y': d.target.x}; })
            .projection(function (d) {
              return [d.y, d.x];
            });

          var nodeSrc = [triggersNode],
            linkSrc = [];

          stages.forEach(function (stage, idx) {
            nodeSrc.push({
              id: stage.refId,
              name: stage.name || '[new stage]',
              phase: stage.phase,
              index: idx,
              section: 'stage',
              isActive: scope.viewState.stageIndex === idx && scope.viewState.section === 'stage',
            });
          });

          stages.forEach(function (stage) {
            if (!stage.requisiteStageRefIds.length) {
              var targetNode = _.find(nodeSrc, { id: stage.refId });
              if (triggersNode.isActive) {
                targetNode.isConnected = true;
              }
              linkSrc.push({
                source: triggersNode,
                target: targetNode,
              });
            }
            stage.requisiteStageRefIds.forEach(function (parentId) {
              var source = _.find(nodeSrc, { id: parentId }),
                  target = _.find(nodeSrc, { id: stage.refId });
              if (source.isActive) {
                target.isConnected = true;
              }
              if (target.isActive) {
                source.isConnected = true;
              }
              linkSrc.push({
                source: source,
                target: target,
              });
            });
          });

          nodeSrc = _.sortByAll(nodeSrc, ['phase', 'name']);

          linkSrc.sort(function(a) {
            if (a.source.isActive || a.target.isActive) {
              return 1;
            }
            return -1;
          });

          var rowCount = 1;
          var grouped = _.groupBy(nodeSrc, 'phase');
          _.forOwn(grouped, function (val) {
            val.forEach(setRow);
            rowCount = Math.max(rowCount, val.length);
          });

          function buildNodeMarkers() {
            var nodes = vis.selectAll('g.node').data(nodeSrc);
            var nodeEnter = nodes.enter().append('g')
              .attr('class', function (d) {
                var baseClass = 'clickable node';
                if (d.isActive) {
                  baseClass += ' active';
                }
                if (d.isConnected) {
                  baseClass += ' connected';
                }
                return baseClass;
              })
              .on('click', function (d) {
                scope.onNodeClick(d.section, d.index);
              });
            nodeEnter.append('circle')
              .attr('r', circleRadius)
              .attr('fill', '#999');
            nodes.attr('transform', function (d) {
              return 'translate(' + d.x + ', ' + d.y + ')';
            });
          }

          function buildNodeLabels() {
            var labels = vis.selectAll('g.label').data(nodeSrc);
            labels.enter().append('foreignObject')
              .attr('x', function (d) {
                return d.x + 8;
              })
              .attr('y', function (d) {
                return d.y - 15;
              })
              .attr('width', maxLabelWidth)
              .attr('height', 100)
              .append('xhtml:body')
              .attr('class', 'label-body')
              .append('div')
              .text(function (d) {
                return d.name;
              })
              .attr('class', function (d) {
                var baseClass = 'clickable node row-' + d.row;
                if (d.isActive) {
                  baseClass += ' active';
                }
                if (d.isConnected) {
                  baseClass += ' connected';
                }
                return baseClass;
              })
              .on('click', function (d) {
                scope.onNodeClick(d.section, d.index);
              });
          }
          function buildNodeLinks() {
            var links = vis.selectAll('line.link').data(linkSrc);
            links.enter()
              .insert('svg:path', 'g')
              .attr('class', function (d) {
                if (d.source.isActive) {
                  return 'link source active';
                }
                if (d.target.isActive) {
                  return 'link target active';
                }
                return 'link';
              })
              .attr('d', function (d) {
                return diagonal({ source: d.source, target: d.target});
              });
          }

          var graphHeight = 0;

          function buildRowHeight() {
            var heights = [];
            var labelsToMeasure = vis.selectAll('g.placeholder').data(nodeSrc);
            labelsToMeasure.enter().append('foreignObject')
              .attr('width', maxLabelWidth)
              .attr('height', 100)
              .append('xhtml:body')
              .attr('class', 'label-body placeholder')
              .append('div')
              .attr('data-row', function(d) { return d.row; })
              .text(function(d) {
                return d.name;
              });

            elem.find('.label-body.placeholder div').each(function() {
              var $label = $(this),
                  row = $label.attr('data-row');
              if (!heights[row]) {
                heights[row] = $label.height();
              } else {
                heights[row] = Math.max($label.height(), heights[row]);
              }
            });
            rowHeights = heights.map(function(row) {
              var result = {
                height: row,
                offset: graphHeight,
              };
              graphHeight += (row + rowPadding);
              return result;
            });
            labelsToMeasure.remove();

          }

          buildRowHeight();
          nodeSrc.forEach(setPosition);
          buildNodeLinks();
          buildNodeMarkers();
          buildNodeLabels();
          elem.find('svg').width(graphWidth + 50).height(graphHeight + 50);

          // TODO: Calculate height, row height based on label heights via class="row-x"
        }

        drawGraph();

        scope.$watch('pipeline', drawGraph, true);
        scope.$watch('viewState', drawGraph, true);

      },
    };
  });
