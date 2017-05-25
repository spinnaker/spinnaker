import { IComponentController, IComponentOptions, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { Application } from 'core/application';
import { RECENT_HISTORY_SERVICE, RecentHistoryService } from 'core/history/recentHistory.service';

import './newapplication.less';
import './application.less';

export class ApplicationController implements IComponentController {

  public app: Application;
  public refreshTooltipTemplate = require('./applicationRefresh.tooltip.html');

  constructor(private recentHistoryService: RecentHistoryService, private $uibModal: IModalService) {
    'ngInject';
  }

  public pageApplicationOwner(): void {
    this.$uibModal.open({
      templateUrl: require('./modal/pageApplicationOwner.html'),
      controller: 'PageApplicationOwner as ctrl',
      resolve: {
        application: () => this.app
      }
    })
  }

  public $onInit() {
    if (this.app.notFound) {
      this.recentHistoryService.removeLastItem('applications');
      return;
    }
    this.app.enableAutoRefresh();
  }

  public $onDestroy() {
    if (!this.app.notFound) {
      this.app.disableAutoRefresh();
    }
  }
}

const applicationComponent: IComponentOptions = {
  bindings: {
    app: '<'
  },
  controller: ApplicationController,
  template: `
    <div class="page-header">
      <div ng-if="$ctrl.app.notFound">
        <h2 class="text-center">Application Not Found</h2>
        <p class="text-center" style="margin-bottom: 20px">Please check your URL - we can't find any data for <em>{{$ctrl.app.name}}</em>.</p>
      </div>
      <div class="container application-header">
        <h2 ng-if="!$ctrl.application.notFound">
          <i class="fa fa-window-maximize"></i>
          <span class="application-name">{{$ctrl.app.name}}</span>
          <component-refresher state="$ctrl.app.activeState || $ctrl.app"
                               template-url="$ctrl.refreshTooltipTemplate"
                               refresh="$ctrl.app.refresh(true)"></component-refresher>
        </h2>
        <div class="application-navigation">
          <application-nav application="$ctrl.app"></application-nav>
          <div class="header-right">
            <secondary-application-nav application="$ctrl.app"></secondary-application-nav>
            <div class="page-button" ng-if="$ctrl.app.attributes.pdApiKey">
              <button class="btn btn-xs btn-danger btn-page-owner"
                      ng-click="$ctrl.pageApplicationOwner()"
                      uib-tooltip="Page application owner">
                <i class="fa fa-phone"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="container scrollable-columns">
      <div class="secondary-panel" ui-view="insight"></div>
    </div>
  `,
};

export const APPLICATION_COMPONENT = 'spinnaker.core.application.component';
module(APPLICATION_COMPONENT, [
  require('angular-ui-router').default,
  RECENT_HISTORY_SERVICE,
]).component('application', applicationComponent);
