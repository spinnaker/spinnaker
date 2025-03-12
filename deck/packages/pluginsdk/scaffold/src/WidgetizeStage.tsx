import React from 'react';

import type { IFormikStageConfigInjectedProps, IStage, IStageConfigProps, IStageTypeConfig } from '@spinnaker/core';
import {
  ExecutionDetailsTasks,
  FormikFormField,
  FormikStageConfig,
  FormValidator,
  HelpField,
  NumberInput,
  TextInput,
  Validators,
} from '@spinnaker/core';

import './WidgetizeStage.less';

/** An example stage config using the FormikStageConfig component */
export function WidgetizeStageConfig(props: IStageConfigProps) {
  return (
    <div className="WidgetizeStageConfig">
      <FormikStageConfig
        {...props}
        validate={validate}
        onChange={props.updateStage}
        render={(props) => <WidgetizeStageForm {...props} />}
      />
    </div>
  );
}

function WidgetizeStageForm(props: IFormikStageConfigInjectedProps) {
  return (
    <>
      <FormikFormField
        name="instanceCount"
        label="Instance Count"
        help={<HelpField content="The number of instances to widgetize" />}
        input={(props) => <NumberInput {...props} />}
      />

      <FormikFormField
        name="user.email"
        label="User Email"
        help={<HelpField content="The email address of the user performing the widgetize operation" />}
        input={(props) => <TextInput {...props} />}
      />
    </>
  );
}

/** Example validation */
export function validate(stageConfig: IStage) {
  const validator = new FormValidator(stageConfig);

  validator
    .field('count')
    .required()
    .withValidators((value, label) => (value < 10 ? `${label} must be > 10` : undefined));

  validator.field('user.email', 'Email').required().withValidators(Validators.emailValue('Invalid email'));

  return validator.validateForm();
}

export const widgetizeStage: IStageTypeConfig = {
  key: 'widgetize',
  label: 'Widgetize',
  description: 'Widgetize the froobulator.',
  component: WidgetizeStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  validateFn: validate,
};
