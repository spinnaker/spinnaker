import {module, IComponentController, IScope, isDefined} from 'angular';
import {cloneDeep, isEqual} from 'lodash';
import {JSON_UTILITY_SERVICE, JsonUtilityService} from 'core/utils/json/json.utility.service';
import {IStage} from 'core/domain/IStage';

export class UnmatchedStageTypeStageCtrl implements IComponentController {
  public stageJson: string;
  public errorMessage: string;
  public textareaRows: number;
  // These values are editable with standard UI controls.
  private keysToHide = new Set<string>(['refId', 'requisiteStageRefIds', 'failPipeline', 'continuePipeline',
                                        'completeOtherBranchesThenFail', 'restrictExecutionDuringTimeWindow',
                                        'restrictedExecutionWindow', 'stageEnabled', 'sendNotifications',
                                        'notifications', 'comments', 'name']);

  static get $inject() { return ['$scope', 'jsonUtilityService']; }

  constructor(public $scope: IScope, private jsonUtilityService: JsonUtilityService) { }

  public $onInit(): void {
    this.stageJson = this.jsonUtilityService.makeSortedStringFromObject(this.makeCleanStageCopy(this.$scope.stage || {}));
    this.textareaRows = this.stageJson.split('\n').length;
  }

  public updateStage(): void {
    let parsedStage: IStage;
    this.errorMessage = null;

    try {
      parsedStage = JSON.parse(this.stageJson);
    } catch (e) {
      this.errorMessage = e.message;
    }
    if (parsedStage && !parsedStage.type) {
      this.errorMessage = 'Cannot delete property <em>type</em>.';
    }

    if (!this.errorMessage) {
      Object.keys(this.$scope.stage).forEach(key => {
        if (!this.keysToHide.has(key)) {
          delete this.$scope.stage[key];
        }
      });
      Object.assign(this.$scope.stage, parsedStage);
      this.setStageJson();
    }
  }

  public setStageJson(): void {
    const stageCopy = this.makeCleanStageCopy(this.$scope.stage);
    // If there are no property differences between the JSON string and the stage object, don't bother updating -
    // we might end up cutting out whitespace unexpectedly.
    if (!isEqual(stageCopy, JSON.parse(this.stageJson || '{}'))) {
      this.stageJson = this.jsonUtilityService.makeStringFromObject(stageCopy);
    }
  }

  private makeCleanStageCopy(stage: IStage): IStage {
    const stageCopy = cloneDeep(stage);
    this.keysToHide.forEach(key => {
      if (isDefined(stageCopy[key])) {
        delete stageCopy[key];
      }
    });
    return stageCopy;
  }
}

export const UNMATCHED_STAGE_TYPE_STAGE_CTRL = 'spinnaker.core.pipeline.stage.unmatchedStageTypeStage.controller';
module(UNMATCHED_STAGE_TYPE_STAGE_CTRL, [JSON_UTILITY_SERVICE])
  .controller('UnmatchedStageTypeStageCtrl', UnmatchedStageTypeStageCtrl);
