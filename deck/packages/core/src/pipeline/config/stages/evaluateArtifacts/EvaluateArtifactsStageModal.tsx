import React from 'react';

import { ExpectedArtifactService } from '../../../../artifact';
import { WizardModal } from '../../../../modal';
import { FormikFormField, TextAreaInput, TextInput } from '../../../../presentation';
import { TaskMonitor } from '../../../../task';
import { UUIDGenerator } from '../../../../utils';

export interface IArtifact {
  name: string;
  contents: string;
  id: string;
}

export const EvaluateArtifactsStageModal = ({
  toggleModal,
  stage,
  artifact,
}: {
  toggleModal: () => void;
  stage: any;
  artifact: IArtifact;
}) => {
  const taskMonitor = new TaskMonitor({ title: 'Evaluate Artifacts' });
  const { expectedArtifacts, artifactContents } = stage;

  const generateArtifact = (values: IArtifact) => {
    const { name, id, contents } = values;
    const evaluateArtifactsIndex = artifactContents.findIndex((item: IArtifact) => item.id === id);
    const producedArtifactIndex = expectedArtifacts.findIndex((item: any) => item.id === id);
    const hasProducedArtifact = producedArtifactIndex >= 0 && evaluateArtifactsIndex >= 0;

    if (hasProducedArtifact) {
      stage.artifactContents[evaluateArtifactsIndex].name = name;
      stage.artifactContents[evaluateArtifactsIndex].contents = contents;
      stage.expectedArtifacts[producedArtifactIndex].displayName = name;
      stage.expectedArtifacts[producedArtifactIndex].matchArtifact.name = name;
    } else {
      stage.artifactContents.push(values);
      const newArtifact = {
        id,
        displayName: name,
        usePriorArtifact: false,
        useDefaultArtifact: false,
        matchArtifact: {
          artifactAccount: 'embedded-artifact',
          id: UUIDGenerator.generateUuid(),
          customKind: true,
          name,
          type: 'embedded/base64',
        },
        defaultArtifact: {
          id: UUIDGenerator.generateUuid(),
          customKind: true,
        },
      };
      ExpectedArtifactService.addArtifactTo(newArtifact, stage);
    }
    toggleModal();
  };

  return (
    <div className="fade in modal" tabIndex={-1} role="dialog">
      <div className="modal-dialog" role="document">
        <div className="modal-content">
          <WizardModal
            heading="Artifact Contents"
            initialValues={artifact}
            taskMonitor={taskMonitor}
            dismissModal={toggleModal}
            closeModal={generateArtifact}
            submitButtonLabel={`${artifact.name !== '' ? 'Update' : 'Create'} Artifact`}
            render={() => (
              <div className="row">
                <div className="col-sm-12">
                  <div>
                    <label htmlFor="name">Name</label>
                    <FormikFormField
                      name="name"
                      input={(inputProps) => <TextInput {...inputProps} />}
                      required={true}
                    />
                  </div>
                  <div style={{ marginTop: '10px' }}>
                    <label htmlFor="contents">Contents</label>
                    <FormikFormField
                      name="contents"
                      input={(inputProps) => <TextAreaInput {...inputProps} />}
                      required={true}
                    />
                  </div>
                </div>
              </div>
            )}
          />
        </div>
      </div>
    </div>
  );
};
