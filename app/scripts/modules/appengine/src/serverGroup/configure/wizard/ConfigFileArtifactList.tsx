import * as React from 'react';
import * as classNames from 'classnames';

import { IArtifact, IExpectedArtifact, IPipeline, IStage, StageArtifactSelectorDelegate } from '@spinnaker/core';

interface IConfigFileArtifactListProps {
  configArtifacts: any[];
  pipeline: IPipeline;
  stage: IStage;
  updateConfigArtifacts: (configArtifacts: any[]) => void;
  updatePipeline: (changes: Partial<IPipeline>) => void;
}

export const ConfigFileArtifactList = (props: IConfigFileArtifactListProps) => {
  const addConfigArtifact = () => {
    props.updateConfigArtifacts(props.configArtifacts.concat([{}]));
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

  const onExpectedArtifactAccountSelected = (account: string, index: number): void => {
    const newConfigArtifacts = [...props.configArtifacts];
    newConfigArtifacts.splice(index, 1, { ...newConfigArtifacts[index], account });
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
              <StageArtifactSelectorDelegate
                artifact={a.artifact}
                excludedArtifactTypePatterns={[]}
                expectedArtifactId={a.id}
                fieldColumns={7}
                label={''}
                onArtifactEdited={(artifact: IArtifact) => {
                  onExpectedArtifactEdited(artifact, i);
                }}
                onExpectedArtifactSelected={(expectedArtifact: IExpectedArtifact) => {
                  onExpectedArtifactSelected(expectedArtifact, i);
                }}
                pipeline={props.pipeline}
                selectedArtifactAccount={a.account}
                selectedArtifactId={a.id}
                setArtifactAccount={(account: string) => {
                  onExpectedArtifactAccountSelected(account, i);
                }}
                setArtifactId={(artifactId: string) => {
                  onChangeExpectedArtifactId(artifactId, i);
                }}
                stage={props.stage}
                updatePipeline={props.updatePipeline}
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
