import classNames from 'classnames';
import React from 'react';

import {
  IArtifact,
  IArtifactAccountPair,
  IExpectedArtifact,
  IPipeline,
  IStage,
  StageArtifactSelector,
} from '@spinnaker/core';

interface IConfigFileArtifactListProps {
  configArtifacts: IArtifactAccountPair[];
  pipeline: IPipeline;
  stage: IStage;
  updateConfigArtifacts: (configArtifacts: any[]) => void;
}

export const ConfigFileArtifactList = (props: IConfigFileArtifactListProps) => {
  const addConfigArtifact = () => {
    props.updateConfigArtifacts(props.configArtifacts.concat([{ id: '', account: '' }]));
  };

  const deleteConfigArtifact = (index: number) => {
    const newConfigArtifacts = [...props.configArtifacts];
    newConfigArtifacts.splice(index, 1);
    props.updateConfigArtifacts(newConfigArtifacts);
  };

  const onExpectedArtifactEdited = (artifact: IArtifact, index: number): void => {
    const newConfigArtifacts = [...props.configArtifacts];
    newConfigArtifacts.splice(index, 1, { ...newConfigArtifacts[index], id: null, artifact });
    props.updateConfigArtifacts(newConfigArtifacts);
  };

  const onExpectedArtifactSelected = (expectedArtifact: IExpectedArtifact, index: number): void => {
    onChangeExpectedArtifactId(expectedArtifact.id, index);
  };

  const onChangeExpectedArtifactId = (id: string, index: number): void => {
    const newConfigArtifacts = [...props.configArtifacts];
    newConfigArtifacts.splice(index, 1, { ...newConfigArtifacts[index], id, artifact: null });
    props.updateConfigArtifacts(newConfigArtifacts);
  };

  return (
    <>
      {props.configArtifacts.map((a, i) => {
        return (
          <div
            key={a.id}
            className={classNames('artifact-configuration-section col-md-12', {
              'last-entry': props.configArtifacts.length - 1 === i,
            })}
          >
            <div className="col-md-9">
              <StageArtifactSelector
                artifact={a.artifact}
                excludedArtifactTypePatterns={[]}
                expectedArtifactId={a.artifact == null ? a.id : null}
                onArtifactEdited={(artifact: IArtifact) => {
                  onExpectedArtifactEdited(artifact, i);
                }}
                onExpectedArtifactSelected={(expectedArtifact: IExpectedArtifact) => {
                  onExpectedArtifactSelected(expectedArtifact, i);
                }}
                pipeline={props.pipeline}
                stage={props.stage}
              />
            </div>
            <div className="col-md-1">
              <button type="button" className="btn btn-sm btn-default" onClick={() => deleteConfigArtifact(i)}>
                <span className="glyphicon glyphicon-trash" /> Delete
              </button>
            </div>
          </div>
        );
      })}
      <div className="col-md-7 col-md-offset-3">
        <button className="btn btn-block btn-add-trigger add-new" onClick={() => addConfigArtifact()}>
          <span className="glyphicon glyphicon-plus-sign" /> Add Config Artifact
        </button>
      </div>
    </>
  );
};
