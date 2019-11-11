import * as React from 'react';
import { FormikFormField, IWizardPageComponent, TextInput, NumberInput, HelpField } from '@spinnaker/core';
import { FormikProps } from 'formik';
import { IAmazonFunctionUpsertCommand } from 'amazon/index';
import { IAmazonFunction } from 'amazon/domain';

export interface IFunctionSettingsProps {
  formik: FormikProps<IAmazonFunctionUpsertCommand>;
  isNew?: boolean;
  functionDef: IAmazonFunction;
}

export class FunctionSettings extends React.Component<IFunctionSettingsProps>
  implements IWizardPageComponent<IAmazonFunctionUpsertCommand> {
  public validate = () => {
    const errors = {} as any;
    return errors;
  };

  public render() {
    return (
      <div className="container-fluid form-horizontal ">
        <div className="sp-margin-m-bottom">
          <FormikFormField name="description" label="Description" input={props => <TextInput {...props} />} />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="memorySize"
            label="Memory (MB)"
            help={<HelpField id="aws.functionBasicSettings.memorySize" />}
            input={props => <NumberInput {...props} min={128} max={3008} />}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="timeout"
            label="Timeout (seconds)"
            help={<HelpField id="aws.functionBasicSettings.timeout" />}
            input={props => <NumberInput {...props} min={1} max={900} />}
          />
        </div>
      </div>
    );
  }
}
