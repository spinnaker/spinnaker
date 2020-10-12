import React from 'react';
import { CheckboxInput, FormikFormField } from 'core/presentation';
import { HelpField } from 'core/help';

export const DryRun = () => (
  <FormikFormField
    name="dryRun"
    label="Dry run"
    help={<HelpField id="execution.dryRun" />}
    input={(props) => <CheckboxInput {...props} text="Run a test execution" />}
  />
);
