import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { ApplicationModelBuilder } from '../../../../application';
import { AccountService } from '../../../../account';
import type { IPipeline, IStage } from '../../../../domain';
import { UploadArtifactToObjectStorageStageConfig } from './UploadArtifactToObjectStorageStageConfig';

describe('<UploadArtifactToObjectStorageStageConfig />', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockReturnValue(
      Promise.resolve([
        {
          name: 'my-aws-account',
          type: 'aws',
          cloudProvider: 'aws',
          regions: [{ name: 'us-east-1' }, { name: 'us-west-2' }],
        },
      ]),
    );
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(
      Promise.resolve([{ name: 'us-east-1' }, { name: 'us-west-2' }]),
    );
    vi.spyOn(AccountService, 'getArtifactAccounts').mockReturnValue(Promise.resolve([]));
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
      stageFieldUpdated: vi.fn(),
      updateStage: vi.fn(),
      updateStageField: vi.fn(),
    };
  };

  it('should render the form', () => {
    const props = createProps();
    const { container } = render(<UploadArtifactToObjectStorageStageConfig {...props} />);
    expect(container.querySelector('.form-horizontal')).toBeInTheDocument();
  });

  it('should default cloudProvider to aws', async () => {
    const props = createProps();
    render(<UploadArtifactToObjectStorageStageConfig {...props} />);
    expect(await screen.findByText('AWS S3')).toBeVisible();
  });

  it('should preserve existing stage values', async () => {
    const props = createProps({
      cloudProvider: 'aws',
      credentials: 'my-aws-account',
      region: 'us-east-1',
      bucket: 'my-bucket',
      objectKeyPrefix: 'releases/latest',
    });
    render(<UploadArtifactToObjectStorageStageConfig {...props} />);
    expect(screen.getByDisplayValue('my-bucket')).toBeVisible();
    expect(screen.getByDisplayValue('releases/latest')).toBeVisible();
    expect(await screen.findByText('my-aws-account')).toBeVisible();
    expect(await screen.findByText('us-east-1')).toBeVisible();
  });

  it('should render expires when configured', () => {
    const props = createProps({
      cloudProvider: 'aws',
      credentials: 'my-aws-account',
      region: 'us-east-1',
      expires: 'P1Y',
    });
    render(<UploadArtifactToObjectStorageStageConfig {...props} />);
    const expiresInput = screen.getByDisplayValue('P1Y');
    fireEvent.change(expiresInput, { target: { value: 'PT6H' } });
    expect(expiresInput).toHaveValue('PT6H');
  });
});
