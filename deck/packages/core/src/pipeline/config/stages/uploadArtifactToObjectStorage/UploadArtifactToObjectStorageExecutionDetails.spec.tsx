import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import type { IExecutionStage } from '../../../../domain';
import { REACT_MODULE } from '../../../../reactShims';
import { UploadArtifactToObjectStorageExecutionDetails } from './UploadArtifactToObjectStorageExecutionDetails';

describe('<UploadArtifactToObjectStorageExecutionDetails />', () => {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  const createStage = (contextOverrides: Record<string, any> = {}): IExecutionStage => ({
    id: 'stage-id',
    name: 'Upload Artifact',
    type: 'uploadArtifactToObjectStorage',
    refId: '1',
    requisiteStageRefIds: [],
    status: 'SUCCEEDED',
    startTime: Date.now() - 10000,
    endTime: Date.now(),
    tasks: [],
    context: {
      destinationUri: 's3://my-bucket/releases/latest/',
      fileCount: 47,
      totalSizeBytes: 2516582,
      ...contextOverrides,
    },
  });

  const createProps = (stage: IExecutionStage) => ({
    name: UploadArtifactToObjectStorageExecutionDetails.title,
    current: UploadArtifactToObjectStorageExecutionDetails.title,
    stage,
    application: {} as any,
    execution: {} as any,
    provider: 'aws',
    config: {} as any,
    detailsSections: [],
  });

  it('should display the destination URI', () => {
    const stage = createStage();
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.find('code').text()).toContain('s3://my-bucket/releases/latest/');
  });

  it('should display the file count', () => {
    const stage = createStage();
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('47');
  });

  it('should format and display the total size in MB', () => {
    const stage = createStage({ totalSizeBytes: 2516582 });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('2.4 MB');
  });

  it('should format bytes correctly', () => {
    const stage = createStage({ totalSizeBytes: 1024 });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('1 KB');
  });

  it('should handle missing data gracefully', () => {
    const stage = createStage({
      destinationUri: undefined,
      fileCount: undefined,
      totalSizeBytes: undefined,
    });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.exists()).toBe(true);
  });

  it('should display failure message when stage fails', () => {
    const stage = {
      ...createStage(),
      status: 'TERMINAL',
      failureMessage: 'Upload failed: Access Denied',
    };
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.find('StageFailureMessage').exists()).toBe(true);
    expect(component.find('StageFailureMessage').prop('message')).toBe('Upload failed: Access Denied');
  });

  it('should have the correct title', () => {
    expect(UploadArtifactToObjectStorageExecutionDetails.title).toBe('uploadArtifactToObjectStorageStatus');
  });
});
