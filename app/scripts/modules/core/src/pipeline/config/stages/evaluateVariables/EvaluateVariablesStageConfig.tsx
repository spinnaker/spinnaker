import { FieldArray } from 'formik';
import { countBy } from 'lodash';
import React from 'react';

import { ExecutionAndStagePicker, IExecutionAndStagePickerProps } from './ExecutionAndStagePicker';
import { FormikStageConfig, IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { IStageConfigProps } from '../common';
import { IStage } from '../../../../domain';
import {
  errorMessage,
  FormikFormField,
  FormValidator,
  ILayoutProps,
  IStageForSpelPreview,
  IValidator,
  LayoutContext,
  LayoutProvider,
  Markdown,
  SpelInput,
  StandardFieldLayout,
  TextInput,
  Tooltip,
  useIsMountedRef,
  ValidationMessage,
} from '../../../../presentation';

import './EvaluateVariablesStageConfig.less';

export interface IEvaluatedVariable {
  key: string;
  value: string;
}

const variableNameValidator: IValidator = (val: string, label: string) =>
  !val.match(/^[a-zA-Z_][a-zA-Z0-9_]*$/) &&
  errorMessage(`${label} should consist only of letters, numbers, or underscore`);

const duplicateKeyValidatorFactory = (variables: IEvaluatedVariable[] = []) => {
  const keyCounts = countBy(
    variables.map((x) => x.key),
    (x) => x,
  );
  return (key: string) => keyCounts[key] > 1 && `Duplicate key '${key}'`;
};

export function validateEvaluateVariablesStage(stage: IStage) {
  const formValidator = new FormValidator(stage);
  const duplicateKeyValidator = duplicateKeyValidatorFactory(stage.variables);
  formValidator.field('variables').withValidators(
    formValidator.arrayForEach((item) => {
      item.field('key', 'Variable Name').required().withValidators(variableNameValidator, duplicateKeyValidator);
      item.field('value', 'Expression').required();
    }),
  );
  return formValidator.validateForm();
}

function PreviewConfiguration(props: IExecutionAndStagePickerProps) {
  return (
    <div style={{ marginLeft: '16px', marginRight: '16px' }}>
      <ValidationMessage
        type={'info'}
        message={
          <>
            <p className="bold">Variable Previews</p>

            <ExecutionAndStagePicker {...props} />
          </>
        }
      />
    </div>
  );
}

export function EvaluateVariablesStageConfig(props: IStageConfigProps) {
  const { application, stage, pipeline, updateStage } = props;
  const [chosenStage, setChosenStage] = React.useState({} as IStageForSpelPreview);

  const helpMessage =
    'Define one or more variables by assigning a **name** _(string)_ and a **value** ' +
    '_([SpEL Expression](https://www.spinnaker.io/guides/user/pipeline/expressions/))_. ' +
    'The evaluated variables can be used in downstream stages, referencing them by name.';

  return (
    <FormikStageConfig
      application={application}
      stage={stage}
      pipeline={pipeline}
      validate={validateEvaluateVariablesStage}
      onChange={updateStage}
      render={(renderProps) => {
        return (
          <LayoutProvider value={StandardFieldLayout}>
            <div className="flex-container-v margin-between-lg">
              <Markdown message={helpMessage} />
              <PreviewConfiguration pipeline={pipeline} pipelineStage={stage} onChange={setChosenStage} />
              <EvaluateVariablesStageForm {...renderProps} chosenStage={chosenStage} />
            </div>
          </LayoutProvider>
        );
      }}
    />
  );
}

interface IEvaluateVariablesStageFormProps extends IFormikStageConfigInjectedProps {
  chosenStage: IStageForSpelPreview;
}

function EvaluateVariablesStageForm(props: IEvaluateVariablesStageFormProps) {
  const { formik, chosenStage } = props;
  const stage = props.formik.values;
  const { variables = [] } = stage;
  const isMountedRef = useIsMountedRef();

  React.useEffect(() => {
    if (variables.length === 0) {
      // This setTimeout is necessary because the interaction between pipelineConfigurer.js and stage.module.js
      // causes this component to get mounted multiple times.  The second time it gets mounted, the initial
      // variable is already added to the array, and then gets auto-touched by SpinFormik.tsx.
      // The end effect is that the red validation warnings are shown immediately when the Evaluate Variables stage is added.
      setTimeout(() => isMountedRef.current && formik.setFieldValue('variables', [{ key: null, value: null }]), 100);
    }
  }, []);

  const FieldLayoutComponent = React.useContext(LayoutContext);

  const [deleteCount, setDeleteCount] = React.useState(0);

  return (
    <FieldArray
      key={deleteCount}
      name="variables"
      render={(arrayHelpers) => (
        <div className="EvaluateVariablesStageConfig form-horizontal">
          <FieldLayoutComponent
            label={<h4>Variable Name</h4>}
            input={<h4>Variable Value</h4>}
            validation={{ hidden: true } as any}
          />

          {variables.map((_, index) => {
            const onDeleteClicked = () => {
              setDeleteCount((count) => count + 1);
              arrayHelpers.handleRemove(index)();
            };

            return (
              <FormikVariable
                key={`${deleteCount}-${index}`}
                index={index}
                previewStage={chosenStage}
                onDeleteClicked={onDeleteClicked}
              />
            );
          })}

          <button
            type="button"
            className="btn btn-block btn-sm add-new"
            onClick={arrayHelpers.handlePush({ key: null, value: null })}
          >
            <span className="glyphicon glyphicon-plus-sign" />
            Add Variable
          </button>
        </div>
      )}
    />
  );
}

interface IFormikVariableProps {
  index: number;
  onDeleteClicked(): void;
  previewStage: IStageForSpelPreview;
}

function FormikVariable({ index, onDeleteClicked, previewStage }: IFormikVariableProps) {
  const [touchedOverride, setTouchedOverride] = React.useState(undefined);

  const variableNameInputAsLabel = (
    <FormikFormField
      name={`variables[${index}].key`}
      required={true}
      input={(inputProps) => <TextInput {...inputProps} placeholder="Variable name" />}
      layout={VariableNameFormLayout}
    />
  );

  const actions = (
    <Tooltip value="Remove variable">
      <button className="btn btn-sm btn-default" onClick={onDeleteClicked}>
        <span className="glyphicon glyphicon-trash" />
      </button>
    </Tooltip>
  );

  const fieldName = `variables[${index}].value`;

  return (
    <FormikFormField
      name={fieldName}
      onChange={(value) => {
        // When the user has entered anything, mark the field as touched so warnings are shown
        if (value && !touchedOverride) {
          setTouchedOverride(true);
        }
      }}
      touched={touchedOverride}
      label={variableNameInputAsLabel}
      actions={actions}
      input={(inputProps) => (
        <SpelInput
          {...inputProps}
          placeholder="Variable value, e.g. ${trigger.buildInfo.number}"
          previewStage={previewStage}
        />
      )}
    />
  );
}

function VariableNameFormLayout(props: ILayoutProps) {
  const { input, validation } = props;
  const { messageNode, category, hidden } = validation;
  return (
    <div className="flex-container-v margin-between-md">
      {input}
      {!hidden && <ValidationMessage message={messageNode} type={category} />}
    </div>
  );
}
