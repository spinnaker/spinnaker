import * as React from 'react';
import Select, { Option } from 'react-select';

import { Application } from 'core/application';
import { IExpectedArtifact, IPipeline, ITrigger, ITriggerTypeConfig } from 'core/domain';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';
import { HelpField } from 'core/help/HelpField';
import { TriggerArtifactConstraintSelector } from 'core/pipeline/config/triggers/artifacts';
import { CheckboxInput, Tooltip } from 'core/presentation';

export interface ITriggerProps {
  application: Application;
  index: number;
  pipeline: IPipeline;
  removeTrigger: (index: number) => void;
  trigger: ITrigger;
  updateExpectedArtifacts: (expectedArtifacts: IExpectedArtifact[]) => void;
  updateTrigger: (index: number, changes: { [key: string]: any }) => void;
}

export interface ITriggerState {
  type: string;
  triggerConfig?: ITriggerTypeConfig;
}

export class Trigger extends React.Component<ITriggerProps, ITriggerState> {
  private disableAutoTriggering = SETTINGS.disableAutoTriggering || [];
  private triggerTypes = Registry.pipeline.getTriggerTypes();

  constructor(props: ITriggerProps) {
    super(props);
    this.state = {
      type: props.trigger.type || '',
    };
  }

  private handleTriggerEnabled = () => {
    this.triggerUpdated({
      enabled: !this.props.trigger.enabled,
    });
  };

  private handleTypeChange = (option: Option<string>) => {
    const type = option.value;
    const triggerConfigs = this.triggerTypes.filter(function(config) {
      return config.key === type;
    });
    this.setState({
      type,
      triggerConfig: triggerConfigs.length ? triggerConfigs[0] : undefined,
    });
    if (this.disableAutoTriggering.includes(type)) {
      this.triggerUpdated({
        enabled: false,
      });
    }
  };

  private triggerUpdated = (changes: { [key: string]: any }) => {
    this.props.updateTrigger(this.props.index, changes);
  };

  private TriggerContents = () => {
    const { triggerConfig } = this.state;
    if (triggerConfig) {
      const TriggerComponent = triggerConfig.component;
      const componentProps = {
        ...this.props,
        pipelineId: this.props.pipeline.id,
        triggerUpdated: this.triggerUpdated,
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
      this.triggerUpdated({
        expectedArtifactIds: [...expectedArtifactIds, expectedArtifact.id],
      });
    } else {
      this.triggerUpdated({
        expectedArtifactIds: [expectedArtifact.id],
      });
    }
  };

  private changeOldExpectedArtifacts = (expectedArtifacts: any[]) => {
    this.changeExpectedArtifacts(expectedArtifacts.map(e => e.value));
  };

  private changeExpectedArtifacts = (expectedArtifacts: string[]) => {
    this.triggerUpdated({
      expectedArtifactIds: expectedArtifacts,
    });
  };

  public render() {
    const { triggerConfig, type } = this.state;
    const { pipeline, trigger } = this.props;
    const { TriggerContents } = this;
    const fieldSetClassName = classNames({ 'templated-pipeline-item': trigger.inherited });
    return (
      <div className="row">
        <div className="col-md-12">
          <fieldset disabled={trigger.inherited} className={`templated-pipeline-item`}>
            <div className="form-horizontal panel-pipeline-phase">
              <div className="form-group row">
                <div className="col-md-10">
                  <div className="row">
                    <label className="col-md-3 sm-label-right">Type</label>
                    <div className="col-md-4">
                      <Select
                        className="input-sm"
                        clearable={false}
                        onChange={this.handleTypeChange}
                        options={[
                          { label: 'Select...', value: '' },
                          ...Registry.pipeline.getTriggerTypes().map(t => ({
                            label: t.label,
                            value: t.key,
                          })),
                        ]}
                        value={type}
                      />
                    </div>
                    <div className="col-md-5">{triggerConfig && triggerConfig.description}</div>
                  </div>
                </div>
                {trigger.inherited ? (
                  <span className="templated-pipeline-item__status btn btn-sm btn-default">
                    <Tooltip value="From Template">
                      <i className="from-template fa fa-table"/>
                    </Tooltip>
                    <span className="visible-xl-inline">From Template</span>
                  </span>
                ) : (
                  <div className="col-md-2 text-right">
                    <button className="btn btn-sm btn-default" onClick={this.removeTrigger}>
                      <span className="glyphicon glyphicon-trash" />
                      <span className="visible-xl-inline">Remove trigger</span>
                    </button>
                  </div>
                )}
              </div>
              <div className="form-group">
                <div className="col-md-10">
                  <TriggerContents />
                </div>
              </div>
              {!SETTINGS.feature['artifactsRewrite'] &&
                SETTINGS.feature['artifacts'] &&
                pipeline.expectedArtifacts &&
                pipeline.expectedArtifacts.length > 0 && (
                  <div className="form-group">
                    <div className="col-md-10">
                      <div className="form-group">
                        <label className="col-md-3 sm-label-right">
                          <span>Artifact Constraints </span>
                          <HelpField id="pipeline.config.expectedArtifact" />
                        </label>
                        <div className="col-md-9">
                          <Select
                            multi={true}
                            onChange={this.changeOldExpectedArtifacts}
                            options={pipeline.expectedArtifacts.map(e => ({
                              label: e.displayName,
                              value: e.id,
                            }))}
                            value={trigger.expectedArtifactIds}
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              {SETTINGS.feature['artifactsRewrite'] && (
                <div className="form-group">
                  <div className="col-md-10">
                    <div className="form-group">
                      <label className="col-md-3 sm-label-right">
                        <span>Artifact Constraints </span>
                        <HelpField id="pipeline.config.expectedArtifact" />
                      </label>
                      <div className="col-md-9 row">
                        <TriggerArtifactConstraintSelector
                          pipeline={pipeline}
                          trigger={trigger}
                          selected={trigger.expectedArtifactIds}
                          onDefineExpectedArtifact={this.defineExpectedArtifact}
                          onChangeSelected={this.changeExpectedArtifacts}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {type && !this.disableAutoTriggering.includes(type) && (
                <div className="form-group">
                  <div className="col-md-10">
                    <div className="row">
                      <div className="col-md-9 col-md-offset-3 checkbox">
                        <label>
                          <CheckboxInput
                            checked={trigger.enabled}
                            onChange={this.handleTriggerEnabled}
                            text={'Trigger Enabled'}
                          />
                        </label>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {type && this.disableAutoTriggering.includes(type) && (
                <div className="form-group">
                  <div className="col-md-10 col-md-offset-1">
                    <div className="row">
                      <div className="col-md-8 col-md-offset-3">
                        <div className="alert alert-warning">
                          Automatic triggering is disabled for this trigger type. You can still use this as a trigger to
                          supply data to downstream stages, but will need to manually execute this pipeline.
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
            <div className="stage-details">
              <div className="row">
                <div className="stage-body" />
              </div>
            </div>
          </fieldset>
        </div>
      </div>
    );
  }
}
