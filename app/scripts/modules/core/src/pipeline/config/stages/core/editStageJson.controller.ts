import { module, IController } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { cloneDeepWith } from 'lodash';

import { IStage } from 'core/domain';
import { JsonUtilityService } from 'core/utils/json/json.utility.service';

export class EditStageJsonController implements IController {
  public stageJSON: string;
  public isInvalid = false;
  public errorMessage: string = null;

  private immutableFields = ['$$hashKey', 'refId', 'requisiteStageRefIds'];

  constructor(
    private $uibModalInstance: IModalServiceInstance,
    private jsonUtilityService: JsonUtilityService,
    private stage: IStage,
  ) {
    'ngInject';
    const copy = cloneDeepWith<IStage>(stage, (value: any) => {
      if (value && value.$$hashKey) {
        delete value.$$hashKey;
      }
      return undefined; // required for clone operation and typescript happiness
    });
    this.immutableFields.forEach(k => delete copy[k]);
    this.stageJSON = this.jsonUtilityService.makeSortedStringFromObject(copy);
  }

  public updateStage(): void {
    try {
      const parsed = JSON.parse(this.stageJSON);
      Object.keys(this.stage)
        .filter(k => !this.immutableFields.includes(k))
        .forEach(k => delete this.stage[k]);
      Object.assign(this.stage, parsed);
      this.$uibModalInstance.close();
    } catch (e) {
      this.isInvalid = true;
      this.errorMessage = e.message;
    }
  }
}

export const EDIT_STAGE_JSON_CONTROLLER = 'spinnaker.core.pipeline.config.stages.core.editStageJson.controller';
module(EDIT_STAGE_JSON_CONTROLLER, [require('angular-ui-bootstrap')]).controller(
  'editStageJsonCtrl',
  EditStageJsonController,
);
