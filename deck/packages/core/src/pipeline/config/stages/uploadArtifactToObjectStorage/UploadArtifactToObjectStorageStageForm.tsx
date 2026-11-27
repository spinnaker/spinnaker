import { get } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';

import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import type { IAccountDetails, IRegion } from '../../../../account';
import { AccountService } from '../../../../account';
import { StageArtifactSelector } from '../../../../artifact';
import { StageConfigField } from '../common';
import type { IArtifact, IExpectedArtifact } from '../../../../domain';
import { HelpField } from '../../../../help';
import type { IFormInputProps } from '../../../../presentation';
import {
  CheckboxInput,
  FormikFormField,
  FormikSpelContextProvider,
  ReactSelectInput,
  TextInput,
  useData,
} from '../../../../presentation';

interface ICloudProviderOption {
  label: string;
  value: string;
  disabled?: boolean;
}

const CLOUD_PROVIDER_OPTIONS: ICloudProviderOption[] = [{ label: 'AWS S3', value: 'aws' }];

export function UploadArtifactToObjectStorageStageForm(props: IFormikStageConfigInjectedProps) {
  const stage = props.formik.values;

  // Fetch accounts for selected cloud provider
  const { result: accounts, status: accountsStatus } = useData<IAccountDetails[]>(
    () => AccountService.getAllAccountDetailsForProvider(stage.cloudProvider),
    [],
    [stage.cloudProvider],
  );

  // Fetch regions for selected account
  const { result: regions, status: regionsStatus } = useData<IRegion[]>(
    () => AccountService.getRegionsForAccount(stage.credentials),
    [],
    [stage.credentials],
  );

  // Clear dependent fields when cloud provider changes
  React.useEffect(() => {
    if (stage.cloudProvider) {
      const currentAccount = accounts.find((a) => a.name === stage.credentials);
      if (!currentAccount) {
        props.formik.setFieldValue('credentials', '');
        props.formik.setFieldValue('region', '');
      }
    }
  }, [stage.cloudProvider, stage.credentials, accounts, props.formik.setFieldValue]);

  // Clear region when account changes
  React.useEffect(() => {
    if (stage.credentials) {
      const currentRegion = regions.find((r) => r.name === stage.region);
      if (!currentRegion && regions.length > 0) {
        props.formik.setFieldValue('region', '');
      }
    }
  }, [stage.credentials, stage.region, regions, props.formik.setFieldValue]);

  const accountOptions: Array<Option<string>> = [
    { label: 'Select an account...', value: '', disabled: true },
    ...accounts.map((a) => ({ label: a.name, value: a.name })),
  ];

  const regionOptions: Array<Option<string>> = [
    { label: 'Select a region...', value: '', disabled: true },
    ...regions.map((r) => ({
      label: r.deprecated ? `${r.name} (deprecated)` : r.name,
      value: r.name,
    })),
  ];

  const setArtifactId = (expectedArtifactId: string): void => {
    props.formik.setFieldValue('expectedArtifactId', expectedArtifactId);
    props.formik.setFieldValue('artifact', null);
  };

  const setArtifact = (artifact: IArtifact): void => {
    props.formik.setFieldValue('artifact', artifact);
    props.formik.setFieldValue('expectedArtifactId', null);
  };

  return (
    <FormikSpelContextProvider value={true}>
      <div className="form-horizontal">
        <h4>Destination Configuration</h4>
        <FormikFormField
          name="cloudProvider"
          label="Cloud Provider"
          required={true}
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput {...inputProps} clearable={false} options={CLOUD_PROVIDER_OPTIONS} />
          )}
        />

        <FormikFormField
          name="credentials"
          label="Account"
          required={true}
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput
              {...inputProps}
              clearable={false}
              isLoading={accountsStatus === 'PENDING'}
              options={accountOptions}
              disabled={!stage.cloudProvider}
            />
          )}
        />

        <FormikFormField
          name="region"
          label="Region"
          required={true}
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput
              {...inputProps}
              clearable={false}
              isLoading={regionsStatus === 'PENDING'}
              options={regionOptions}
              disabled={!stage.credentials}
            />
          )}
        />

        <FormikFormField
          name="bucket"
          label="Bucket"
          required={true}
          help={<HelpField content="The name of the bucket to upload to. SpEL expressions are supported." />}
          input={(inputProps: IFormInputProps) => <TextInput {...inputProps} placeholder="my-bucket-name" />}
        />

        <FormikFormField
          name="objectKeyPrefix"
          label="Object Key Prefix"
          required={true}
          help={<HelpField content="The key prefix (path) for uploaded files. SpEL expressions are supported." />}
          input={(inputProps: IFormInputProps) => <TextInput {...inputProps} placeholder="releases/latest" />}
        />

        <FormikFormField
          name="extractArchive"
          label="Extract Archive"
          help={
            <HelpField content="Enable if the artifact is an archive that should be extracted before uploading its contents. Disable for single-file artifacts." />
          }
          input={(inputProps: IFormInputProps) => (
            <CheckboxInput
              {...inputProps}
              text="Artifact is an archive (ZIP, TAR, TAR.GZ) — extract before upload"
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                inputProps.onChange(e);
                if (!e.target.checked) {
                  props.formik.setFieldValue('deleteStaleFiles', false);
                }
              }}
            />
          )}
        />

        {stage.extractArchive !== false && (
          <FormikFormField
            name="deleteStaleFiles"
            label="Delete Stale Files"
            help={
              <HelpField content="When enabled, files in the destination that are not present in the uploaded artifact will be deleted." />
            }
            input={(inputProps: IFormInputProps) => (
              <CheckboxInput {...inputProps} text="Delete stale files from destination" />
            )}
          />
        )}

        <FormikFormField
          name="cacheControl"
          label="Cache-Control"
          help={
            <HelpField content="Optional Cache-Control header for uploaded objects (e.g., 'max-age=31536000, public'). Applied to all files. SpEL expressions are supported." />
          }
          input={(inputProps: IFormInputProps) => <TextInput {...inputProps} placeholder="max-age=31536000, public" />}
        />

        {stage.extractArchive === false && (
          <FormikFormField
            name="contentType"
            label="Content-Type"
            help={
              <HelpField content="Optional Content-Type override for the uploaded file. When set, overrides auto-detection (useful for extensionless files like 'current' or 'Dockerfile'). SpEL expressions are supported." />
            }
            input={(inputProps: IFormInputProps) => <TextInput {...inputProps} placeholder="text/plain" />}
          />
        )}

        <h4>Source Artifact</h4>
        <StageConfigField label="Artifact">
          <StageArtifactSelector
            artifact={get(stage, 'artifact')}
            expectedArtifactId={get(stage, 'expectedArtifactId')}
            onArtifactEdited={setArtifact}
            onExpectedArtifactSelected={(artifact: IExpectedArtifact) => setArtifactId(artifact.id)}
            pipeline={props.pipeline}
            stage={stage}
          />
        </StageConfigField>
      </div>
    </FormikSpelContextProvider>
  );
}
