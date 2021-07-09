import React from 'react';

import {
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  StageArtifactSelectorDelegate,
  StageConfigField,
  TextInput,
} from '@spinnaker/core';

export class BakeCloudFoundryManifestConfigForm extends React.Component<IFormikStageConfigInjectedProps> {
  private static readonly excludedArtifactTypes = excludeAllTypesExcept(
    ArtifactTypePatterns.BITBUCKET_FILE,
    ArtifactTypePatterns.CUSTOM_OBJECT,
    ArtifactTypePatterns.EMBEDDED_BASE64,
    ArtifactTypePatterns.GCS_OBJECT,
    ArtifactTypePatterns.GITHUB_FILE,
    ArtifactTypePatterns.GITLAB_FILE,
    ArtifactTypePatterns.S3_OBJECT,
    ArtifactTypePatterns.HTTP_FILE,
  );

  public componentDidMount() {
    const stage = this.props.formik.values;
    if (stage.inputArtifacts && stage.inputArtifacts.length === 0) {
      this.props.formik.setFieldValue('inputArtifacts', [
        {
          account: '',
          id: '',
        },
      ]);
    }
  }

  private onTemplateArtifactEdited = (artifact: IArtifact, index: number) => {
    this.props.formik.setFieldValue(`inputArtifacts[${index}].id`, null);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].artifact`, artifact);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].account`, artifact.artifactAccount);
  };

  private onTemplateArtifactSelected = (id: string, index: number) => {
    this.props.formik.setFieldValue(`inputArtifacts[${index}].id`, id);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].artifact`, null);
  };

  private addInputArtifact = () => {
    const stage = this.props.formik.values;
    const newInputArtifacts = [
      ...stage.inputArtifacts,
      {
        account: '',
        id: '',
      },
    ];

    this.props.formik.setFieldValue('inputArtifacts', newInputArtifacts);
  };

  private removeInputArtifact = (index: number) => {
    const stage = this.props.formik.values;
    const newInputArtifacts = [...stage.inputArtifacts];
    newInputArtifacts.splice(index, 1);
    this.props.formik.setFieldValue('inputArtifacts', newInputArtifacts);
  };

  private getInputArtifact = (stage: any, index: number) => {
    if (!stage.inputArtifacts || stage.inputArtifacts.length === 0) {
      return {
        account: '',
        id: '',
      };
    } else {
      return stage.inputArtifacts[index];
    }
  };

  private outputNameChange = (outputName: string) => {
    const stage = this.props.formik.values;
    const expectedArtifacts = stage.expectedArtifacts;
    if (
      expectedArtifacts &&
      expectedArtifacts.length === 1 &&
      expectedArtifacts[0].matchArtifact &&
      expectedArtifacts[0].matchArtifact.type === 'embedded/base64'
    ) {
      this.props.formik.setFieldValue('expectedArtifacts', [
        {
          ...expectedArtifacts[0],
          matchArtifact: {
            ...expectedArtifacts[0].matchArtifact,
            name: outputName,
          },
        },
      ]);
    }
  };

  public render() {
    const stage = this.props.formik.values;

    return (
      <>
        <h4>Manifest Options</h4>
        <StageConfigField fieldColumns={3} label={'Name'} helpKey={'pipeline.config.bake.cf.manifest.name'}>
          <TextInput
            onChange={(e: React.ChangeEvent<any>) => {
              this.props.formik.setFieldValue('outputName', e.target.value);
              this.outputNameChange(e.target.value);
            }}
            value={stage.outputName}
          />
        </StageConfigField>
        <h4>Manifest Template</h4>
        <StageArtifactSelectorDelegate
          artifact={this.getInputArtifact(stage, 0).artifact}
          excludedArtifactTypePatterns={BakeCloudFoundryManifestConfigForm.excludedArtifactTypes}
          expectedArtifactId={this.getInputArtifact(stage, 0).id}
          label="Template Artifact"
          onArtifactEdited={(artifact) => {
            this.onTemplateArtifactEdited(artifact, 0);
          }}
          helpKey={'pipeline.config.bake.cf.manifest.templateArtifact'}
          onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onTemplateArtifactSelected(artifact.id, 0)}
          pipeline={this.props.pipeline}
          stage={stage}
        />
        <h4>Manifest Variables</h4>
        {stage.inputArtifacts && stage.inputArtifacts.length > 1 && (
          <div className="row form-group">
            {stage.inputArtifacts.slice(1).map((a: any, index: number) => {
              return (
                <div key={index}>
                  <div className="col-md-offset-1 col-md-9">
                    <StageArtifactSelectorDelegate
                      artifact={a.artifact}
                      excludedArtifactTypePatterns={[]}
                      expectedArtifactId={a.id}
                      label="Variables Artifact"
                      onArtifactEdited={(artifact) => {
                        this.onTemplateArtifactEdited(artifact, index + 1);
                      }}
                      onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                        this.onTemplateArtifactSelected(artifact.id, index + 1)
                      }
                      helpKey={'pipeline.config.bake.cf.manifest.varsArtifact'}
                      pipeline={this.props.pipeline}
                      stage={stage}
                    />
                  </div>
                  <div className="col-md-1">
                    <div className="form-control-static">
                      <button onClick={() => this.removeInputArtifact(index + 1)}>
                        <span className="glyphicon glyphicon-trash" />
                        <span className="sr-only">Remove field</span>
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        <StageConfigField fieldColumns={8} label={''}>
          <button className="btn btn-block btn-sm add-new" onClick={() => this.addInputArtifact()}>
            <span className="glyphicon glyphicon-plus-sign" />
            Add variables artifact
          </button>
        </StageConfigField>
      </>
    );
  }
}
