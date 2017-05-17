import {InsightFilterStateModel} from './insightFilterState.model';
import {INSIGHT_NGMODULE} from './insight.module';

class InsightLayoutCtrl {
  constructor(public InsightFilterStateModel: InsightFilterStateModel) { 'ngInject'; }
}

INSIGHT_NGMODULE.component('insightLayout', {
  templateUrl: require('./insightLayout.component.html'),
  controller: InsightLayoutCtrl,
  bindings: {
    app: '<',
  }
});
