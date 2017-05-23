import {InsightFilterStateModel} from './insightFilterState.model';
import {INSIGHT_NGMODULE} from './insight.module';
import { Application } from 'core/application';

class InsightLayoutCtrl {
  public app: Application;
  public ready = false;

  constructor(public InsightFilterStateModel: InsightFilterStateModel) { 'ngInject'; }

  public $onInit() {
    this.app.ready().then(() => this.ready = true);
  }
}

INSIGHT_NGMODULE.component('insightLayout', {
  templateUrl: require('./insightLayout.component.html'),
  controller: InsightLayoutCtrl,
  bindings: {
    app: '<',
  }
});
