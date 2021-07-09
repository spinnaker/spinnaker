import { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import React from 'react';

import { FormikStageConfig, FormValidator, IStage, IStageConfigProps } from '@spinnaker/core';

import { DeployAppengineConfigForm } from './DeployAppengineConfigForm';

export function DeployAppengineConfigurationConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
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
