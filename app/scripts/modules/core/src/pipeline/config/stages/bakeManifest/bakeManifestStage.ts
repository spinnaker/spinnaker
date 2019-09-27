import { get } from 'lodash';

import { ArtifactReferenceService, ExecutionArtifactTab, ExpectedArtifactService } from 'core/artifact';
import { ExecutionDetailsTasks, IValidatorConfig } from 'core/pipeline';

import { IArtifact, IStage, IPipeline, IStageOrTriggerTypeConfig } from 'core/domain';

import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config';

import { BakeManifestConfig } from './BakeManifestConfig';
import { BakeManifestDetailsTab } from './BakeManifestDetailsTab';
import { ManualExecutionBakeManifest } from './ManualExecutionBakeManifest';
import { ICustomValidator } from '../../validation/PipelineConfigValidator';
import { RequiredFieldValidator, IRequiredFieldValidationConfig } from '../../validation/requiredField.validator';

export const BAKE_MANIFEST_STAGE_KEY = 'bakeManifest';
if (SETTINGS.feature.versionedProviders) {
  const requiredField = (
    _pipeline: IPipeline,
    stage: IStage,
    _validator: IValidatorConfig,
    _config: IStageOrTriggerTypeConfig,
  ): string => {
    if (stage.templateRenderer !== 'HELM2') {
      return '';
    }

    return new RequiredFieldValidator().validate(
      _pipeline,
      stage,
      { fieldLabel: 'Name', fieldName: 'outputName' } as IRequiredFieldValidationConfig,
      _config,
    );
  };

  Registry.pipeline.registerStage({
    label: 'Bake (Manifest)',
    description: 'Bake a manifest (or multi-doc manifest set) using a template renderer such as Helm.',
    key: BAKE_MANIFEST_STAGE_KEY,
    component: BakeManifestConfig,
    producesArtifacts: true,
    cloudProvider: 'kubernetes',
    executionDetailsSections: [BakeManifestDetailsTab, ExecutionDetailsTasks, ExecutionArtifactTab],
    artifactExtractor: (fields: string[]) =>
      ExpectedArtifactService.accumulateArtifacts<IArtifact>(['inputArtifacts'])(fields).map((a: IArtifact) => a.id),
    artifactRemover: (stage: IStage, artifactId: string) => {
      ArtifactReferenceService.removeArtifactFromFields(['expectedArtifactId'])(stage, artifactId);

      const artifactMatches = (artifact: IArtifact) => artifact.id === artifactId;
      stage.expectedArtifacts = get(stage, 'expectedArtifacts', []).filter(a => !artifactMatches(a));
      stage.inputArtifacts = get(stage, 'inputArtifacts', []).filter(a => !artifactMatches(a));
    },
    validators: [{ type: 'custom', validate: requiredField } as ICustomValidator],
    manualExecutionComponent: ManualExecutionBakeManifest,
  });
}
