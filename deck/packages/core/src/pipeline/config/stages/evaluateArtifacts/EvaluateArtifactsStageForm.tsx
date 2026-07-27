import React, { useState } from 'react';

import type { IArtifact } from './EvaluateArtifactsStageModal';
import { EvaluateArtifactsStageModal } from './EvaluateArtifactsStageModal';
import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { UUIDGenerator } from '../../../../utils';

export const EvaluateArtifactsStageForm = (props: IFormikStageConfigInjectedProps) => {
  const stage = props.formik.values;
  const { artifactContents, expectedArtifacts } = stage;
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedArtifact, setSelectedArtifact] = useState<IArtifact>({
    name: '',
    contents: '',
    id: UUIDGenerator.generateUuid(),
  });

  const toggleModal = () => {
    setModalVisible(!modalVisible);
  };

  const showModal = (artifact: IArtifact) => {
    const propArtifact = artifact || { name: '', contents: '', id: UUIDGenerator.generateUuid() };
    setSelectedArtifact(propArtifact);
    toggleModal();
  };

  const deleteArtifact = (id: string) => {
    stage.artifactContents = artifactContents.filter((artifact: IArtifact) => artifact.id !== id);
    stage.expectedArtifacts = expectedArtifacts.filter((artifact: any) => artifact.id !== id);
    props.formik.setFieldValue('artifactContents', stage.artifactContents);
  };

  return (
    <>
      <table className="table table-condensed">
        <thead>
          <tr>
            <th>Display name</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {artifactContents &&
            artifactContents.map((artifact: IArtifact) => (
              <tr key={artifact.id}>
                <td>{artifact.name}</td>
                <td>
                  <a className="glyphicon glyphicon-edit" onClick={() => showModal(artifact)} />
                  <a className="glyphicon glyphicon-trash" onClick={() => deleteArtifact(artifact.id)} />
                </td>
              </tr>
            ))}
        </tbody>
      </table>
      <button type="button" className="btn btn-block btn-sm add-new" onClick={() => showModal(null)}>
        <span className="glyphicon glyphicon-plus-sign" /> Add Artifact
      </button>
      {modalVisible && (
        <EvaluateArtifactsStageModal toggleModal={toggleModal} stage={stage} artifact={selectedArtifact} />
      )}
    </>
  );
};
