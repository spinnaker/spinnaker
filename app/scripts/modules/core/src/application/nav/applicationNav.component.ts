import { module, IComponentController, IComponentOptions } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { Application } from 'core/application/application.model';
import { ApplicationDataSource } from 'core/application/service/applicationDataSource';

import './applicationNav.component.less';

class ApplicationNavController implements IComponentController {
  public application: Application;

  constructor(private $state: StateService) { 'ngInject'; }

  public isActive(dataSource: ApplicationDataSource): boolean {
    return this.$state.includes(dataSource.activeState);
  }

  public getDataSources(): ApplicationDataSource[] {
    const dataSources: ApplicationDataSource[] = this.application.dataSources || [];
    return dataSources.filter(ds => ds.visible !== false && !ds.disabled && ds.primary);
  };
}

export class ApplicationNavComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
  };

  public controller: any = ApplicationNavController;

  public template = `
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
  `;
}

export const APPLICATION_NAV_COMPONENT = 'spinnaker.core.application.nav.component';
module(APPLICATION_NAV_COMPONENT, [])
  .component('applicationNav', new ApplicationNavComponent());
