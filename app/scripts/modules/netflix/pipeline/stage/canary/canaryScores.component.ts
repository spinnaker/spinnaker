import { module } from 'angular';
import { isString } from 'lodash';

import './canary.less';

class CanaryScoresConfigComponentCtrl implements ng.IComponentController {

  public unhealthyScore: string;
  public successfulScore: string;
  public onChange: (scoreConfig: any) => void;

  public successful: number;
  public unhealthy: number;
  public invalid = false;
  public hasExpressions = false;

  private isExpression(scoreValue: string): boolean {
    return isString(scoreValue) && scoreValue.includes('${');
  }

  public $onInit() {
    if (this.isExpression(this.unhealthyScore) || this.isExpression(this.successfulScore)) {
      this.hasExpressions = true;
    } else {
      this.successful = parseInt(this.successfulScore, 10);
      this.unhealthy = parseInt(this.unhealthyScore, 10);
    }
  }

  public onUpdate() {
    this.invalid = !(this.successful && this.unhealthy);
    if (this.onChange) {
      this.onChange({
        successfulScore: this.successful ? this.successful.toString() : '',
        unhealthyScore: this.unhealthy ? this.unhealthy.toString() : ''
      });
    }
  }
}

class CanaryScoresConfigComponent implements ng.IComponentOptions {

  public bindings: any = {
    unhealthyScore: '<',
    successfulScore: '<',
    onChange: '&'
  };
  public controller: any = CanaryScoresConfigComponentCtrl;
  public template = `
    <div ng-if="$ctrl.hasExpressions" class="form-group">
      <div class="col-md-2 col-md-offset-1 sm-label-right">
        <label>Canary Scores</label>
      </div>
      <div class="col-md-9 form-control-static">
        Expressions are currently being used for canary scores.
      </div>
    </div>
    <div class="canary-score" ng-if="!$ctrl.hasExpressions">
      <div class="form-group">
        <div class="col-md-2 col-md-offset-1 sm-label-right">
          <label>Unhealthy Score</label>
          <help-field key="pipeline.config.canary.unhealthyScore"></help-field>
        </div>
        <div class="col-md-2">
          <input type="number"
                 required
                 min="0"
                 max="{{$ctrl.successful - 1}}"
                 ng-model="$ctrl.unhealthy"
                 ng-change="$ctrl.onUpdate()"
                 class="form-control input-sm"/>
        </div>
        <div class="col-md-2 col-md-offset-1 sm-label-right">
          <label>Successful Score</label>
          <help-field key="pipeline.config.canary.successfulScore"></help-field>
        </div>
        <div class="col-md-2">
          <input type="number"
                 required
                 min="{{$ctrl.unhealthy + 1}}"
                 max="100"
                 ng-model="$ctrl.successful"
                 ng-change="$ctrl.onUpdate()"
                 class="form-control input-sm"/>
        </div>
      </div>
      <div class="row">
        <div class="col-md-offset-1 col-md-10">
          <div class="progress">
              <div class="progress-bar progress-bar-danger" style="width: {{$ctrl.invalid ? 0 : $ctrl.unhealthy}}%"></div>
              <div class="progress-bar progress-bar-warning" style="width: {{$ctrl.invalid ? 0 : 100 - ($ctrl.unhealthy + (100 - $ctrl.successful))}}%"></div>
              <div class="progress-bar progress-bar-success" style="width: {{$ctrl.invalid ? 0 : 100 - $ctrl.successful}}%"></div>
              <div class="progress-bar progress-bar-warning" style="width: {{$ctrl.invalid ? 100 : 0}}%"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

export const CANARY_SCORES_CONFIG_COMPONENT = 'spinnaker.netflix.canary.scores.component';
module(CANARY_SCORES_CONFIG_COMPONENT, [])
  .component('canaryScores', new CanaryScoresConfigComponent());
