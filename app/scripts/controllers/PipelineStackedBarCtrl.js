'use strict';

angular.module('deckApp')
  .controller('PipelineStackedBarCtrl', function($scope, $element, d3Service, momentService) {
    var data = $scope.executions;
    $scope.timescale = 'absolute';

    var margin = {
      top: 30,
      right: 120,
      bottom: 30,
      left: 120
    };

    var width = $element.get()[0].clientWidth - margin.left - margin.right;
    var height = 300 - margin.top - margin.bottom;

    var x = d3Service
      .scale
      .linear()
      .rangeRound([0, width]);

    var xAxis = d3Service
      .svg
      .axis()
      .scale(x)
      .tickFormat(function(t) {
        return momentService.duration(t, 'milliseconds').humanize();
      })
      .orient('bottom');

    var y = d3Service
      .scale
      .ordinal()
      .rangeRoundBands([height, 0], 0.1);

    var yAxis = d3Service
      .svg
      .axis()
      .scale(y)
      .tickFormat(function(t) {
        return momentService(data[t].startTime).calendar();
      })
      .orient('left');

    var color = d3Service
      .scale
      .ordinal()
      .range(['rgb(127,201,127)','rgb(190,174,212)','rgb(253,192,134)']);
      //.range(['#e41a1c','#377eb8','#4daf4a','#984ea3','#ff7f00','#ffff33','#a65628','#f781bf','#999999']);
      //.range(['#66c2a5','#fc8d62','#8da0cb','#e78ac3','#a6d854','#ffd92f','#e5c494','#b3b3b3']);

    var svg = d3Service
      .select($element.children()[0])
      .append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

    y.domain(data.map(function(d,i) {
      return i;
    }));

    color(Object.keys(data.reduce(function(acc, cur) {
      cur.stages.forEach(function(stage) {
        acc[stage.name] = true;
      });
      return acc;
    }, {})));

    svg.append('g')
      .attr('class', 'x axis')
      .attr('transform', 'translate(0,' + height + ')')
      .call(xAxis);

    svg.append('g')
      .attr('class', 'y axis')
      .call(yAxis);

    var execution = svg.selectAll('.execution')
      .data(data)
      .enter()
      .append('g')
      .attr('class', 'g')
      .attr('transform', function(d, i) { return 'translate(0,'+y(i)+')'; });

    execution.selectAll('rect')
      .data(function(d) {
        return d.stages.map(function(stage) {
          stage.executionStartTime = d.startTime;
          stage.startTimePercentage = (stage.startTime-d.startTime) / (d.endTime-d.startTime);
          stage.endTimePercentage = (stage.endTime-d.startTime) / (d.endTime-d.startTime);
          return stage;
        });
      })
      .enter()
      .append('rect')
      .attr('height', y.rangeBand())
      .attr('class', 'what')
      .style('fill', function(d) { return color(d.name); });

      var legend = svg.selectAll('.legend')
        .data(color.domain()[0]) // why is this necessary?
        .enter().append('g')
        .attr('class', 'legend')
        .attr('transform', function(d, i) { return 'translate(20,' + ((height - 18) - (i * 20)) + ')'; });

      legend.append('rect')
        .attr('x', width - 18)
        .attr('width', 18)
        .attr('height', 18)
        .style('fill', color);

      legend.append('text')
        .attr('x', width + 10)
        .attr('y', 9)
        .attr('dy', '.35em')
        .style('text-anchor', 'start')
        .text(function(d) { return d; });

    /*
    execution.selectAll('text')
      .data(function(d) {
        return d.stages.map(function(stage) {
          stage.executionStart = d.startTime;
          return stage;
        });
      })
      .enter()
      .append('text')
      .attr('height', y.rangeBand())
      .attr('x', function(d) { return x(d.startTime) - x(d.executionStartTime); })
      .attr('width', function(d) { return x(d.endTime) - x(d.startTime) ; })
      .text(function(d) {
        return x(d.endTime) - x(d.startTime) > 10 ? d.name : '';
      })
      .attr('color', '#fff')
      .attr('text-anchoer', 'middle')
      .attr('dy', 25)
      .attr('dx', 5)
      .attr('class', 'vis-label');
      */

    $scope.$watch('timescale', function(scale) {
      var trans = svg
        .transition()
        .duration(150);

      switch (scale) {
        case 'relative': {
          x.domain([0,1]);

          trans
           .selectAll('rect.what')
            .attr('x', function(d) { 
              return x(d.startTimePercentage);
            })
            .attr('width', function(d) {
              return x(d.endTimePercentage) - x(d.startTimePercentage);
            });
          break;
        }
        case 'absolute': {
          x.domain([
            0,
            d3Service.max(data, function(d) {
              return d.endTime - d.startTime;
            }),
          ]);

          trans
            .selectAll('rect.what')
            .attr('x', function(d) { return x(d.startTime) - x(d.executionStartTime); })
            .attr('width', function(d) { return x(d.endTime) - x(d.startTime) ; });
          break;  
        }
      }
    });
  });
