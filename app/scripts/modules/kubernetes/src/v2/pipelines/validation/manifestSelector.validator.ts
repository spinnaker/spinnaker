import {
  IPipeline,
  IStage,
  IStageOrTriggerTypeConfig,
  IStageOrTriggerValidator,
  IValidatorConfig,
  PipelineConfigValidator,
} from '@spinnaker/core';

export interface IStageWithManifestSelector extends IStage {
  manifestName: string;
  location: string;
}

export class ManifestSelectorValidator implements IStageOrTriggerValidator {
  public validate(
    _pipeline: IPipeline,
    stage: IStageWithManifestSelector,
    _validator: IValidatorConfig,
    _config: IStageOrTriggerTypeConfig,
  ): string {
    const [kind, name] = (stage.manifestName || '').split(' ');
    if (!name && !kind) {
      return `<strong>Name</strong> and <strong>Kind</strong> are required fields.`;
    }
    if (!name) {
      return `<strong>Name</strong> is a required field.`;
    }
    if (!kind) {
      return `<strong>Kind</strong> is a required field.`;
    }
    return null;
  }
}

PipelineConfigValidator.registerValidator('manifestSelector', new ManifestSelectorValidator());
