import { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import React from 'react';

import { FormikStageConfig, FormValidator, IStage, IStageConfigProps } from '@spinnaker/core';

import { CloudFoundryDestroyServiceStageConfigForm } from './CloudFoundryDestroyServiceStageConfigForm';

export function CloudFoundryDestroyServiceStageConfig({
  application,
  pipeline,
  stage,
  updateStage,
}: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      credentials: '',
      serviceInstanceName: '',
      removeBindings: false,
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
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
