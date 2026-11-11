import type { FormikErrors } from 'formik';
import React from 'react';

import type { IStage, IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig, FormValidator } from '@spinnaker/core';

import { DeployAppengineConfigForm } from './DeployAppengineConfigForm';

export function DeployAppengineConfigurationConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stage}
      validate={validateDeployAppengineConfigurationStage}
      render={(props) => <DeployAppengineConfigForm {...props} />}
    />
  );
}

export function validateDeployAppengineConfigurationStage(stage: IStage): FormikErrors<IStage> {
  const formValidator = new FormValidator(stage);
  formValidator.field('account').required();
  formValidator.field('region').required();
  return formValidator.validateForm();
}
