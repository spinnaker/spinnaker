import { FormikErrors, FormikProps } from 'formik';
import React from 'react';

import { FormikFormField, FormValidator, IWizardPageComponent, TextInput } from '@spinnaker/core';
import { iamRoleValidator } from '../../aws.validators';
import { IAmazonFunction } from '../../domain';
import { IAmazonFunctionUpsertCommand } from '../../index';

export interface IExecutionRoleProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  functionDef: IAmazonFunction;
}

export class ExecutionRole
  extends React.Component<IExecutionRoleProps>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  constructor(props: IExecutionRoleProps) {
    super(props);
  }

  public validate(values: IAmazonFunctionUpsertCommand): FormikErrors<IAmazonFunctionUpsertCommand> {
    const validator = new FormValidator(values);
    validator.field('role', 'Role ARN').required().withValidators(iamRoleValidator);
    return validator.validateForm();
  }

  public render() {
    return (
      <div className="form-group">
        <div className="col-md-11">
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="role"
              label="Role ARN"
              input={(props) => <TextInput {...props} placeholder="Enter role ARN" name="role" />}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
