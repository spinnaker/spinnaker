import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { render, screen } from '@testing-library/react';
import React from 'react';

import type { IExecutionStage } from '../../../../domain';
import { UploadArtifactToObjectStorageExecutionDetails } from './UploadArtifactToObjectStorageExecutionDetails';

describe('<UploadArtifactToObjectStorageExecutionDetails />', () => {
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
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container.querySelector('code')).toHaveTextContent('s3://my-bucket/releases/latest/');
  });

  it('should display the total file count', () => {
    const stage = createStage();
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('Total Files');
    expect(container).toHaveTextContent('47');
  });

  it('should format and display the total size in MB', () => {
    const stage = createStage({ totalSizeBytes: 2516582 });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('2.4 MB');
  });

  it('should format bytes correctly', () => {
    const stage = createStage({ totalSizeBytes: 1024 });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('1 KB');
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
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);
    expect(container).not.toBeEmptyDOMElement();
  });

  it('should display failure message when stage fails', () => {
    const stage = {
      ...createStage(),
      status: 'TERMINAL',
      failureMessage: 'Upload failed: Access Denied',
    };
    const props = createProps(stage);
    render(
      <UIRouterContext.Provider value={new UIRouterReact()}>
        <UploadArtifactToObjectStorageExecutionDetails {...props} />
      </UIRouterContext.Provider>,
    );
    expect(screen.getByText('Upload failed: Access Denied')).toBeVisible();
  });

  it('should display delete stale files enabled and deleted count when both present', () => {
    const stage = createStage({ deleteStaleFiles: true, deletedCount: 5 });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('Delete Stale');
    expect(container).toHaveTextContent('Yes');
    expect(container).toHaveTextContent('Deleted');
    expect(container).toHaveTextContent('5');
  });

  it('should display delete stale files disabled when deleteStaleFiles is false', () => {
    const stage = createStage({ deleteStaleFiles: false });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('Delete Stale');
    expect(container).toHaveTextContent('No');
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
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).not.toBeEmptyDOMElement();
    expect(container).not.toHaveTextContent('Delete Stale');
    expect(container).not.toHaveTextContent('Deleted');
  });

  it('should handle null deleteStaleFiles gracefully', () => {
    const stage = createStage({
      deleteStaleFiles: null,
      deletedCount: null,
    });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).not.toBeEmptyDOMElement();
    expect(container).not.toHaveTextContent('Delete Stale');
    expect(container).not.toHaveTextContent('Deleted');
  });

  it('should display uploaded and skipped counts when sync data is present', () => {
    const stage = createStage({ uploadCount: 12, skippedCount: 35, fileCount: 47 });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('Uploaded');
    expect(container).toHaveTextContent('12');
    expect(container).toHaveTextContent('Skipped');
    expect(container).toHaveTextContent('35');
    expect(container).toHaveTextContent('Total Files');
    expect(container).toHaveTextContent('47');
  });

  it('should display uploaded count without skipped when skippedCount is absent', () => {
    const stage = createStage({ uploadCount: 47, fileCount: 47 });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);

    expect(container).toHaveTextContent('Uploaded');
    expect(container).toHaveTextContent('47');
    expect(container).not.toHaveTextContent('Skipped');
  });

  it('should display zero uploaded and all skipped when nothing changed', () => {
    const stage = createStage({ uploadCount: 0, skippedCount: 100, fileCount: 100 });
    const props = createProps(stage);
    const { container } = render(<UploadArtifactToObjectStorageExecutionDetails {...props} />);
    const uploadedLabel = Array.from(container.querySelectorAll('dt')).find((dt) => dt.textContent === 'Uploaded');
    expect(uploadedLabel.nextElementSibling).toHaveTextContent('0');
    expect(container).toHaveTextContent('Skipped');
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
