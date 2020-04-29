import { module } from 'angular';

import { Application } from 'core/application';
import { InsightFilterStateModel, INSIGHT_FILTER_STATE_MODEL } from './insightFilterState.model';
import { FILTER_COLLAPSE_COMPONENT } from 'core/filterModel/filterCollapse.component';

class InsightLayoutCtrl {
  public app: Application;
  public ready = false;

  public static $inject = ['insightFilterStateModel'];
  constructor(public insightFilterStateModel: InsightFilterStateModel) {}

  public $onInit() {
    this.app.ready().then(() => (this.ready = true));
  }
}

export const insightLayoutComponent = {
  bindings: {
    app: '<',
  },
  controller: InsightLayoutCtrl,
  templateUrl: require('./insightLayout.component.html'),
};

export const INSIGHT_LAYOUT_COMPONENT = 'spinnaker.core.insight.insightLayout.component';
module(INSIGHT_LAYOUT_COMPONENT, [INSIGHT_FILTER_STATE_MODEL, FILTER_COLLAPSE_COMPONENT]).component(
  'insightLayout',
  insightLayoutComponent,
);
