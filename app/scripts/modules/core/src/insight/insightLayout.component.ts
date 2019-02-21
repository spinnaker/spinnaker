import { module } from 'angular';

import { Application } from 'core/application';
import { InsightFilterStateModel, INSIGHT_FILTER_STATE_MODEL } from './insightFilterState.model';

class InsightLayoutCtrl {
  public app: Application;
  public ready = false;

  public static $inject = ['insightFilterStateModel'];
  constructor(public insightFilterStateModel: InsightFilterStateModel) {
    'ngInject';
  }

  public $onInit() {
    this.app.ready().then(() => (this.ready = true));
  }
}

export class InsightLayoutComponent {
  public bindings: any = {
    app: '<',
  };
  public controller: any = InsightLayoutCtrl;
  public templateUrl: string = require('./insightLayout.component.html');
}

export const INSIGHT_LAYOUT_COMPONENT = 'spinnaker.core.insight.insightLayout.component';
module(INSIGHT_LAYOUT_COMPONENT, [INSIGHT_FILTER_STATE_MODEL]).component('insightLayout', new InsightLayoutComponent());
