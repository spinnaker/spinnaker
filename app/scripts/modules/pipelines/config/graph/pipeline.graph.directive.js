'use strict';

angular.module('deckApp.pipelines.graph.directive', [
  'deckApp.utils.d3',
  'deckApp.utils.lodash',
  'deckApp.utils.jQuery',
])
  .directive('pipelineGraph', function ($window, d3Service, _, $) {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        viewState: '=',
        onNodeClick: '=',
      },
      templateUrl: 'scripts/modules/pipelines/config/graph/pipelineGraph.directive.html',
      link: function (scope, elem) {

        scope.nodeRadius = 8;
        scope.rowPadding = 30;
        scope.graphVerticalPadding = 15;
        scope.labelOffsetX = scope.nodeRadius + 3;
        scope.labelOffsetY = scope.nodeRadius + 10;

        /**
         * Used to draw inverse bezier curve between stages
         */
        var diagonal = d3Service.svg.diagonal()
          .source(function(d) { return {'x': d.source.y, 'y': d.source.x + scope.nodeRadius}; })
          .target(function(d) { return {'x': d.target.y, 'y': d.target.x - scope.nodeRadius}; })
          .projection(function (d) {
            return [d.y, d.x];
          });


        scope.nodeClicked = function(node) {
          scope.onNodeClick(node.section, node.index);
        };

        scope.highlight = function(node) {
          if (node.isActive) {
            return;
          }
          node.isHighlighted = true;
          node.parentLinks.forEach(function(link) {
            link.isHighlighted = true;
            link.className = 'highlighted target';
          });
          node.childLinks.forEach(function(link) {
            link.isHighlighted = true;
            link.className = 'highlighted source';
          });
        };

        scope.removeHighlight = function(node) {
          if (node.isActive) {
            return;
          }
          node.isHighlighted = false;
          node.parentLinks.forEach(function(link) {
            link.isHighlighted = false;
            link.className = link.parent.isActive ? 'active source' : 'inactive';
          });
          node.childLinks.forEach(function(link) {
            link.isHighlighted = false;
            link.className = link.child.isActive ? 'active target' : 'inactive';
          });
        };

        /**
         * Creates base configuration for all nodes;
         *  - does not set phase, position, or create links between nodes
         */
        function createNodes() {
          var triggersNode = {
              name: 'Triggers (' + scope.pipeline.triggers.length + ')',
              phase: 0,
              id: -1,
              section: 'triggers',
              parentIds: [],
              parents: [],
              children: [],
              parentLinks: [],
              childLinks: [],
              isActive: scope.viewState.section === 'triggers',
              isHighlighted: false,
            },
            nodes = [triggersNode];

          scope.pipeline.parallel = true;
          scope.pipeline.stages.forEach(function(stage, idx) {
            stage.requisiteStageRefIds = stage.requisiteStageRefIds || [];
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
              isActive: scope.viewState.stageIndex === idx && scope.viewState.section === 'stage',
              isHighlighted: false,
            };
            if (!node.parentIds.length) {
              node.parentIds.push(triggersNode.id);
            }
            nodes.push(node);
          });
          return nodes;
        }

        /**
         * Sets phases and adds children/parents to nodes
         * Probably blows the stack if circular dependencies exist, maybe not
         */
        function applyPhasesAndLink(nodes) {
          nodes = nodes || createNodes();
          var allPhasesResolved = true;
          nodes.forEach(function(node) {
            var phaseResolvable = true,
                phase = 0;
            if (!node.parentIds.length) {
              node.phase = phase;
            } else {
              node.parentIds.forEach(function(parentId) {
                var parent = _.find(nodes, { id: parentId });
                if (parent.phase === undefined) {
                  phaseResolvable = false;
                } else {
                  phase = Math.max(phase, parent.phase);
                  parent.children.push(node);
                  node.parents.push({node: parent});
                }
              });
              if (phaseResolvable) {
                node.phase = phase + 1;
              } else {
                allPhasesResolved = false;
              }
            }
          });
          if (!allPhasesResolved) {
            applyPhasesAndLink(nodes);
          } else {
            scope.phaseCount = _.max(nodes, 'phase').phase;
            if (scope.phaseCount > 6) {
              scope.nodeRadius = 6;
              scope.labelOffsetX = scope.nodeRadius + 3;
              scope.labelOffsetY = 15;
            }
            scope.nodes = [];
            nodes.forEach(function(node) {
              node.children = _.uniq(node.children);
              node.parents = _.uniq(node.parents);
              if (!node.children.length) {
                node.phase = scope.phaseCount;
              }
            });

            // Collision minimization "Algorithm"
            nodes = _.sortByAll(nodes,
              'phase',
              function(node) {
                return _.sortBy(node.parents, 'phase').map(function(parent) {
                  return [(node.phase - parent.phase), parent.name].join('-');
                });
              },
              function(node) {
                return _.sortBy(node.children, 'phase').map(function(child) {
                  return [(child.phase - node.phase), child.name].join('-');
                });
              },
              'name'
            );
            nodes.forEach(function(node) {
              scope.nodes[node.phase] = scope.nodes[node.phase] || [];
              scope.nodes[node.phase].push(node);
            });
          }
        }

        /**
         * Sets the width of the graph and determines the width available for each label
         */
        function applyPhaseWidth() {
          scope.graphWidth = elem.width() - (2 * scope.nodeRadius);
          var maxLabelWidth = scope.graphWidth;

          if (scope.phaseCount) {
            maxLabelWidth = (scope.graphWidth / (scope.phaseCount + 1)) - (2*scope.nodeRadius) - scope.labelOffsetX;
          }

          scope.maxLabelWidth = maxLabelWidth;
        }

        function applyNodeHeights() {
          var placeholderNode = elem.find('g.placeholder div');
          placeholderNode.width(scope.maxLabelWidth);
          scope.graphHeight = 0;
          scope.nodes.forEach(function(nodes) {
            nodes.forEach(function(node) {
              placeholderNode.html('<a href>' + node.name + '</a>');
              node.height = placeholderNode.height() + scope.rowPadding;
            });
            scope.graphHeight = Math.max(_.sum(nodes, 'height'), scope.graphHeight);
          });
          placeholderNode.empty();
          scope.graphHeight += 3*scope.graphVerticalPadding;
        }

        function setNodePositions() {
          scope.nodes.forEach(function(nodes, idx) {
            var nodeOffset = scope.graphVerticalPadding;
            nodes.forEach(function(node, rowNumber) {
              node.x = (scope.maxLabelWidth + 2*scope.nodeRadius + scope.labelOffsetX) * idx;
              node.y = nodeOffset;
              nodeOffset += scope.rowHeights[rowNumber];
            });
          });
        }

        function createLinks() {
          scope.nodes.forEach(function(column) {
            column.forEach(function(node) {
              node.children.forEach(function(child) {
                var linkClass = node.isActive ? 'active source' :
                  child.isActive ? 'active target' : 'inactive';
                var link = {
                  parent: node,
                  child: child,
                  className: linkClass,
                  line: diagonal({ source: node, target: child })
                };
                node.childLinks.push(link);
                child.parentLinks.push(link);
              });
            });
          });
        }

        function applyAllNodes() {
          var flattened = _.flatten(scope.nodes),
            highlighted = _.find(flattened, 'isHighlighted'),
            active = _.find(flattened, 'isActive'),
            base = _.filter(flattened, {isActive: false, isHighlighted: false});

          scope.allNodes = base;
          if (highlighted) {
            base.push(highlighted);
          }
          if (active) {
            base.push(active);
          }
        }

        function establishRowHeights() {
          var rowHeights = [];
          scope.nodes.forEach(function(column) {
            column.forEach(function(node, rowNumber) {
              if (!rowHeights[rowNumber]) {
                rowHeights[rowNumber] = 0;
              }
              rowHeights[rowNumber] = Math.max(rowHeights[rowNumber], node.height);
            });
          });
          scope.rowHeights = rowHeights;
          scope.graphHeight = _.sum(scope.rowHeights) + 2*scope.graphVerticalPadding;
        }


        function updateGraph() {
          applyPhasesAndLink();
          applyPhaseWidth();
          applyNodeHeights();
          establishRowHeights();
          setNodePositions();
          createLinks();
          applyAllNodes();

        }

        var handleWindowResize = _.throttle(function() {
          scope.$evalAsync(updateGraph);
        }, 300);

        updateGraph();

        scope.$watch('pipeline', updateGraph, true);
        scope.$watch('viewState', updateGraph, true);
        $($window).bind('resize.pipelineGraph-' + scope.pipeline.name, handleWindowResize);

        scope.$on('$destroy', function() {
          $($window).unbind('resize.pipelineGraph-' + scope.pipeline.name);
        });

      },
    };
  });
