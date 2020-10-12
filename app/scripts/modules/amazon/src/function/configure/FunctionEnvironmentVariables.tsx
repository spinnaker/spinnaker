import React from 'react';

import {
  IWizardPageComponent,
  HelpField,
  TextInput,
  FormikFormField,
  FormValidator,
  MapEditorInput,
} from '@spinnaker/core';
import { FormikProps } from 'formik';
import { IAmazonFunctionUpsertCommand } from 'amazon/index';
import { IAmazonFunction } from 'amazon/domain';
import { awsArnValidator } from 'amazon/aws.validators';

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
