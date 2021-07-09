import { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import React from 'react';

import {
  FormikStageConfig,
  FormValidator,
  IArtifact,
  IExpectedArtifact,
  IStage,
  IStageConfigProps,
} from '@spinnaker/core';

import { BakeCloudFoundryManifestConfigForm } from './BakeCloudFoundryManifestConfigForm';

export function BakeCloudFoundryManifestConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      inputArtifacts: [],
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
      validate={validateBakeCloudFoundryManifestStage}
      render={(props) => <BakeCloudFoundryManifestConfigForm {...props} />}
    />
  );
}

export function validateBakeCloudFoundryManifestStage(stage: IStage): FormikErrors<IStage> {
  const formValidator = new FormValidator(stage);

  formValidator
    .field('expectedArtifacts', 'Produced artifacts')
    .required()
    .withValidators((artifacts: IExpectedArtifact[]) => {
      if (validateProducedArtifacts(artifacts)) {
        return undefined;
      }
      return 'Exactly one expected artifact of type embedded/base64 must be configured in the Produces Artifacts section';
    });
  formValidator.field('outputName', 'Name').required();
  formValidator
    .field('inputArtifacts', 'Template and Variables Artifacts')
    .required()
    .withValidators((artifacts: IArtifact[]) => {
      if (validateInputArtifacts(artifacts)) {
        return undefined;
      }
      return 'There should be one manifest template and at least one variables artifact';
    });

  return formValidator.validateForm();
}

export function validateProducedArtifacts(artifacts: IExpectedArtifact[]): boolean {
  return artifacts?.length === 1 && artifacts[0]?.matchArtifact?.type === 'embedded/base64';
}

export function validateInputArtifacts(artifacts: IArtifact[]): boolean {
  return artifacts?.length >= 2;
}
