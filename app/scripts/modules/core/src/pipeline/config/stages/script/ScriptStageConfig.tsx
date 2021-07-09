import React from 'react';

import { FormikStageConfig, IContextualValidator } from '../FormikStageConfig';
import { IStageConfigProps } from '../common/IStageConfigProps';
import { HelpField } from '../../../../help';
import { CheckboxInput, FormikFormField, FormValidator, TextAreaInput, TextInput } from '../../../../presentation';

export const ScriptStageConfig: React.SFC<IStageConfigProps> = (stageConfigProps) => (
  <FormikStageConfig
    {...stageConfigProps}
    onChange={stageConfigProps.updateStage}
    validate={validate}
    render={({ pipeline }) => (
      <div className="form-horizontal">
        <FormikFormField
          name="repoUrl"
          label="Repository Url"
          help={<HelpField id="pipeline.config.script.repoUrl" />}
          input={(props) => <TextInput {...props} />}
        />
        <FormikFormField
          name="repoBranch"
          label="Repository Branch"
          help={<HelpField id="pipeline.config.script.repoBranch" />}
          input={(props) => <TextInput {...props} />}
        />
        <FormikFormField
          name="scriptPath"
          label="Script Path"
          help={<HelpField id="pipeline.config.script.path" />}
          input={(props) => <TextInput {...props} />}
        />
        <FormikFormField
          name="command"
          label="Command"
          required={true}
          help={<HelpField id="pipeline.config.script.command" />}
          input={(props) => <TextAreaInput {...props} />}
        />
        {!pipeline.strategy && (
          <>
            <FormikFormField
              name="image"
              label="Image"
              help={<HelpField id="pipeline.config.script.image" />}
              input={(props) => <TextInput {...props} />}
            />
            <FormikFormField
              name="account"
              label="Account"
              help={<HelpField id="pipeline.config.script.account" />}
              input={(props) => <TextInput {...props} />}
            />
            <FormikFormField
              name="region"
              label="Region"
              help={<HelpField id="pipeline.config.script.region" />}
              input={(props) => <TextInput {...props} />}
            />
            <FormikFormField
              name="cluster"
              label="Cluster"
              help={<HelpField id="pipeline.config.script.cluster" />}
              input={(props) => <TextInput {...props} />}
            />
          </>
        )}
        <FormikFormField
          name="cmc"
          label="Cmc"
          help={<HelpField id="pipeline.config.script.cmc" />}
          input={(props) => <TextInput {...props} />}
        />
        <FormikFormField
          name="waitForCompletion"
          label="Wait for results"
          help={<HelpField id="script.waitForCompletion" />}
          input={(props) => <CheckboxInput {...props} />}
        />
        <FormikFormField
          name="propertyFile"
          label="Property File"
          help={<HelpField id="pipeline.config.script.propertyFile" />}
          input={(props) => <TextInput {...props} />}
        />
      </div>
    )}
  />
);

export const validate: IContextualValidator = (stage) => {
  const formValidator = new FormValidator(stage);
  formValidator.field('command', 'Command').required();
  formValidator.field('scriptPath', 'Script Path').required();
  return formValidator.validateForm();
};
