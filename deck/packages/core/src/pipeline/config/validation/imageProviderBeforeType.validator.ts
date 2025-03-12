import type { IStageOrTriggerValidator, IValidatorConfig } from './PipelineConfigValidator';
import { PipelineConfigValidator } from './PipelineConfigValidator';
import type { ICluster, IPipeline, IStage, IStageOrTriggerTypeConfig } from '../../../domain';
import { PipelineConfigService } from '../services/PipelineConfigService';

export interface IImageProviderBeforeTypeValidationConfig extends IValidatorConfig {
  triggerTypes?: string[];
  message: string;
}

/**
 * Checks for triggers that provide images or a custom image id. If none are present, warn the user who is configuring the pipeline.
 */
export class ImageProviderBeforeTypeValidator implements IStageOrTriggerValidator {
  public validate(
    pipeline: IPipeline,
    stage: IStage,
    validator: IImageProviderBeforeTypeValidationConfig,
    _config: IStageOrTriggerTypeConfig,
  ): string {
    const triggersToTest = pipeline.triggers || [];
    const hasImageProvider =
      Boolean(triggersToTest.length) &&
      triggersToTest.every((trigger) => (validator.triggerTypes || []).includes(trigger.type));

    const hasCustomImage = (stage.clusters || []).every(
      (cluster: ICluster) => cluster.imageId && cluster.imageId !== '${trigger.properties.imageName}',
    );

    const stageTypes = ['findAmi', 'findImage', 'findImageFromTags'];
    const stagesToTest = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage);
    const hasFindImageStage = stagesToTest.some((test) => stageTypes.includes(test.type));

    if (!hasImageProvider && !hasCustomImage && !hasFindImageStage) {
      return `${
        validator.message
      } Update the pipeline with a find image stage or pipeline trigger or update this stage by manually inputting the imageId. Suggested triggers: ${(
        validator.triggerTypes || []
      ).join(', ')}`;
    }

    return null;
  }
}

PipelineConfigValidator.registerValidator('imageProviderBeforeType', new ImageProviderBeforeTypeValidator());
