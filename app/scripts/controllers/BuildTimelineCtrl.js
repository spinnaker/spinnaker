'use strict';

angular.module('deckApp')
  .controller('BuildTimelineCtrl', function($scope, $element, d3Service, momentService) {
    var data = $scope.executions.filter(function(execution) {
      return execution.trigger.type === 'jenkins' && execution.status === 'COMPLETED';
    }).sort(function(a,b) {
      return a.endTime - b.endTime;
    }).map(function(execution) {
      return {
        build: execution.trigger.buildInfo.lastBuildLabel,
        timeStamp: momentService(execution.endTime).toDate(),
      };
    });

    var margin = {
      top: 30,
      right: 30,
      bottom: 30,
      left: 50
    };
    var height = 300 - margin.top - margin.bottom;
    var width = $element.get()[0].clientWidth - margin.left - margin.right;

    var x = d3Service
      .time
      .scale()
      .range([0, width]);

    var y = d3Service
      .scale
      .linear()
      .range([height, 0]);

    var xAxis = d3Service
      .svg
      .axis()
      .scale(x)
      .orient('bottom');

    var yAxis = d3Service
      .svg
      .axis()
      .scale(y)
      .orient('left');

    var svg = d3Service
      .select($element.children()[0])
      .append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

    x.domain(d3Service.extent(data, function(d) { return d.timeStamp; }));
    y.domain(d3Service.extent(data, function(d) { return d.build; }));

    svg.append('g')
      .attr('class', 'x axis')
      .attr('transform', 'translate(0,'+height+')')
      .call(xAxis);

    svg.append('g')
      .attr('class', 'y axis')
      .call(yAxis)
      .append('g')
      .attr('transform', 'translate(-50)')
      .append('text')
      .attr('transform', 'rotate(-90)')
      .attr('y', 6)
      .attr('dy', '.71em')
      .style('text-anchor', 'end')
      .text('Build Version');

    svg.append('path')
      .datum(data)
      .attr('class', 'line')
      .attr('d', d3Service
        .svg
        .line()
        .x(function(d) { return x(d.timeStamp); })
        .y(function(d) { return y(d.build); })
        .interpolate('step'));

    $scope.$destroy();
  });
