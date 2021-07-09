import { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import React from 'react';

import { FormikStageConfig, FormValidator, IStage, IStageConfigProps } from '@spinnaker/core';

import { CloudFoundryDeleteServiceBindingsStageConfigForm } from './CloudFoundryDeleteServiceBindingsStageConfigForm';

interface ServiceUnbindingRequests {
  serviceInstanceName: String;
}

export function CloudFoundryDeleteServiceBindingsConfig({
  application,
  pipeline,
  stage,
  updateStage,
}: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      serviceUnbindingRequests: [],
      restageRequired: true,
      restartRequired: false,
      credentials: '',
      region: '',
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
      validate={validateCloudFoundryDeleteServiceBindingsStage}
      render={(props) => <CloudFoundryDeleteServiceBindingsStageConfigForm {...props} />}
    />
  );
}

export function validateCloudFoundryDeleteServiceBindingsStage(stage: IStage): FormikErrors<IStage> {
  const formValidator = new FormValidator(stage);

  formValidator.field('credentials', 'Account').required();
  formValidator.field('region', 'Region').required();
  formValidator.field('cluster', 'Cluster').required();
  formValidator.field('target', 'Target').required();

  formValidator
    .field('serviceUnbindingRequests', 'Service Binding Requests')
    .required()
    .withValidators((serviceUnbindingRequests: ServiceUnbindingRequests[]) => {
      if (validateServiceUnbindingRequests(serviceUnbindingRequests)) {
        return undefined;
      }
      return 'There should be at least one service binding request. At a minimum, each request must have a service instance name.';
    });

  return formValidator.validateForm();
}

export function validateServiceUnbindingRequests(serviceUnbindingRequests: ServiceUnbindingRequests[]): boolean {
  if (serviceUnbindingRequests?.length < 1) {
    return false;
  }
  return serviceUnbindingRequests.every((req) => req.serviceInstanceName);
}
