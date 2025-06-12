import React from 'react';

import type { IFormikStageConfigInjectedProps } from '../../FormikStageConfig';
import { ManifestRenderers } from '../ManifestRenderers';
import { AccountService } from '../../../../../account';
import {
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  ExpectedArtifactService,
  StageArtifactSelectorDelegate,
} from '../../../../../artifact';
import { StageConfigField } from '../../common/stageConfigField/StageConfigField';
import { SETTINGS } from '../../../../../config';
import type { IArtifact, IExpectedArtifact } from '../../../../../domain';
import { MapEditor } from '../../../../../forms';
import { CheckboxInput, TextInput } from '../../../../../presentation';

export interface IBakeHelmConfigFormState {
  gitRepoArtifactAccountNames: string[];
  helmImageArtifactAccountNames: string[];
}

export class BakeHelmConfigForm extends React.Component<IFormikStageConfigInjectedProps, IBakeHelmConfigFormState> {
  constructor(props: IFormikStageConfigInjectedProps) {
    super(props);
    this.state = { gitRepoArtifactAccountNames: [], helmImageArtifactAccountNames: [] };
  }

  private static readonly excludedArtifactTypes = excludeAllTypesExcept(
    ArtifactTypePatterns.BITBUCKET_FILE,
    ArtifactTypePatterns.CUSTOM_OBJECT,
    ArtifactTypePatterns.EMBEDDED_BASE64,
    ArtifactTypePatterns.GCS_OBJECT,
    ArtifactTypePatterns.GIT_REPO,
    ArtifactTypePatterns.GITHUB_FILE,
    ArtifactTypePatterns.GITLAB_FILE,
    ArtifactTypePatterns.S3_OBJECT,
    ArtifactTypePatterns.HELM_CHART,
    ArtifactTypePatterns.HTTP_FILE,
    ArtifactTypePatterns.ORACLE_OBJECT,
    ArtifactTypePatterns.HELM_IMAGE,
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

    // If the Expected Artifact id is provided but the account is not, then attempt to find the artifact from
    // upstream stages and set the account value.
    // This is needed because helm chart file path field will need to be rendered if the artifact has a git repo account type
    const expectedArtifact = this.getInputArtifact(stage, 0);
    if (expectedArtifact.id && !expectedArtifact.account) {
      const availableArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
        stage,
        this.props.pipeline,
      );
      const expectedMatchedArtifact = availableArtifacts.find((a) => a.id === expectedArtifact.id);
      if (expectedMatchedArtifact && expectedMatchedArtifact.matchArtifact) {
        this.props.formik.setFieldValue(
          `inputArtifacts[0].account`,
          expectedMatchedArtifact.matchArtifact.artifactAccount,
        );
      }
    }

    AccountService.getArtifactAccounts().then((artifactAccounts) => {
      this.setState({
        gitRepoArtifactAccountNames: artifactAccounts
          .filter((account) => account.types.some((type) => ArtifactTypePatterns.GIT_REPO.test(type)))
          .map((account) => account.name),
        helmImageArtifactAccountNames: artifactAccounts
          .filter((account) => account.types.some((type) => ArtifactTypePatterns.HELM_IMAGE.test(type)))
          .map((account) => account.name),
      });
    });
  }

  private onTemplateArtifactEdited = (artifact: IArtifact, index: number) => {
    this.props.formik.setFieldValue(`inputArtifacts[${index}].id`, null);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].artifact`, artifact);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].account`, artifact.artifactAccount);
  };

  private onTemplateArtifactSelected = (artifact: IExpectedArtifact, index: number) => {
    this.props.formik.setFieldValue(`inputArtifacts[${index}].id`, artifact.id);
    this.props.formik.setFieldValue(`inputArtifacts[${index}].artifact`, null);
    // Set the account to matchArtifact.artifactAccount if it exists.
    // This account value will be used to determine if the Helm Chart File Path should be displayed.
    if (artifact.matchArtifact) {
      this.props.formik.setFieldValue(`inputArtifacts[${index}].account`, artifact.matchArtifact.artifactAccount);
    } else {
      this.props.formik.setFieldValue(`inputArtifacts[${index}].account`, null);
    }
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
    const enableApiVersions = SETTINGS.feature.helmApiVersions;
    return (
      <>
        <h4>Helm Options</h4>
        {enableApiVersions && ( // Only render if enableApiVersions is true
          <>
            <StageConfigField fieldColumns={3} label={'ApiVersions'}>
              <TextInput
                onChange={(e: React.ChangeEvent<any>) => {
                  this.props.formik.setFieldValue('apiVersions', e.target.value);
                }}
                value={stage.apiVersions}
              />
            </StageConfigField>
            <StageConfigField fieldColumns={3} label={'KubeVersion'}>
              <TextInput
                onChange={(e: React.ChangeEvent<any>) => {
                  this.props.formik.setFieldValue('kubeVersion', e.target.value);
                }}
                value={stage.kubeVersion}
              />
            </StageConfigField>
          </>
        )}
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
          excludedArtifactTypePatterns={BakeHelmConfigForm.excludedArtifactTypes}
          expectedArtifactId={this.getInputArtifact(stage, 0).id}
          helpKey="pipeline.config.bake.manifest.expectedArtifact"
          label="Expected Artifact"
          onArtifactEdited={(artifact) => {
            this.onTemplateArtifactEdited(artifact, 0);
          }}
          onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onTemplateArtifactSelected(artifact, 0)}
          pipeline={this.props.pipeline}
          stage={stage}
        />
        {(this.state.gitRepoArtifactAccountNames.includes(this.getInputArtifact(stage, 0).account) ||
          this.state.helmImageArtifactAccountNames.includes(this.getInputArtifact(stage, 0).account)) && (
          <StageConfigField label="Helm Chart File Path" helpKey="pipeline.config.bake.manifest.helm.chartFilePath">
            <TextInput
              onChange={(e: React.ChangeEvent<any>) => {
                this.props.formik.setFieldValue('helmChartFilePath', e.target.value);
              }}
              value={stage.helmChartFilePath}
            />
          </StageConfigField>
        )}
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
                      onArtifactEdited={(artifact) => {
                        this.onTemplateArtifactEdited(artifact, index + 1);
                      }}
                      onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                        this.onTemplateArtifactSelected(artifact, index + 1)
                      }
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
          helpKey={'pipeline.config.bake.manifest.helm.rawOverrides'}
          label="Raw Overrides"
        >
          <CheckboxInput
            value={stage.rawOverrides}
            text={''}
            onChange={() => this.props.formik.setFieldValue('rawOverrides', !stage.rawOverrides)}
          />
        </StageConfigField>
        {stage.templateRenderer === ManifestRenderers.HELM3 && (
          <StageConfigField
            fieldColumns={6}
            helpKey={'pipeline.config.bake.manifest.helm.includeCRDs'}
            label="Include CRDs"
          >
            <CheckboxInput
              value={stage.includeCRDs}
              text={''}
              onChange={() => this.props.formik.setFieldValue('includeCRDs', !stage.includeCRDs)}
            />
          </StageConfigField>
        )}
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
      </>
    );
  }
}
