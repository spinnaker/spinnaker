import React from 'react';
import { get } from 'lodash';

import {
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  FormikFormField,
  IArtifact,
  IExpectedArtifact,
  IFormInputProps,
  IFormikStageConfigInjectedProps,
  IgorService,
  IPipeline,
  MapEditorInput,
  ReactSelectInput,
  StageArtifactSelector,
  TextInput,
  useData,
  YamlEditor,
} from 'core';
import { CheckboxInput } from 'core/presentation';

interface IAwsCodeBuildStageFormProps {
  updatePipeline: (pipeline: IPipeline) => void;
}

const EXCLUDED_ARTIFACT_TYPES: RegExp[] = excludeAllTypesExcept(
  ArtifactTypePatterns.S3_OBJECT,
  ArtifactTypePatterns.GIT_REPO,
);

const SOURCE_TYPES: string[] = ['BITBUCKET', 'CODECOMMIT', 'GITHUB', 'GITHUB_ENTERPRISE', 'S3'];

export function AwsCodeBuildStageForm(props: IAwsCodeBuildStageFormProps & IFormikStageConfigInjectedProps) {
  const stage = props.formik.values;

  const { result: fetchAccountsResult, status: fetchAccountsStatus } = useData(
    () => IgorService.getCodeBuildAccounts(),
    [],
    [],
  );

  const onYamlChange = (buildspec: string, _: any): void => {
    props.formik.setFieldValue('buildspec', buildspec);
  };

  const setArtifactId = (artifactId: string): void => {
    props.formik.setFieldValue('source.artifactId', artifactId);
    props.formik.setFieldValue('source.artifact', null);
  };

  const setArtifact = (artifact: IArtifact): void => {
    props.formik.setFieldValue('source.artifact', artifact);
    props.formik.setFieldValue('source.artifactId', null);
  };

  return (
    <div className="form-horizontal">
      <h4>Basic Settings</h4>
      <FormikFormField
        fastField={false}
        label="Account"
        name="account"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            isLoading={fetchAccountsStatus === 'PENDING'}
            stringOptions={fetchAccountsResult}
          />
        )}
      />
      {/* TODO: Select project from a drop-down list. Behind the scene, gate calls igor to fetch projects list */}
      <FormikFormField
        fastField={false}
        label="Project Name"
        name="projectName"
        input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
      />
      <h4>Source Configuration</h4>
      <FormikFormField
        fastField={false}
        label="Source"
        name="sourceOverride"
        input={(inputProps: IFormInputProps) => (
          <CheckboxInput {...inputProps} text="Override source to Spinnaker artifact" />
        )}
      />
      {stage.sourceOverride === true && (
        <FormikFormField
          fastField={false}
          label="SourceType"
          name="source.sourceType"
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput {...inputProps} clearable={true} stringOptions={SOURCE_TYPES} />
          )}
        />
      )}
      {stage.sourceOverride === true && (
        <FormikFormField
          fastField={false}
          label="Source Artifact Override"
          name="source"
          input={(inputProps: IFormInputProps) => (
            <StageArtifactSelector
              {...inputProps}
              artifact={get(stage, 'source.artifact')}
              excludedArtifactTypePatterns={EXCLUDED_ARTIFACT_TYPES}
              expectedArtifactId={get(stage, 'source.artifactId')}
              onArtifactEdited={setArtifact}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => setArtifactId(artifact.id)}
              pipeline={props.pipeline}
              stage={stage}
            />
          )}
        />
      )}
      <FormikFormField
        fastField={false}
        label="Source Version"
        name="sourceVersion"
        input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
      />
      <FormikFormField
        fastField={false}
        label="Buildspec"
        name="buildspec"
        input={(inputProps: IFormInputProps) => (
          <YamlEditor {...inputProps} value={get(stage, 'buildspec')} onChange={onYamlChange} />
        )}
      />
      <h4>Environment Configuration</h4>
      <FormikFormField
        fastField={false}
        label="Image"
        name="image"
        input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
      />
      <h4>Advanced Configuration</h4>
      <FormikFormField
        fastField={false}
        label="Environment Variables"
        name="environmentVariables"
        input={(inputProps: IFormInputProps) => (
          <MapEditorInput {...inputProps} addButtonLabel="Add environment variable" />
        )}
      />
    </div>
  );
}
