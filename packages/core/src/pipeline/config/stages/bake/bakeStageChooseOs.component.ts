import { IComponentOptions, IController, module } from 'angular';
import { SETTINGS } from '../../../../config/settings';

export interface IBaseOsOption {
  id: string;
  shortDescription?: string;
  detailedDescription: string;
  isImageFamily?: boolean;
  displayName?: string;
}

export class BakeStageChooseOSController implements IController {
  public model: any;
  public baseOsOptions: IBaseOsOption[];
  public onChange: () => any;

  public showRadioButtons = false;

  public $onChanges(): void {
    this.showRadioButtons = this.baseOsOptions && this.baseOsOptions.length <= 2;
  }

  public getBaseOsDescription(baseOsOption: IBaseOsOption): string {
    const baseOsName = baseOsOption?.displayName || baseOsOption?.id || '';
    if (baseOsOption?.shortDescription) {
      return `${baseOsName} (${baseOsOption.shortDescription})`;
    }
    return baseOsName;
  }
  public getBaseOsDetailedDescription(baseOsOption: IBaseOsOption): string {
    return baseOsOption.detailedDescription + (baseOsOption.isImageFamily ? ' (family)' : '');
  }

  public getBaseOsDisabled(baseOsOption: IBaseOsOption): boolean {
    const disabledImages = SETTINGS.disabledImages || [];
    return disabledImages.includes(baseOsOption.id);
  }
}

const bakeStageChooseOsComponent: IComponentOptions = {
  bindings: {
    baseOsOptions: '<',
    model: '=',
    onChange: '=',
  },
  controller: BakeStageChooseOSController,
  templateUrl: require('./bakeStageChooseOs.component.html'),
};

export const PIPELINE_BAKE_STAGE_CHOOSE_OS = 'spinnaker.core.pipeline.bake.chooseOS.component';
module(PIPELINE_BAKE_STAGE_CHOOSE_OS, []).component('bakeStageChooseOs', bakeStageChooseOsComponent);
