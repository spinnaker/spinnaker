import { IController, module } from 'angular';
import { INSIGHT_LAYOUT_COMPONENT } from 'core/insight/insightLayout.component';
import { InsightFilterStateModel } from './insightFilterState.model';

export class InsightFilterCtrl implements IController {
  constructor(public insightFilterStateModel: InsightFilterStateModel) {
    'ngInject';
  }
}

export const INSIGHT_FILTER_COMPONENT = 'spinnaker.core.insight.filter.component';
module(INSIGHT_FILTER_COMPONENT, [require('@uirouter/angularjs').default, INSIGHT_LAYOUT_COMPONENT]).component(
  'insightFilter',
  {
    templateUrl: require('./insightFilter.component.html'),
    controller: InsightFilterCtrl,
    transclude: true,
    bindings: {
      hidden: '<',
    },
  },
);
