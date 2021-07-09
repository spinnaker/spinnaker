import { partition } from 'lodash';
import React from 'react';
import Select from 'react-select';

import { ArtifactTypePatterns, ExpectedArtifactModal, ExpectedArtifactService } from '../../../../artifact';
import { IExpectedArtifact, IPipeline } from '../../../../domain';
import { createFakeReactSyntheticEvent, IFormInputProps } from '../../../../presentation';
import { Registry } from '../../../../registry';

import './triggerArtifactConstraintSelectorInput.less';

export interface ITriggerArtifactConstraintSelectorProps extends IFormInputProps {
  pipeline: IPipeline;
  triggerType: string;
  value?: string[];
  addExpectedArtifact: (artifact: IExpectedArtifact) => void;
  updateExpectedArtifact: (artifact: IExpectedArtifact) => void;
  removeExpectedArtifact: (artifact: IExpectedArtifact) => void;
}

export class TriggerArtifactConstraintSelectorInput extends React.Component<ITriggerArtifactConstraintSelectorProps> {
  private defaultExcludedArtifactTypePatterns = [
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
  ];

  private excludedArtifactTypes = () => {
    const triggerConfig = Registry.pipeline.getTriggerConfig(this.props.triggerType);
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
      }).then(
        (artifact) => {
          this.props.addExpectedArtifact(artifact);
          this.props.onChange(
            createFakeReactSyntheticEvent({
              name: this.props.name,
              value: (this.props.value || []).concat([artifact.id]),
            }),
          );
        },
        () => null,
      );
      return;
    }

    const selected = (this.props.value || []).slice(0);
    selected[index] = selectedArtifact.id;
    this.props.onChange(createFakeReactSyntheticEvent({ name: this.props.name, value: selected }));
  };

  private removeExpectedArtifact = (artifact: IExpectedArtifact) => {
    const selected = (this.props.value || []).slice();
    this.props.removeExpectedArtifact(artifact);
    this.props.onChange(
      createFakeReactSyntheticEvent({
        name: this.props.name,
        value: selected.filter((id) => id !== artifact.id),
      }),
    );
  };

  private editExpectedArtifact = (artifact: IExpectedArtifact) => {
    ExpectedArtifactModal.show({
      expectedArtifact: artifact,
      pipeline: this.props.pipeline,
      excludedArtifactTypePatterns: this.excludedArtifactTypes(),
      excludedDefaultArtifactTypePatterns: this.defaultExcludedArtifactTypePatterns,
    }).then(
      (artifact) => this.props.updateExpectedArtifact(artifact),
      () => null,
    );
  };

  private renderArtifact = (artifact: IExpectedArtifact) => {
    return <span>{artifact && artifact.displayName}</span>;
  };

  public render() {
    const { pipeline } = this.props;
    const selected = this.props.value || [];
    const expectedArtifacts = pipeline.expectedArtifacts || [];

    const isSelected = (artifact: IExpectedArtifact) => selected.includes(artifact.id);
    const [selectedArtifacts, availableArtifacts] = partition(expectedArtifacts, isSelected);

    const createNewArtifact = ExpectedArtifactService.createEmptyArtifact();
    createNewArtifact.id = '__create.new.artifact';
    createNewArtifact.displayName = 'Define a new artifact...';
    availableArtifacts.push(createNewArtifact);

    const renderSelect = (i: number, artifact?: IExpectedArtifact) => {
      const editButtons = (
        <>
          <a className="clickable glyphicon glyphicon-edit" onClick={() => this.editExpectedArtifact(artifact)} />
          <a className="clickable glyphicon glyphicon-trash" onClick={() => this.removeExpectedArtifact(artifact)} />
        </>
      );

      return (
        <div key={(artifact && artifact.id) || 'new'} className="flex-container-h baseline margin-between-md">
          <Select
            className="flex-grow"
            clearable={false}
            value={artifact}
            onChange={(a: IExpectedArtifact) => this.handleChange(i, a)}
            options={availableArtifacts}
            optionRenderer={this.renderArtifact}
            valueRenderer={this.renderArtifact}
            placeholder="Select or define an artifact..."
          />

          <div style={{ minWidth: '50px' }} className="flex-container-h baseline margin-between-md">
            {artifact && editButtons}
          </div>
        </div>
      );
    };

    return (
      <div className="TriggerArtifactConstraintSelector_Selects flex-container-v margin-between-md">
        {selectedArtifacts.map((artifact, i) => renderSelect(i, artifact))}
        {renderSelect(selected.length)}
      </div>
    );
  }
}
