import type { FormikProps } from 'formik';
import React from 'react';

import type { IWizardPageComponent } from '@spinnaker/core';
import { FormikFormField, FormValidator, MapEditorInput } from '@spinnaker/core';
import { awsTagsValidator } from '../../aws.validators';
import type { IAmazonFunction } from '../../domain';
import type { IAmazonFunctionUpsertCommand } from '../../index';

export interface IFunctionTagsProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  functionDef: IAmazonFunction;
}

export class FunctionTags
  extends React.Component<IFunctionTagsProps>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  public validate = (values: IAmazonFunctionUpsertCommand) => {
    const validator = new FormValidator(values);
    validator.field('tags', 'Tag').required().withValidators(awsTagsValidator);
    return validator.validateForm();
  };

  public render() {
    return (
      <div className="container-fluid form-horizontal ">
        <FormikFormField
          name="tags"
          input={(props) => <MapEditorInput {...props} allowEmptyValues={false} addButtonLabel="Add" />}
        />
      </div>
    );
  }
}
