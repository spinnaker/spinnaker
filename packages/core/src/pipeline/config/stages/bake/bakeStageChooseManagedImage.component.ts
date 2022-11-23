import type { IComponentOptions, IController } from 'angular';
import { module } from 'angular';
import { SETTINGS } from '../../../../config/settings';

export interface IManagedImageOption {
  id: string;
  name: string;
  osType: string;
}

export class BakeStageChooseManagedImageController implements IController {
  public model: any;
  public managedImageOptions: IManagedImageOption[];
  public onChange: () => any;

  public $onChanges(): void {}

  public getManagedImageDescription(managedImageOption: IManagedImageOption): string {
    return managedImageOption?.name || '';
  }

  public getManagedImageDetailedDescription(managedImageOption: IManagedImageOption): string {
    if (managedImageOption?.osType) {
      return `${managedImageOption.name} (${managedImageOption.osType})`;
    }
    return `${managedImageOption.name}`;
  }

  public getManagedImageDisabled(managedImageOption: IManagedImageOption): boolean {
    const disabledImages = SETTINGS.disabledImages || [];
    return disabledImages.includes(managedImageOption.id);
  }
}

const bakeStageChooseManagedImageComponent: IComponentOptions = {
  bindings: {
    managedImageOptions: '<',
    model: '=',
    onChange: '=',
  },
  controller: BakeStageChooseManagedImageController,
  templateUrl: require('./bakeStageChooseManagedImage.component.html'),
};

export const PIPELINE_BAKE_STAGE_CHOOSE_MANAGED_IMAGE = 'spinnaker.core.pipeline.bake.chooseManagedImage.component';
module(PIPELINE_BAKE_STAGE_CHOOSE_MANAGED_IMAGE, []).component(
  'bakeStageChooseManagedImage',
  bakeStageChooseManagedImageComponent,
);
