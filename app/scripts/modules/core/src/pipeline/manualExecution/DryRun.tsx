import * as React from 'react';
import { CheckboxInput, FormikFormField } from 'core/presentation';
import { HelpField } from 'core/help';

export class DryRun extends React.Component {
  public render() {
    return (
      <FormikFormField
        name="dryRun"
        label={'Dry run'}
        fastField={false}
        help={<HelpField id={'execution.dryRun'} />}
        input={props => <CheckboxInput {...props} text={'Run a test execution'} />}
      />
    );
  }
}
