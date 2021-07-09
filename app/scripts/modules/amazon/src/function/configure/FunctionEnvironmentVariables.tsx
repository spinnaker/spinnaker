import { FormikProps } from 'formik';
import React from 'react';

import {
  FormikFormField,
  FormValidator,
  HelpField,
  IWizardPageComponent,
  MapEditorInput,
  TextInput,
} from '@spinnaker/core';
import { awsArnValidator } from '../../aws.validators';
import { IAmazonFunction } from '../../domain';
import { IAmazonFunctionUpsertCommand } from '../../index';

export interface IFunctionEnvironmentVariablesProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  functionDef: IAmazonFunction;
}

export class FunctionEnvironmentVariables
  extends React.Component<IFunctionEnvironmentVariablesProps>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  public validate = (values: IAmazonFunctionUpsertCommand) => {
    const validator = new FormValidator(values);
    validator.field('kmskeyArn', 'KMS Key ARN').optional().withValidators(awsArnValidator);
    return validator.validateForm();
  };

  public render() {
    return (
      <div className="container-fluid form-horizontal ">
        <FormikFormField
          name="envVariables"
          label="Env Variables"
          input={(props) => <MapEditorInput {...props} allowEmptyValues={true} addButtonLabel="Add" />}
        />
        <FormikFormField
          name="kmskeyArn"
          label="Key ARN"
          help={<HelpField id="aws.function.kmsKeyArn" />}
          input={(props) => <TextInput {...props} />}
        />
      </div>
    );
  }
}
