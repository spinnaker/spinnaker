import {InsightFilterStateModel} from './insightFilterState.model';
import {INSIGHT_NGMODULE} from './insight.module';

export class InsightFilterCtrl {
  static get $inject() { return ['InsightFilterStateModel']; }
  constructor(public InsightFilterStateModel: InsightFilterStateModel) {
  }
}

INSIGHT_NGMODULE.component('insightFilter', {
  templateUrl: require('./insightFilter.component.html'),
  controller: InsightFilterCtrl,
  transclude: true,
  bindings: {
    hidden: '<',
  }
});
