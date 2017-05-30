import {module, IComponentController, IComponentOptions} from 'angular';
import {Application} from '../application.model';
import {ApplicationDataSource} from '../service/applicationDataSource';
import {StateService} from 'angular-ui-router';

class SecondaryApplicationNavComponentController implements IComponentController {

  public application: Application;

  constructor(private $state: StateService) { 'ngInject'; }

  public isActive(dataSource: ApplicationDataSource) {
    return this.$state.includes(dataSource.activeState);
  }

  public getSecondaryDataSources(): ApplicationDataSource[] {
    return (this.application.dataSources || []).filter(ds => ds.visible !== false && !ds.disabled && !ds.primary);
  }
}

class SecondaryApplicationNavComponent implements IComponentOptions {
  public bindings: any = {
    application: '=',
  };
  public controller: any = SecondaryApplicationNavComponentController;
  public template = `
    <div ng-if="!$ctrl.application.notFound">
      <a ng-repeat="dataSource in $ctrl.getSecondaryDataSources()"
         ui-sref="{{dataSource.sref}}"
         analytics-on="click"
         analytics-category="Application Nav"
         analytics-event="{{dataSource.title}}"
         ng-class="{active: $ctrl.isActive(dataSource)}">
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
  `;
}

export const SECONDARY_APPLICATION_NAV_COMPONENT = 'spinnaker.core.application.nav.secondaryNav.component';
module(SECONDARY_APPLICATION_NAV_COMPONENT, [])
  .component('secondaryApplicationNav', new SecondaryApplicationNavComponent());
