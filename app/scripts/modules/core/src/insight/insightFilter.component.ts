import { IController, module } from 'angular';
import { INSIGHT_LAYOUT_COMPONENT } from './insightLayout.component';
import { InsightFilterStateModel } from './insightFilterState.model';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

export class InsightFilterCtrl implements IController {
  public static $inject = ['insightFilterStateModel'];
  constructor(public insightFilterStateModel: InsightFilterStateModel) {}
}

export const INSIGHT_FILTER_COMPONENT = 'spinnaker.core.insight.filter.component';
module(INSIGHT_FILTER_COMPONENT, [UIROUTER_ANGULARJS, INSIGHT_LAYOUT_COMPONENT]).component('insightFilter', {
  templateUrl: require('./insightFilter.component.html'),
  controller: InsightFilterCtrl,
  transclude: true,
  bindings: {
    hidden: '<',
  },
});
