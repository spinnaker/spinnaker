import * as React from 'react';
import { countBy } from 'lodash';
import { FieldArray } from 'formik';

import {
  FormValidator,
  FormikFormField,
  ILayoutProps,
  IValidator,
  LayoutProvider,
  SpelInput,
  StandardFieldLayout,
  TextInput,
  Tooltip,
  ValidationMessage,
  errorMessage,
} from 'core/presentation';
import { IStage } from 'core/domain';
import { FormikStageConfig, IFormikStageConfigInjectedProps, IStageConfigProps } from 'core/pipeline';

import './EvaluateVariablesStageConfig.less';

export interface IEvaluatedVariable {
  key: string;
  value: string;
}

const variableNameValidator: IValidator = (val: string, label: string) =>
  !val.match(/^[a-zA-Z0-9_]+$/) && errorMessage(`${label} should consist only of letters, numbers, or underscore`);

const getDuplicateKeyValidator = (variables: IEvaluatedVariable[]) => {
  const keyCounts = countBy(variables.map(x => x.key), x => x);
  return (key: string) => keyCounts[key] > 1 && `Duplicate key '${key}'`;
};

export function validateEvaluateVariablesStage(stage: IStage) {
  const formValidator = new FormValidator(stage);
  const duplicateKeyValidator = getDuplicateKeyValidator(stage.variables);
  formValidator.field('variables').withValidators(
    formValidator.arrayForEach(item => {
      item
        .field('key', 'Variable Name')
        .required()
        .withValidators(variableNameValidator, duplicateKeyValidator);
      item.field('value', 'Expression').required();
    }),
  );
  return formValidator.validateForm();
}

export function EvaluateVariablesStageConfig(props: IStageConfigProps) {
  const { application, stage, pipeline, updateStage } = props;
  return (
    <FormikStageConfig
      application={application}
      stage={stage}
      pipeline={pipeline}
      validate={validateEvaluateVariablesStage}
      onChange={updateStage}
      render={renderProps => {
        return (
          <LayoutProvider value={StandardFieldLayout}>
            <EvaluateVariablesStageForm {...renderProps} />
          </LayoutProvider>
        );
      }}
    />
  );
}

function EvaluateVariablesStageForm(props: IFormikStageConfigInjectedProps) {
  const { formik } = props;

  React.useEffect(() => {
    const { variables = [] } = props.formik.values;
    const initialTouched = variables.map(() => ({ key: true, value: true }));
    formik.setTouched({ variables: initialTouched } as any);
  }, []);

  const [deleteCount, setDeleteCount] = React.useState(0);

  return (
    <FieldArray
      key={deleteCount}
      name="variables"
      render={arrayHelpers => (
        <div className="EvaluateVariablesStageConfig form-horizontal">
          {formik.values.variables.map((_, index) => {
            const onDeleteClicked = () => {
              setDeleteCount(count => count + 1);
              arrayHelpers.handleRemove(index)();
            };
            return <FormikVariable key={`${deleteCount}-${index}`} index={index} onDeleteClicked={onDeleteClicked} />;
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
}

function FormikVariable({ index, onDeleteClicked }: IFormikVariableProps) {
  return (
    <FormikFormField
      name={`variables[${index}].value`}
      actions={
        <Tooltip value="Remove variable">
          <button className="btn btn-sm btn-default" onClick={onDeleteClicked}>
            <span className="glyphicon glyphicon-trash" />
          </button>
        </Tooltip>
      }
      label={
        <FormikFormField
          name={`variables[${index}].key`}
          required={true}
          input={inputProps => <TextInput {...inputProps} placeholder="Variable name" />}
          layout={VariableNameFormLayout}
        />
      }
      input={inputProps => (
        <SpelInput
          {...inputProps}
          placeholder="Variable value, e.g. ${trigger.properties.myproperty}"
          executionId={null}
          stageId={null}
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
