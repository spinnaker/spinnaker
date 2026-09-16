import type { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import React from 'react';

import { BakeManifestStageForm, validateProducedArtifacts } from './BakeManifestStageForm';
import { FormikStageConfig } from '../FormikStageConfig';
import { HELM_RENDERERS, HELMFILE_RENDERER } from './ManifestRenderers';
import type { IStageConfigProps } from '../common';
import type { IExpectedArtifact, IStage } from '../../../../domain';
import type { IValidator } from '../../../../presentation';
import { errorMessage, FormValidator } from '../../../../presentation';

// Namespace/Environment are passed as literal arguments to the helmfile CLI (e.g. `--namespace
// <value>`) by rosco's HelmfileTemplateUtils, which executes the command as an argv array (never
// via a shell) - so this is not a shell-injection fix. It guards against argument injection (e.g.
// a value beginning with "-" being misread as a new CLI flag) and acts as defense-in-depth,
// matching the equivalent validation in HelmfileTemplateUtils on the rosco side. Values containing
// a SpEL expression are exempted here since they're resolved server-side before baking.
const safeCliArgumentValidator: IValidator = (val: string, label: string) =>
  !/^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$/.test(val) &&
  errorMessage(`${label} may only contain letters, numbers, '.', '_', and '-'`);

export function BakeManifestConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      inputArtifacts: [],
      overrides: {},
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
      validate={validateBakeManifestStage}
      render={(props) => <BakeManifestStageForm {...props} />}
    />
  );
}

export function validateBakeManifestStage(stage: IStage): FormikErrors<IStage> {
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

  if (HELM_RENDERERS.includes(stage.templateRenderer)) {
    formValidator.field('outputName', 'Name').required();
  }
  if (HELMFILE_RENDERER === stage.templateRenderer) {
    formValidator.field('outputName', 'Name').required();
    formValidator.field('namespace', 'Namespace').optional().spelAware().withValidators(safeCliArgumentValidator);
    formValidator.field('environment', 'Environment').optional().spelAware().withValidators(safeCliArgumentValidator);
  }

  return formValidator.validateForm();
}
