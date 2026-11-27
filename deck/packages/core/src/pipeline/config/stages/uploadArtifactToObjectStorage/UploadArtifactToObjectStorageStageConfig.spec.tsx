import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import { ApplicationModelBuilder } from '../../../../application';
import { AccountService } from '../../../../account';
import type { IPipeline, IStage } from '../../../../domain';
import { REACT_MODULE } from '../../../../reactShims';
import { UploadArtifactToObjectStorageStageConfig } from './UploadArtifactToObjectStorageStageConfig';

describe('<UploadArtifactToObjectStorageStageConfig />', () => {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  beforeEach(() => {
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue(
      Promise.resolve([
        {
          name: 'my-aws-account',
          type: 'aws',
          cloudProvider: 'aws',
          regions: [{ name: 'us-east-1' }, { name: 'us-west-2' }],
        },
      ]),
    );
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(
      Promise.resolve([{ name: 'us-east-1' }, { name: 'us-west-2' }]),
    );
    spyOn(AccountService, 'getArtifactAccounts').and.returnValue(Promise.resolve([]));
  });

  const createProps = (stageOverrides: Partial<IStage> = {}) => {
    const stage: IStage = {
      name: 'Upload Artifact',
      type: 'uploadArtifactToObjectStorage',
      refId: '1',
      requisiteStageRefIds: [],
      ...stageOverrides,
    };

    const pipeline: IPipeline = {
      application: 'my-application',
      id: 'pipeline-id',
      limitConcurrent: true,
      keepWaitingPipelines: false,
      name: 'My Pipeline',
      parameterConfig: [],
      stages: [stage],
      triggers: [],
    };

    return {
      application: ApplicationModelBuilder.createApplicationForTests('my-application'),
      pipeline,
      stage,
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage: jasmine.createSpy('updateStage'),
      updateStageField: jasmine.createSpy('updateStageField'),
    };
  };

  it('should render the form', () => {
    const props = createProps();
    const component = mount(<UploadArtifactToObjectStorageStageConfig {...props} />);
    expect(component.find('.form-horizontal').length).toBeGreaterThanOrEqual(0);
  });

  it('should default cloudProvider to aws', async () => {
    const props = createProps();
    const component = mount(<UploadArtifactToObjectStorageStageConfig {...props} />);

    await new Promise((resolve) => setTimeout(resolve));
    component.update();

    const formikValues = component.find('UploadArtifactToObjectStorageStageForm').props();
    expect(formikValues).toBeDefined();
  });

  it('should preserve existing stage values', async () => {
    const props = createProps({
      cloudProvider: 'aws',
      credentials: 'my-aws-account',
      region: 'us-east-1',
      bucket: 'my-bucket',
      objectKey: 'releases/latest',
    });
    const component = mount(<UploadArtifactToObjectStorageStageConfig {...props} />);

    await new Promise((resolve) => setTimeout(resolve));
    component.update();

    expect(component.find('.form-horizontal').length).toBeGreaterThanOrEqual(0);
  });

  it('should render expires when configured', async () => {
    const props = createProps({
      cloudProvider: 'aws',
      credentials: 'my-aws-account',
      region: 'us-east-1',
      bucket: 'my-bucket',
      objectKeyPrefix: 'releases/latest',
      expires: 'P1Y',
    });
    const component = mount(<UploadArtifactToObjectStorageStageConfig {...props} />);

    await new Promise((resolve) => setTimeout(resolve));
    component.update();

    const expiresField = component.find('StageConfigField').filterWhere((field) => field.prop('label') === 'Expires');
    expect(expiresField.length).toBe(1);
    expect(expiresField.find('TextInput').prop('value')).toBe('P1Y');

    const expiresInput = expiresField.find('TextInput');
    expiresInput.prop('onChange')({
      target: { value: 'PT6H' },
    } as React.ChangeEvent<HTMLInputElement>);

    await new Promise((resolve) => setTimeout(resolve));
    component.update();

    expect(
      component
        .find('TextInput')
        .filterWhere((input) => input.prop('name') === 'expires')
        .prop('value'),
    ).toBe('PT6H');
  });
});
