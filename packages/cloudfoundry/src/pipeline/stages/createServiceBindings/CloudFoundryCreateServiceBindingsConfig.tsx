import { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import React from 'react';

import { FormikStageConfig, FormValidator, IStage, IStageConfigProps } from '@spinnaker/core';

import { CloudFoundryCreateServiceBindingsStageConfigForm } from './CloudFoundryCreateServiceBindingsStageConfigForm';

export interface ServiceBindingRequests {
  serviceInstanceName: String;
}

export function CloudFoundryCreateServiceBindingsConfig({
  application,
  pipeline,
  stage,
  updateStage,
}: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      serviceBindingRequests: [],
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
      validate={validateCloudFoundryCreateServiceBindingsStage}
      render={(props) => <CloudFoundryCreateServiceBindingsStageConfigForm {...props} />}
    />
  );
}

export function validateCloudFoundryCreateServiceBindingsStage(stage: IStage): FormikErrors<IStage> {
  const formValidator = new FormValidator(stage);

  formValidator.field('credentials', 'Account').required();
  formValidator.field('region', 'Region').required();
  formValidator.field('cluster', 'Cluster').required();
  formValidator.field('target', 'Target').required();

  formValidator
    .field('serviceBindingRequests', 'Service Binding Requests')
    .required()
    .withValidators((serviceBindingRequests: ServiceBindingRequests[]) => {
      if (validateServiceBindingRequests(serviceBindingRequests)) {
        return undefined;
      }
      return 'There should be at least one service binding request. At a minimum, each request must have a service instance name.';
    });

  return formValidator.validateForm();
}

export function validateServiceBindingRequests(serviceBindingRequests: ServiceBindingRequests[]): boolean {
  if (serviceBindingRequests?.length < 1) {
    return false;
  }
  return serviceBindingRequests.every((req) => req?.serviceInstanceName);
}
