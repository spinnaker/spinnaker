import {IComponentController, IComponentOptions, module} from 'angular';

export interface IBaseOsOption {
  id: string;
  shortDescription?: string;
  detailedDescription: string;
  isImageFamily?: boolean;
}

export class BakeStageChooseOSController implements IComponentController {

  public model: any;
  public baseOsOptions: IBaseOsOption[];

  private showRadioButtons = false;

  public $onInit(): void {
    this.showRadioButtons = this.baseOsOptions.length <= 2;
  }

  public getBaseOsDescription(baseOsOption: IBaseOsOption): String {
    return baseOsOption.id + (baseOsOption.shortDescription ? ' (' + baseOsOption.shortDescription + ')' : '');
  }

  public getBaseOsDetailedDescription(baseOsOption: IBaseOsOption): String {
    return baseOsOption.detailedDescription + (baseOsOption.isImageFamily ? ' (family)' : '');
  }
}

class BakeStageChooseOSComponent implements IComponentOptions {
  public bindings: any = {
    baseOsOptions: '<',
    model: '='
  };
  public controller: any = BakeStageChooseOSController;
  public templateUrl: string = require('./bakeStageChooseOs.component.html')
}

export const PIPELINE_BAKE_STAGE_CHOOSE_OS = 'spinnaker.core.pipeline.bake.chooseOS.component';
module(PIPELINE_BAKE_STAGE_CHOOSE_OS, [])
  .component('bakeStageChooseOs', new BakeStageChooseOSComponent());
