import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import type { IExecutionStage } from '../../../../domain';
import { REACT_MODULE } from '../../../../reactShims';
import { UploadArtifactToObjectStorageExecutionDetails } from './UploadArtifactToObjectStorageExecutionDetails';

describe('<UploadArtifactToObjectStorageExecutionDetails />', () => {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  const createStage = (resultOverrides: Record<string, any> = {}): IExecutionStage => ({
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
      'kato.tasks': [
        {
          id: 'kato-task-id',
          resultObjects: [
            {
              destinationUri: 's3://my-bucket/releases/latest/',
              fileCount: 47,
              totalSizeBytes: 2516582,
              ...resultOverrides,
            },
          ],
        },
      ],
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

  it('should display the total file count', () => {
    const stage = createStage();
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Total Files');
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
    const stage: IExecutionStage = {
      id: 'stage-id',
      name: 'Upload Artifact',
      type: 'uploadArtifactToObjectStorage',
      refId: '1',
      requisiteStageRefIds: [],
      status: 'RUNNING',
      startTime: Date.now() - 10000,
      endTime: Date.now(),
      tasks: [],
      context: {},
    };
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

  it('should display delete stale files enabled and deleted count when both present', () => {
    const stage = createStage({ deleteStaleFiles: true, deletedCount: 5 });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Delete Stale');
    expect(component.text()).toContain('Yes');
    expect(component.text()).toContain('Deleted');
    expect(component.text()).toContain('5');
  });

  it('should display delete stale files disabled when deleteStaleFiles is false', () => {
    const stage = createStage({ deleteStaleFiles: false });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Delete Stale');
    expect(component.text()).toContain('No');
  });

  it('should handle missing deleteStaleFiles and deletedCount gracefully', () => {
    const stage: IExecutionStage = {
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
        'kato.tasks': [
          {
            id: 'kato-task-id',
            resultObjects: [
              {
                destinationUri: 's3://my-bucket/releases/latest/',
                fileCount: 10,
              },
            ],
          },
        ],
      },
    };
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.exists()).toBe(true);
    expect(component.text()).not.toContain('Delete Stale');
    expect(component.text()).not.toContain('Deleted');
  });

  it('should handle null deleteStaleFiles gracefully', () => {
    const stage = createStage({
      deleteStaleFiles: null,
      deletedCount: null,
    });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.exists()).toBe(true);
    expect(component.text()).not.toContain('Delete Stale');
    expect(component.text()).not.toContain('Deleted');
  });

  it('should display uploaded and skipped counts when sync data is present', () => {
    const stage = createStage({ uploadCount: 12, skippedCount: 35, fileCount: 47 });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Uploaded');
    expect(component.text()).toContain('12');
    expect(component.text()).toContain('Skipped');
    expect(component.text()).toContain('35');
    expect(component.text()).toContain('Total Files');
    expect(component.text()).toContain('47');
  });

  it('should display uploaded count without skipped when skippedCount is absent', () => {
    const stage = createStage({ uploadCount: 47, fileCount: 47 });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Uploaded');
    expect(component.text()).toContain('47');
    expect(component.text()).not.toContain('Skipped');
  });

  it('should display zero uploaded and all skipped when nothing changed', () => {
    const stage = createStage({ uploadCount: 0, skippedCount: 100, fileCount: 100 });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    const text = component.text();
    expect(text).toContain('Uploaded');
    expect(text).toContain('Skipped');

    const dts = component.find('dt');
    const dds = component.find('dd');
    const uploadedIndex = dts.map((dt) => dt.text()).indexOf('Uploaded');
    expect(dds.at(uploadedIndex).text()).toBe('0');
  });

  it('should display force upload enabled when forceUpload is true', () => {
    const stage = createStage({ forceUpload: true });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Force Upload');
    expect(component.text()).toContain('Yes');
  });

  it('should display force upload disabled when forceUpload is false', () => {
    const stage = createStage({ forceUpload: false });
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).toContain('Force Upload');
    expect(component.text()).toContain('No');
  });

  it('should not display force upload when forceUpload is not present', () => {
    const stage = createStage();
    const props = createProps(stage);
    const component = mount(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(component.text()).not.toContain('Force Upload');
  });

  it('should have the correct title', () => {
    expect(UploadArtifactToObjectStorageExecutionDetails.title).toBe('uploadArtifactToObjectStorageConfig');
  });
});
