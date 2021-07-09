import { FormikErrors, FormikProps } from 'formik';
import React from 'react';

import {
  FormikFormField,
  FormValidator,
  HelpField,
  IWizardPageComponent,
  ReactSelectInput,
  TextInput,
} from '@spinnaker/core';
import { awsArnValidator } from '../../aws.validators';
import { IAmazonFunction } from '../../domain';
import { IAmazonFunctionUpsertCommand } from '../../index';

export interface IFunctionDebugAndErrorHandlingProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  functionDef: IAmazonFunction;
}

export class FunctionDebugAndErrorHandling
  extends React.Component<IFunctionDebugAndErrorHandlingProps>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  constructor(props: IFunctionDebugAndErrorHandlingProps) {
    super(props);
  }

  public validate = (values: IAmazonFunctionUpsertCommand): FormikErrors<IAmazonFunctionUpsertCommand> => {
    const validator = new FormValidator(values);
    validator.field('deadLetterConfig.targetArn', 'Target ARN').optional().withValidators(awsArnValidator);
    return validator.validateForm();
  };

  public render() {
    return (
      <div className="container-fluid form-horizontal ">
        Dead Letter Config
        <FormikFormField
          name="deadLetterConfig.targetArn"
          label="Target ARN"
          help={<HelpField id="aws.function.deadletterqueue" />}
          input={(props) => <TextInput {...props} />}
        />
        X-Ray Tracing
        <FormikFormField
          name="tracingConfig.mode"
          label="Mode"
          help={<HelpField id="aws.function.tracingConfig.mode" />}
          input={(props) => <ReactSelectInput {...props} stringOptions={['Active', 'PassThrough']} clearable={true} />}
        />
      </div>
    );
  }
}
