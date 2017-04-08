import {InsightFilterStateModel} from './insightFilterState.model';
import {INSIGHT_NGMODULE} from './insight.module';

class InsightLayoutCtrl {
  static get $inject() { return ['InsightFilterStateModel']; }
  constructor(public InsightFilterStateModel: InsightFilterStateModel) {
  }
}

INSIGHT_NGMODULE.component('insightLayout', {
  templateUrl: require('./insightLayout.component.html'),
  controller: InsightLayoutCtrl,
  bindings: {
    app: '<',
  }
});
