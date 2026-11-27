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
      destinationUri: 's3://my-bucket/releases/latest/',
      fileCount: 47,
      totalSizeBytes: 2516582,
      ...resultOverrides,
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

    expect(container).toHaveTextContent('Files');
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

  it('should have the correct title', () => {
    expect(UploadArtifactToObjectStorageExecutionDetails.title).toBe('uploadArtifactToObjectStorageStatus');
  });
});
