import {DATA_SOURCE_ALERTS_COMPONENT} from 'core/entityTag/dataSourceAlerts.component';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.nav.component', [
    require('angular-ui-router'),
    DATA_SOURCE_ALERTS_COMPONENT,
  ])
  .component('applicationNav', {
    bindings: {
      application: '<',
    },
    template: `
      <ul class="nav nav-pills" ng-if="!$ctrl.application.notFound">
        <li ng-repeat="dataSource in $ctrl.application.dataSources" ng-if="dataSource.visible !== false && !dataSource.disabled">
          <a ui-sref="{{dataSource.sref}}"
             analytics-on="click"
             analytics-category="Application Nav"
             analytics-event="{{dataSource.title}}"
             ng-class="{active: $ctrl.isActive(dataSource)}">
            {{dataSource.label}}
             <ds-alerts alerts="dataSource.alerts"></ds-alerts>
            <span class="badge"
                  ng-if="dataSource.badge && $ctrl.application[dataSource.badge].data.length">
              {{$ctrl.application[dataSource.badge].data.length}}
            </span>
            <span class="small glyphicon glyphicon-exclamation-sign"
                  ng-if="dataSource.badge && $ctrl.application[dataSource.badge].loadFailure"
                  uib-tooltip="There was an error loading data for {{dataSource.title}}. We'll try again shortly."></span>
          </a>
        </li>
      </ul>
`,
    controller: function ($state) {
      this.isActive = (dataSource) => $state.includes(dataSource.activeState);
    }
  });
