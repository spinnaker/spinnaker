import * as React from 'react';
import { Option } from 'react-select';

import { StageArtifactSelectorDelegate } from 'core/artifact';
import { IArtifact, IExpectedArtifact, IPipeline } from 'core/domain';
import { MapEditor } from 'core/forms';
import { IFormikStageConfigInjectedProps, StageConfigField } from 'core/pipeline';
import { CheckboxInput, ReactSelectInput, TextInput } from 'core/presentation';

interface IBakeManifestStageFormProps {
  updatePipeline: (pipeline: IPipeline) => void;
}

export class BakeManifestStageForm extends React.Component<
  IBakeManifestStageFormProps & IFormikStageConfigInjectedProps
> {
  public readonly templateRenderers = ['HELM2'];

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
  };

  private onTemplateArtifactSelected = (id: string, index: number) => {
    this.props.formik.setFieldValue(`inputArtifacts[${index}].id`, id);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].artifact`, null);
  };

  private onTemplateArtifactAccountSelected = (account: string, index: number) => {
    this.props.formik.setFieldValue(`inputArtifacts[${index}].account`, account);
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

  private overrideChanged = (overrides: any) => {
    this.props.formik.setFieldValue('overrides', overrides);
  };

  public render() {
    const stage = this.props.formik.values;
    return (
      <div className="form-horizontal clearfix">
        <div className="container-fluid form-horizontal">
          <h4>Template Renderer</h4>
          <StageConfigField
            fieldColumns={3}
            label={'Render Engine'}
            helpKey={'pipeline.config.bake.manifest.templateRenderer'}
          >
            <ReactSelectInput
              clearable={false}
              onChange={(o: Option<string>) => {
                this.props.formik.setFieldValue('templateRenderer', o.value);
              }}
              value={stage.templateRenderer}
              stringOptions={this.templateRenderers}
            />
          </StageConfigField>
          <StageConfigField fieldColumns={3} label={'Name'}>
            <TextInput
              onChange={(e: React.ChangeEvent<any>) => {
                this.props.formik.setFieldValue('outputName', e.target.value);
                this.outputNameChange(e.target.value);
              }}
              value={stage.outputName}
            />
          </StageConfigField>
          <StageConfigField fieldColumns={3} label={'Namespace'}>
            <TextInput
              onChange={(e: React.ChangeEvent<any>) => {
                this.props.formik.setFieldValue('namespace', e.target.value);
              }}
              value={stage.namespace}
            />
          </StageConfigField>
          <h4>Template Artifact</h4>
          <StageArtifactSelectorDelegate
            artifact={this.getInputArtifact(stage, 0).artifact}
            excludedArtifactTypePatterns={[]}
            expectedArtifactId={this.getInputArtifact(stage, 0).id}
            helpKey="pipeline.config.bake.manifest.expectedArtifact"
            label="Expected Artifact"
            onArtifactEdited={artifact => {
              this.onTemplateArtifactEdited(artifact, 0);
            }}
            onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
              this.onTemplateArtifactSelected(artifact.id, 0)
            }
            pipeline={this.props.pipeline}
            selectedArtifactAccount={this.getInputArtifact(stage, 0).account}
            selectedArtifactId={this.getInputArtifact(stage, 0).id}
            setArtifactAccount={account => this.onTemplateArtifactAccountSelected(account, 0)}
            setArtifactId={id => this.onTemplateArtifactSelected(id, 0)}
            stage={stage}
            updatePipeline={this.props.updatePipeline}
          />
          <h4>Overrides</h4>
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
                        label="Expected Artifact"
                        onArtifactEdited={artifact => {
                          this.onTemplateArtifactEdited(artifact, index + 1);
                        }}
                        onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                          this.onTemplateArtifactSelected(artifact.id, index + 1)
                        }
                        pipeline={this.props.pipeline}
                        selectedArtifactAccount={this.getInputArtifact(stage, index + 1).account}
                        selectedArtifactId={this.getInputArtifact(stage, index + 1).id}
                        setArtifactAccount={account => this.onTemplateArtifactAccountSelected(account, index + 1)}
                        setArtifactId={id => this.onTemplateArtifactSelected(id, index + 1)}
                        stage={stage}
                        updatePipeline={this.props.updatePipeline}
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
              Add value artifact
            </button>
          </StageConfigField>
          <StageConfigField fieldColumns={6} label="Overrides">
            {stage.overrides && (
              <MapEditor
                addButtonLabel={'Add override'}
                model={stage.overrides}
                allowEmpty={true}
                onChange={(o: any) => this.overrideChanged(o)}
              />
            )}
          </StageConfigField>
          <StageConfigField
            fieldColumns={6}
            helpKey={'pipeline.config.bake.manifest.overrideExpressionEvaluation'}
            label="Expression Evaluation"
          >
            <CheckboxInput
              value={stage.evaluateOverrideExpressions}
              text={'Evaluate SpEL expressions in overrides at bake time'}
              onChange={() =>
                this.props.formik.setFieldValue('evaluateOverrideExpressions', !stage.evaluateOverrideExpressions)
              }
            />
          </StageConfigField>
        </div>
      </div>
    );
  }
}
