const angular = require('angular');

import { ENTITY_TAGS_MODULE } from 'core/entityTag/entityTags.module';

import './applicationNav.component.less';

module.exports = angular
  .module('spinnaker.core.application.nav.component', [
    require('angular-ui-router').default,
    ENTITY_TAGS_MODULE,
  ])
  .component('applicationNav', {
    bindings: {
      application: '<',
    },
    template: `
      <div ng-if="!$ctrl.application.notFound" class="nav-section">
        <a ng-repeat="dataSource in $ctrl.getDataSources()" 
           ui-sref="{{dataSource.sref}}"
           analytics-on="click"
           analytics-category="Application Nav"
           analytics-event="{{dataSource.title}}"
           ng-class="{active: $ctrl.isActive(dataSource)}"
        >
          <i ng-if="dataSource.icon" class="ds-icon fa fa-{{dataSource.icon}}"></i>
          {{dataSource.label}}
          
          <x-data-source-notifications
            tags="dataSource.alerts"
            application="$ctrl.application"
            tab-name="dataSource.key"
          ></x-data-source-notifications>
          
          <span class="badge"
                ng-if="dataSource.badge && $ctrl.application[dataSource.badge].data.length">
            {{$ctrl.application[dataSource.badge].data.length}}
          </span>
          <span class="small fa fa-exclamation-circle"
                ng-if="dataSource.badge && $ctrl.application[dataSource.badge].loadFailure"
                uib-tooltip="There was an error loading data for {{dataSource.title}}. We'll try again shortly."></span>
        </a>
      </div>
`,
    controller: function ($state) {
      this.isActive = (dataSource) => $state.includes(dataSource.activeState);

      this.getDataSources = () => {
        return (this.application.dataSources || []).filter(ds => ds.visible !== false && !ds.disabled && ds.primary);
      };
    }
  });
