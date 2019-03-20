import './triggerArtifactConstraintSelector.less';

import { module } from 'angular';
import * as React from 'react';
import Select from 'react-select';
import { react2angular } from 'react2angular';

import { ArtifactTypePatterns, ExpectedArtifactModal, ExpectedArtifactService } from 'core/artifact';
import { IExpectedArtifact, IPipeline, ITrigger } from 'core/domain';
import { Registry } from 'core/registry';

export interface ITriggerArtifactConstraintSelectorProps {
  pipeline: IPipeline;
  trigger: ITrigger;
  selected?: string[]; // expected artifact ids
  onDefineExpectedArtifact: (artifact: IExpectedArtifact) => void;
  onChangeSelected: (selected: string[], referer: any) => void;
}

export class TriggerArtifactConstraintSelector extends React.Component<ITriggerArtifactConstraintSelectorProps> {
  private defaultExcludedArtifactTypePatterns = [
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
  ];

  private excludedArtifactTypes = () => {
    const triggerConfig = Registry.pipeline
      .getTriggerTypes()
      .filter(config => config.key === this.props.trigger.type)
      .pop();
    return this.defaultExcludedArtifactTypePatterns.concat(
      (triggerConfig && triggerConfig.excludedArtifactTypePatterns) || [],
    );
  };

  private handleChange = (index: number, selectedArtifact: IExpectedArtifact) => {
    if (selectedArtifact.id === '__create.new.artifact') {
      ExpectedArtifactModal.show({
        pipeline: this.props.pipeline,
        excludedArtifactTypePatterns: this.excludedArtifactTypes(),
        excludedDefaultArtifactTypePatterns: this.defaultExcludedArtifactTypePatterns,
      }).then(this.props.onDefineExpectedArtifact);
      return;
    }

    const selected = (this.props.selected || []).slice(0);
    selected[index] = selectedArtifact.id;
    this.props.onChangeSelected(selected, this.props.trigger);
  };

  private removeExpectedArtifact = (artifact: IExpectedArtifact) => {
    const selected = (this.props.selected || []).slice(0);
    selected.splice(selected.findIndex(artifactId => artifact.id === artifactId), 1);
    this.props.onChangeSelected(selected, this.props.trigger);
  };

  private editExpectedArtifact = (artifact: IExpectedArtifact) => {
    ExpectedArtifactModal.show({
      expectedArtifact: artifact,
      pipeline: this.props.pipeline,
      excludedArtifactTypePatterns: this.excludedArtifactTypes(),
      excludedDefaultArtifactTypePatterns: this.defaultExcludedArtifactTypePatterns,
    }).then((editedArtifact: IExpectedArtifact) => {
      this.props.onDefineExpectedArtifact(editedArtifact);
      this.props.onChangeSelected(this.props.selected, this.props.trigger);
    });
  };

  private renderArtifact = (artifact: IExpectedArtifact) => {
    return <span>{artifact && artifact.displayName}</span>;
  };

  public render() {
    const { pipeline } = this.props;
    const selected = this.props.selected || [];
    const expectedArtifacts = pipeline.expectedArtifacts || [];
    const selectedAsArtifacts = expectedArtifacts.filter(artifact => selected.includes(artifact.id));
    const availableArtifacts = [...expectedArtifacts.filter(artifact => !selected.includes(artifact.id))];

    const createNewArtifact = ExpectedArtifactService.createEmptyArtifact();
    createNewArtifact.id = '__create.new.artifact';
    createNewArtifact.displayName = 'Define a new artifact...';
    availableArtifacts.push(createNewArtifact);

    const renderSelect = (i: number, artifact?: IExpectedArtifact) => (
      <div key={(artifact && artifact.id) || 'new'}>
        <div className="col-md-10">
          <div className="artifact-select input-sm">
            <Select
              clearable={false}
              value={artifact}
              onChange={(a: IExpectedArtifact) => this.handleChange(i, a)}
              options={availableArtifacts}
              optionRenderer={this.renderArtifact}
              valueRenderer={this.renderArtifact}
              placeholder="Select or define an artifact..."
            />
          </div>
        </div>
        {artifact && (
          <div className="col-md-2">
            <a className="glyphicon glyphicon-edit" onClick={() => this.editExpectedArtifact(artifact)} />
            <a className="glyphicon glyphicon-trash" onClick={() => this.removeExpectedArtifact(artifact)} />
          </div>
        )}
      </div>
    );
    const renderSelectEditable = (artifact: IExpectedArtifact, i: number) => renderSelect(i, artifact);

    return (
      <>
        {selectedAsArtifacts.map(renderSelectEditable)}
        {renderSelect(selected.length)}
      </>
    );
  }
}

export const TRIGGER_ARTIFACT_CONSTRAINT_SELECTOR_REACT = 'spinnaker.core.trigger.artifact.selector.react';
module(TRIGGER_ARTIFACT_CONSTRAINT_SELECTOR_REACT, []).component(
  'triggerArtifactConstraintSelectorReact',
  react2angular(TriggerArtifactConstraintSelector, [
    'pipeline',
    'trigger',
    'selected',
    'onDefineExpectedArtifact',
    'onChangeSelected',
  ]),
);
