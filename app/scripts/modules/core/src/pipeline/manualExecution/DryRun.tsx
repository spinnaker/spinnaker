import React from 'react';

import { HelpField } from 'core/help';
import { CheckboxInput, FormikFormField } from 'core/presentation';

export const DryRun = () => (
  <FormikFormField
    name="dryRun"
    label="Dry run"
    help={<HelpField id="execution.dryRun" />}
    input={(props) => <CheckboxInput {...props} text="Run a test execution" />}
  />
);
