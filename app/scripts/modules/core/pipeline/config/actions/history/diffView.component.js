'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.diffView.component', [
    require('../../../../utils/jQuery'),
  ])
  .component('diffView', {
    bindings: {
      diff: '='
    },
    controller: function ($) {
      this.scrollBody = function(e) {
        let line = 1;
        if (e.target.getAttribute('data-attr-block-line')) {
          line = e.target.getAttribute('data-attr-block-line');
        } else {
          line = parseInt(e.offsetY / e.currentTarget.clientHeight * this.diff.summary.total);
        }
        $('pre.history').animate({scrollTop: (line - 3) * 15}, 200);
      };
    },
    template: `
        <pre class="form-control flex-fill history">
          <div data-attr-line="{{$index}}"
               ng-repeat="line in $ctrl.diff.details"
               class="{{line.type}}">{{line.text}}</div>
        </pre>
        <div class="summary-nav flex-fill"
             ng-click="$ctrl.scrollBody($event)">
          <div ng-repeat="block in $ctrl.diff.changeBlocks"
               data-attr-block-line="{{block.start}}"
               style="height: {{block.height}}%; top: {{block.top}}%"
               class="delta {{block.type}}">
          </div>
        </div>
`
  });
