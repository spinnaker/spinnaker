import { get } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';

import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import type { IAccountDetails, IRegion } from '../../../../account';
import { AccountService } from '../../../../account';
import { StageArtifactSelector } from '../../../../artifact';
import { StageConfigField } from '../common';
import type { IArtifact, IExpectedArtifact } from '../../../../domain';
import { CheckboxInput, ReactSelectInput, TextInput, useData } from '../../../../presentation';

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
    if (stage.cloudProvider && accountsStatus === 'RESOLVED') {
      const currentAccount = accounts.find((a) => a.name === stage.credentials);
      if (!currentAccount) {
        props.formik.setFieldValue('credentials', '');
        props.formik.setFieldValue('region', '');
      }
    }
  }, [stage.cloudProvider, accountsStatus, accounts, props.formik.setFieldValue]);

  // Clear region when account changes
  React.useEffect(() => {
    if (stage.credentials && regionsStatus === 'RESOLVED') {
      const currentRegion = regions.find((r) => r.name === stage.region);
      if (!currentRegion && regions.length > 0) {
        props.formik.setFieldValue('region', '');
      }
    }
  }, [stage.credentials, regionsStatus, regions, props.formik.setFieldValue]);

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

  const onSelectChange = (fieldName: string) => (e: React.ChangeEvent<HTMLSelectElement>) => {
    props.formik.setFieldValue(fieldName, e.target.value);
  };

  const onTextChange = (fieldName: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    props.formik.setFieldValue(fieldName, e.target.value);
  };

  const onCheckboxChange = (fieldName: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    props.formik.setFieldValue(fieldName, e.target.checked);
  };

  return (
    <div className="form-horizontal">
      <h4>Destination Configuration</h4>
      <StageConfigField label="Cloud Provider" fieldColumns={6}>
        <ReactSelectInput
          clearable={false}
          options={CLOUD_PROVIDER_OPTIONS}
          value={stage.cloudProvider || ''}
          onChange={onSelectChange('cloudProvider')}
        />
      </StageConfigField>

      <StageConfigField label="Account" fieldColumns={6}>
        <ReactSelectInput
          clearable={false}
          isLoading={accountsStatus === 'PENDING'}
          options={accountOptions}
          value={stage.credentials || ''}
          onChange={onSelectChange('credentials')}
          disabled={!stage.cloudProvider}
        />
      </StageConfigField>

      <StageConfigField label="Region" fieldColumns={6}>
        <ReactSelectInput
          clearable={false}
          isLoading={regionsStatus === 'PENDING'}
          options={regionOptions}
          value={stage.region || ''}
          onChange={onSelectChange('region')}
          disabled={!stage.credentials}
        />
      </StageConfigField>

      <StageConfigField label="Bucket" fieldColumns={6} helpKey="pipeline.config.uploadArtifactToObjectStorage.bucket">
        <TextInput
          name="bucket"
          value={stage.bucket || ''}
          onChange={onTextChange('bucket')}
          placeholder="my-bucket-name"
        />
      </StageConfigField>

      <StageConfigField
        label="Object Key Prefix"
        fieldColumns={6}
        helpKey="pipeline.config.uploadArtifactToObjectStorage.objectKeyPrefix"
      >
        <TextInput
          name="objectKeyPrefix"
          value={stage.objectKeyPrefix || ''}
          onChange={onTextChange('objectKeyPrefix')}
          placeholder="releases/latest"
        />
      </StageConfigField>

      <StageConfigField label="Extract Archive" fieldColumns={6}>
        <CheckboxInput
          checked={stage.extractArchive !== false}
          text="Artifact is an archive (ZIP, TAR, TAR.GZ) — extract before upload"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            props.formik.setFieldValue('extractArchive', e.target.checked);
            if (!e.target.checked) {
              props.formik.setFieldValue('deleteStaleFiles', false);
            }
          }}
        />
      </StageConfigField>

      {stage.extractArchive !== false && (
        <StageConfigField
          label="Delete Stale"
          fieldColumns={6}
          helpKey="pipeline.config.uploadArtifactToObjectStorage.deleteStaleFiles"
        >
          <CheckboxInput
            checked={!!stage.deleteStaleFiles}
            text="Delete stale files from destination"
            onChange={onCheckboxChange('deleteStaleFiles')}
          />
        </StageConfigField>
      )}

      <StageConfigField
        label="Cache-Control"
        fieldColumns={6}
        helpKey="pipeline.config.uploadArtifactToObjectStorage.cacheControl"
      >
        <TextInput
          value={stage.cacheControl || ''}
          onChange={onTextChange('cacheControl')}
          placeholder="max-age=31536000, public"
        />
      </StageConfigField>

      {stage.extractArchive === false && (
        <StageConfigField
          label="Content-Type"
          fieldColumns={6}
          helpKey="pipeline.config.uploadArtifactToObjectStorage.contentType"
        >
          <TextInput value={stage.contentType || ''} onChange={onTextChange('contentType')} placeholder="text/plain" />
        </StageConfigField>
      )}

      <h4>Source Artifact</h4>
      <StageConfigField label="Artifact" fieldColumns={6}>
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
  );
}
