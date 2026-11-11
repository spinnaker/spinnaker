import type { FormikErrors } from 'formik';
import React from 'react';

import type { IStage, IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig, FormValidator } from '@spinnaker/core';

import { CloudFoundryDestroyServiceStageConfigForm } from './CloudFoundryDestroyServiceStageConfigForm';

export function CloudFoundryDestroyServiceStageConfig({
  application,
  pipeline,
  stage,
  updateStage,
}: IStageConfigProps) {
  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stage}
      defaults={{
        credentials: '',
        serviceInstanceName: '',
        removeBindings: false,
      }}
      validate={validateCloudFoundryDestroyServiceStage}
      render={(props) => <CloudFoundryDestroyServiceStageConfigForm {...props} />}
    />
  );
}

export function validateCloudFoundryDestroyServiceStage(stage: IStage): FormikErrors<IStage> {
  const formValidator = new FormValidator(stage);

  formValidator.field('credentials', 'Account').required();
  formValidator.field('region', 'Region').required();
  formValidator.field('serviceInstanceName', 'Service Instance Name').required();
  formValidator.field('application', 'Application').required();

  return formValidator.validateForm();
}
