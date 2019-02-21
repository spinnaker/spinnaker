import { IController, IComponentOptions, module } from 'angular';

export interface IBaseOsOption {
  id: string;
  shortDescription?: string;
  detailedDescription: string;
  isImageFamily?: boolean;
}

export class BakeStageChooseOSController implements IController {
  public model: any;
  public baseOsOptions: IBaseOsOption[];

  public showRadioButtons = false;

  public $onChanges(): void {
    this.showRadioButtons = this.baseOsOptions && this.baseOsOptions.length <= 2;
  }

  public getBaseOsDescription(baseOsOption: IBaseOsOption): string {
    return baseOsOption.id + (baseOsOption.shortDescription ? ' (' + baseOsOption.shortDescription + ')' : '');
  }

  public getBaseOsDetailedDescription(baseOsOption: IBaseOsOption): string {
    return baseOsOption.detailedDescription + (baseOsOption.isImageFamily ? ' (family)' : '');
  }
}

const bakeStageChooseOsComponent: IComponentOptions = {
  bindings: {
    baseOsOptions: '<',
    model: '=',
  },
  controller: BakeStageChooseOSController,
  templateUrl: require('./bakeStageChooseOs.component.html')
};

export const PIPELINE_BAKE_STAGE_CHOOSE_OS = 'spinnaker.core.pipeline.bake.chooseOS.component';
module(PIPELINE_BAKE_STAGE_CHOOSE_OS, []).component('bakeStageChooseOs', bakeStageChooseOsComponent);
