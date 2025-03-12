import type { FormikProps } from 'formik';
import React from 'react';

import type { IWizardPageComponent } from '@spinnaker/core';
import { FormikFormField, HelpField, NumberInput, TextInput } from '@spinnaker/core';
import type { IAmazonFunction } from '../../domain';
import type { IAmazonFunctionUpsertCommand } from '../../index';

export interface IFunctionSettingsProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  functionDef: IAmazonFunction;
}

export class FunctionSettings
  extends React.Component<IFunctionSettingsProps>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  public validate = () => {
    const errors = {} as any;
    return errors;
  };

  public render() {
    return (
      <div className="container-fluid form-horizontal ">
        <FormikFormField name="description" label="Description" input={(props) => <TextInput {...props} />} />
        <FormikFormField
          name="memorySize"
          label="Memory (MB)"
          help={<HelpField id="aws.functionBasicSettings.memorySize" />}
          input={(props) => <NumberInput {...props} min={128} max={3008} />}
        />
        <FormikFormField
          name="timeout"
          label="Timeout (seconds)"
          help={<HelpField id="aws.functionBasicSettings.timeout" />}
          input={(props) => <NumberInput {...props} min={1} max={900} />}
        />
        <FormikFormField name="targetGroups" label="Target Group Name" input={(props) => <TextInput {...props} />} />
      </div>
    );
  }
}
