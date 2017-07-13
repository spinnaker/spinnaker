import { IController } from 'angular';
import { InsightFilterStateModel } from './insightFilterState.model';
import { INSIGHT_NGMODULE } from './insight.module';

export class InsightFilterCtrl implements IController {
  constructor(public insightFilterStateModel: InsightFilterStateModel) { 'ngInject'; }
}

INSIGHT_NGMODULE.component('insightFilter', {
  templateUrl: require('./insightFilter.component.html'),
  controller: InsightFilterCtrl,
  transclude: true,
  bindings: {
    hidden: '<',
  }
});
