import * as React from 'react';
import * as classNames from 'classnames';
import { pick } from 'lodash';

import { Application } from 'core/application';
import { IExpectedArtifact, IPipeline, ITrigger, ITriggerTypeConfig } from 'core/domain';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';
import { HelpField } from 'core/help/HelpField';
import { TriggerArtifactConstraintSelector } from 'core/pipeline/config/triggers/artifacts';
import { ValidationMessage } from 'core/validation';
import {
  CheckboxInput,
  FormField,
  Tooltip,
  LayoutProvider,
  ReactSelectInput,
  IFormInputProps,
} from 'core/presentation';

import { LegacyTriggerCommonLayout } from './TriggerFieldLayout';

export interface ITriggerProps {
  application: Application;
  index: number;
  pipeline: IPipeline;
  removeTrigger: (index: number) => void;
  trigger: ITrigger;
  updateExpectedArtifacts: (expectedArtifacts: IExpectedArtifact[]) => void;
  updateTrigger: (index: number, changes: { [key: string]: any }) => void;
}

export class Trigger extends React.Component<ITriggerProps> {
  private disableAutoTriggering = SETTINGS.disableAutoTriggering || [];
  private triggerTypes = Registry.pipeline.getTriggerTypes();

  private getSelectedTriggerType = (): string => {
    return this.props.trigger.type || '';
  };

  private getTriggerConfig = (): ITriggerTypeConfig => {
    const selectedTriggerType = this.getSelectedTriggerType();
    return this.triggerTypes.find(triggerType => triggerType.key === selectedTriggerType);
  };

  private handleTriggerEnabled = () => {
    const enabled = !this.props.trigger.enabled;
    this.updateTriggerFields({ enabled });
  };

  private handleTypeChange = (type: string) => {
    const commonFields: Array<keyof ITrigger> = [
      'enabled',
      'rebake',
      'user',
      'type',
      'expectedArtifactIds',
      'runAsUser',
      'excludedArtifactTypePatterns',
    ];

    // Clear out all non-common fields when the type is changed
    const trigger = pick(this.props.trigger, commonFields) as ITrigger;
    trigger.enabled = this.disableAutoTriggering.includes(type) ? false : trigger.enabled;
    trigger.type = type;
    this.props.updateTrigger(this.props.index, trigger);
  };

  private updateTriggerFields = (changes: { [key: string]: any }) => {
    const trigger = { ...this.props.trigger, ...changes };
    this.props.updateTrigger(this.props.index, trigger);
  };

  private TriggerContents = () => {
    const triggerConfig = this.getTriggerConfig();
    if (triggerConfig) {
      const TriggerComponent = triggerConfig.component;
      const componentProps = {
        ...this.props,
        pipelineId: this.props.pipeline.id,
        triggerUpdated: this.updateTriggerFields,
      };
      return <TriggerComponent {...componentProps} />;
    }
    return <div />;
  };

  private removeTrigger = () => {
    this.props.removeTrigger(this.props.index);
  };

  private defineExpectedArtifact = (expectedArtifact: IExpectedArtifact) => {
    const expectedArtifacts = this.props.pipeline.expectedArtifacts;
    if (expectedArtifacts) {
      const editingArtifact = expectedArtifacts.findIndex(artifact => artifact.id === expectedArtifact.id);
      if (editingArtifact >= 0) {
        const newExpectedArtifactsList = expectedArtifacts.splice(0);
        newExpectedArtifactsList.splice(editingArtifact, 1, expectedArtifact);
        this.props.updateExpectedArtifacts(newExpectedArtifactsList);
      } else {
        this.props.updateExpectedArtifacts([...expectedArtifacts, expectedArtifact]);
      }
    } else {
      this.props.updateExpectedArtifacts([expectedArtifact]);
    }

    const expectedArtifactIds = this.props.trigger.expectedArtifactIds;
    if (expectedArtifactIds && !expectedArtifactIds.includes(expectedArtifact.id)) {
      this.updateTriggerFields({
        expectedArtifactIds: [...expectedArtifactIds, expectedArtifact.id],
      });
    } else {
      this.updateTriggerFields({
        expectedArtifactIds: [expectedArtifact.id],
      });
    }
  };

  private changeOldExpectedArtifacts = (expectedArtifacts: any[]) => {
    this.changeExpectedArtifacts(expectedArtifacts.map(e => e.value));
  };

  private changeExpectedArtifacts = (expectedArtifacts: string[]) => {
    this.updateTriggerFields({
      expectedArtifactIds: expectedArtifacts,
    });
  };

  public render() {
    const type = this.getSelectedTriggerType();
    const triggerConfig = this.getTriggerConfig();
    const { pipeline, trigger } = this.props;
    const { TriggerContents } = this;
    const fieldSetClassName = classNames({ 'templated-pipeline-item': trigger.inherited });

    const expectedArtifactOptions =
      pipeline.expectedArtifacts && pipeline.expectedArtifacts.map(e => ({ label: e.displayName, value: e.id }));

    const showArtifactConstraints =
      !SETTINGS.feature['artifactsRewrite'] &&
      SETTINGS.feature['artifacts'] &&
      pipeline.expectedArtifacts &&
      pipeline.expectedArtifacts.length > 0;

    return (
      <LayoutProvider value={LegacyTriggerCommonLayout}>
        <fieldset disabled={trigger.inherited} className={fieldSetClassName}>
          <div className="form-horizontal panel-pipeline-phase">
            <FormField
              label="Type"
              value={type}
              actions={
                trigger.inherited ? <FromTemplateMessage /> : <RemoveTriggerButton onClick={this.removeTrigger} />
              }
              onChange={e => this.handleTypeChange(e.target.value)}
              input={props => <TriggerTypeSelectInput {...props} triggerConfig={triggerConfig} />}
            />

            <div className="form-group">
              <div className="col-md-10">
                <TriggerContents />
              </div>
            </div>

            {showArtifactConstraints && (
              <FormField
                label="Artifact Constraints"
                help={<HelpField id="pipeline.config.expectedArtifacts" />}
                value={trigger.expectedArtifactIds}
                onChange={e => this.changeOldExpectedArtifacts(e.target.value)}
                input={props => <ReactSelectInput {...props} multi={true} options={expectedArtifactOptions} />}
              />
            )}

            {SETTINGS.feature['artifactsRewrite'] && (
              <FormField
                label="Artifact Constraints"
                help={<HelpField id="pipeline.config.expectedArtifact" />}
                input={() => (
                  <div className="row">
                    <TriggerArtifactConstraintSelector
                      pipeline={pipeline}
                      trigger={trigger}
                      selected={trigger.expectedArtifactIds}
                      onDefineExpectedArtifact={this.defineExpectedArtifact}
                      onChangeSelected={this.changeExpectedArtifacts}
                    />
                  </div>
                )}
              />
            )}

            {type && this.disableAutoTriggering.includes(type) && <AutoTriggeringDisabledMessage />}

            {type && !this.disableAutoTriggering.includes(type) && (
              <FormField
                label=""
                value={trigger.enabled}
                onChange={this.handleTriggerEnabled}
                input={props => <CheckboxInput {...props} text="Trigger Enabled" />}
              />
            )}
          </div>
        </fieldset>
      </LayoutProvider>
    );
  }
}

function TriggerTypeSelectInput(props: IFormInputProps & { triggerConfig: ITriggerTypeConfig }) {
  const { triggerConfig } = props;
  const triggerTypes = Registry.pipeline.getTriggerTypes().map(t => ({ label: t.label, value: t.key }));
  const triggerTypeOptions = [{ label: 'Select...', value: '' }, ...triggerTypes];

  return (
    <div className="row">
      <div className="col-md-5">
        <ReactSelectInput {...props} clearable={false} options={triggerTypeOptions} />
      </div>
      <div className="col-md-7">{triggerConfig && triggerConfig.description}</div>
    </div>
  );
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
