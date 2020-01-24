import React from 'react';
import classNames from 'classnames';
import { FormikProps } from 'formik';
import { isEqual, pick } from 'lodash';
import { Option } from 'react-select';

import { Application } from 'core/application';
import { SETTINGS } from 'core/config/settings';
import { IExpectedArtifact, IPipeline, ITrigger, ITriggerTypeConfig } from 'core/domain';
import { HelpField } from 'core/help/HelpField';
import { TriggerArtifactConstraintSelectorInput } from './artifacts';
import {
  CheckboxInput,
  FormField,
  FormikFormField,
  IFormInputProps,
  ReactSelectInput,
  SpinFormik,
  Tooltip,
  ValidationMessage,
  WatchValue,
} from 'core/presentation';
import { Registry } from 'core/registry';

import { RunAsUserInput } from './RunAsUser';
import './Trigger.less';

export interface ITriggerProps {
  application: Application;
  index: number;
  pipeline: IPipeline;
  removeTrigger: (index: number) => void;
  trigger: ITrigger;
  updateExpectedArtifacts: (expectedArtifacts: IExpectedArtifact[]) => void;
  updateTrigger: (index: number, changes: { [key: string]: any }) => void;
}

export const Trigger = (props: ITriggerProps) => {
  function getValidateFn() {
    const triggerType = Registry.pipeline.getTriggerTypes().find(type => type.key === props.trigger.type);
    const validateWithContext = (values: ITrigger) => triggerType.validateFn(values, { pipeline: props.pipeline });
    return triggerType && triggerType.validateFn ? validateWithContext : undefined;
  }

  return (
    <SpinFormik<ITrigger>
      onSubmit={() => null}
      initialValues={props.trigger}
      validate={getValidateFn()}
      render={formik => <TriggerForm {...props} formik={formik} />}
    />
  );
};

const commonTriggerFields: Array<keyof ITrigger> = [
  'enabled',
  'rebake',
  'user',
  'type',
  'expectedArtifactIds',
  'runAsUser',
  'excludedArtifactTypePatterns',
];

function TriggerForm(triggerFormProps: ITriggerProps & { formik: FormikProps<ITrigger> }) {
  const { formik, pipeline, index, updateTrigger, updateExpectedArtifacts } = triggerFormProps;
  const trigger = formik.values as Readonly<ITrigger>;

  const { type } = trigger;
  const triggerTypes = React.useMemo(() => Registry.pipeline.getTriggerTypes(), []);
  const triggerConfig = React.useMemo(() => triggerTypes.find(x => x.key === trigger.type), [
    triggerTypes,
    trigger.type,
  ]);
  const disableAutoTriggering = SETTINGS.disableAutoTriggering || [];

  // Clear out all non-common fields when the type is changed
  const handleTypeChange = (newType: string) => {
    const newValues = pick(trigger, commonTriggerFields) as ITrigger;
    newValues.enabled = disableAutoTriggering.includes(newType) ? false : trigger.enabled;
    newValues.type = newType;
    formik.setValues(newValues);
  };

  const updateTriggerFields = (changes: { [key: string]: any }) => {
    const updatedTrigger = { ...trigger, ...changes };
    formik.setValues(updatedTrigger);
  };

  const triggerComponentProps = {
    ...triggerFormProps,
    trigger: formik.values,
    formik,
    pipelineId: pipeline.id,
    triggerUpdated: updateTriggerFields,
  };

  const EmptyComponent = () => <></>;
  // The actual trigger component for the specific trigger type
  const TriggerComponent = (triggerConfig && triggerConfig.component) || EmptyComponent;

  const defineExpectedArtifact = (expectedArtifact: IExpectedArtifact) => {
    const pipelineExpectedArtifacts = (pipeline.expectedArtifacts || []).slice();
    const triggerExpectedArtifactIds = (trigger.expectedArtifactIds || []).slice();

    const editArtifactIdx = pipelineExpectedArtifacts.findIndex(artifact => artifact.id === expectedArtifact.id);

    if (editArtifactIdx !== -1) {
      pipelineExpectedArtifacts.splice(editArtifactIdx, 1, expectedArtifact);
    } else {
      pipelineExpectedArtifacts.push(expectedArtifact);
      triggerExpectedArtifactIds.push(expectedArtifact.id);
    }

    updateExpectedArtifacts(pipelineExpectedArtifacts);
    updateTriggerFields({ expectedArtifactIds: triggerExpectedArtifactIds });
  };

  const showRunAsUser = SETTINGS.feature.fiatEnabled && !SETTINGS.feature.managedServiceAccounts;
  const fieldSetClassName = classNames({ 'templated-pipeline-item': trigger.inherited, Trigger: true });

  const expectedArtifactOptions =
    pipeline.expectedArtifacts && pipeline.expectedArtifacts.map(e => ({ label: e.displayName, value: e.id }));

  const showArtifactConstraints = SETTINGS.feature['artifactsRewrite'];
  const showOldArtifactConstraints =
    !showArtifactConstraints &&
    SETTINGS.feature['artifacts'] &&
    pipeline.expectedArtifacts &&
    pipeline.expectedArtifacts.length > 0;

  return (
    <fieldset disabled={trigger.inherited} className={fieldSetClassName}>
      <WatchValue
        value={trigger}
        isEqual={isEqual} // deep compare
        onChange={updatedTrigger => updateTrigger(index, updatedTrigger)}
      />

      <div className="form-horizontal panel-pipeline-phase">
        <FormField // use FormField to avoid race condition setting type and then clearing out non-common fields
          label="Type"
          actions={
            trigger.inherited ? (
              <FromTemplateMessage />
            ) : (
              <RemoveTriggerButton onClick={() => triggerFormProps.removeTrigger(index)} />
            )
          }
          value={trigger.type}
          onChange={e => handleTypeChange(e.target.value)}
          input={props => <TriggerTypeSelectInput {...props} triggerConfig={triggerConfig} />}
        />

        <TriggerComponent {...triggerComponentProps} />

        {showRunAsUser && (
          <FormikFormField
            name="runAsUser"
            label="Run As User"
            help={<HelpField id="pipeline.config.trigger.runAsUser" />}
            input={props => <RunAsUserInput {...props} />}
          />
        )}

        {showOldArtifactConstraints && (
          <FormikFormField
            name="expectedArtifactIds"
            label="Artifact Constraints"
            help={<HelpField id="pipeline.config.expectedArtifact" />}
            input={props => <ReactSelectInput {...props} multi={true} options={expectedArtifactOptions} />}
          />
        )}

        {showArtifactConstraints && (
          <FormikFormField
            fastField={false}
            name="expectedArtifactIds"
            label="Artifact Constraints"
            help={<HelpField id="pipeline.config.expectedArtifact" />}
            input={props => (
              <TriggerArtifactConstraintSelectorInput
                {...props}
                pipeline={pipeline}
                triggerType={trigger.type}
                onDefineExpectedArtifact={defineExpectedArtifact}
              />
            )}
          />
        )}

        {type && disableAutoTriggering.includes(type) && <AutoTriggeringDisabledMessage />}

        {type && !disableAutoTriggering.includes(type) && (
          <FormField
            label=""
            value={trigger.enabled}
            onChange={() => formik.setFieldValue('enabled', !trigger.enabled)}
            input={props => <CheckboxInput {...props} text="Trigger Enabled" />}
          />
        )}
      </div>
    </fieldset>
  );
}

function TriggerTypeSelectInput(props: IFormInputProps & { triggerConfig: ITriggerTypeConfig }) {
  const triggerTypes = Registry.pipeline
    .getTriggerTypes()
    .map(t => ({ label: t.label, value: t.key, description: t.description }));

  const optionRenderer = (option: Option<any>) => (
    <p className="flex-container-h baseline margin-between-md">
      <span style={{ fontWeight: 'bold', minWidth: '100px' }}>{option.label}</span>
      <span>{option.description}</span>
    </p>
  );

  return <ReactSelectInput {...props} options={triggerTypes} optionRenderer={optionRenderer} />;
}

function RemoveTriggerButton(props: { onClick: () => void }) {
  return (
    <button className="btn btn-sm btn-default" onClick={props.onClick}>
      <span className="glyphicon glyphicon-trash" />
      <span className="visible-xl-inline">Remove trigger</span>
    </button>
  );
}

function AutoTriggeringDisabledMessage() {
  const message =
    'Automatic triggering is disabled for this trigger type. ' +
    'You can still use this as a trigger to supply data ' +
    'to downstream stages, but will need to manually execute this pipeline.';
  return <FormField label="" input={() => <ValidationMessage message={message} type="warning" />} />;
}

function FromTemplateMessage() {
  return (
    <span className="templated-pipeline-item__status btn btn-sm btn-default">
      <Tooltip value="From Template">
        <i className="from-template fa fa-table" />
      </Tooltip>
      <span className="visible-xl-inline">From Template</span>
    </span>
  );
}
