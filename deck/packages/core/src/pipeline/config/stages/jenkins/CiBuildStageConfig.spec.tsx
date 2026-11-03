import { render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { BuildServiceType, IgorService } from '../../../../ci/igor.service';
import { HelpContentsRegistry, HelpTextExpandedContext } from '../../../../help';
import { AddCiBuildParameterModal } from './AddCiBuildParameterModal';
import { CiBuildStageConfig } from './CiBuildStageConfig';

describe('<CiBuildStageConfig />', () => {
  beforeEach(() => {
    vi.spyOn(IgorService, 'listMasters').mockReturnValue(Promise.resolve([]));
    vi.spyOn(IgorService, 'listJobsForMaster').mockReturnValue(Promise.resolve([]));
    vi.spyOn(IgorService, 'getJobConfig').mockReturnValue(Promise.resolve({ parameterDefinitionList: [] } as any));
  });

  const flushPromises = async () => {
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
  };

  const createProps = (stageOverrides = {}) => {
    const stage: any = {
      name: 'Jenkins',
      refId: '1',
      requisiteStageRefIds: [],
      type: 'jenkins',
      ...stageOverrides,
    };

    return {
      application: {} as any,
      pipeline: { stages: [stage] } as any,
      stage,
      buildServiceLabel: 'Jenkins Master',
      buildServicePlaceholder: 'Select a master...',
      buildServiceType: BuildServiceType.Jenkins,
      markUnstableHelpKeyPrefix: 'pipeline.config.jenkins.markUnstableAsSuccessful',
      stageFieldUpdated: vi.fn(),
      updateStage: vi.fn(),
      updateStageField: vi.fn().mockImplementation((changes: any) => Object.assign(stage, changes)),
      waitForCompletionHelpKey: 'pipeline.config.jenkins.waitForCompletion',
    };
  };

  const expectFullJobListSearchBeforeLimit = async (buildServiceType: BuildServiceType) => {
    const user = setupUser();
    const jobs = Array.from({ length: 500 }, (_value, index) => `common-job-${index}`);
    jobs.push('target-job-after-limit');
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(Promise.resolve(jobs));
    const props = createProps({ master: 'master' });

    render(<CiBuildStageConfig {...props} buildServiceType={buildServiceType} />);
    const jobInput = (await screen.findByText('Start typing...')).closest('.Select').querySelector('input');
    await user.click(jobInput);
    expect(screen.getAllByRole('option')).toHaveLength(100);

    await user.type(jobInput, 'target-job-after-limit');
    expect(screen.getAllByRole('option')).toHaveLength(1);
    expect(screen.getByRole('option', { name: 'target-job-after-limit' })).toBeVisible();
  };

  it('sets legacy defaults without marking the stage dirty', () => {
    const props = createProps();

    render(<CiBuildStageConfig {...props} />);

    expect(props.stage.failPipeline).toBe(true);
    expect(props.stage.continuePipeline).toBe(false);
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('initializes missing job parameters without marking the stage dirty', async () => {
    const props = createProps({ master: 'master', job: 'job' });
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(Promise.resolve(['job']));

    render(<CiBuildStageConfig {...props} />);
    await waitFor(() => expect(props.stage.parameters).toEqual({}));

    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('clears saved jobs missing from Igor through updateStageField', async () => {
    const props = createProps({ master: 'master', job: 'missing-job' });
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(Promise.resolve(['current-job']));

    render(<CiBuildStageConfig {...props} />);
    await waitFor(() => expect(props.stage.job).toBe(''));

    expect(props.updateStageField).toHaveBeenCalledWith({ job: '' });
    expect(props.stageFieldUpdated).toHaveBeenCalled();
  });

  it('searches the full Jenkins job list before limiting results', async () => {
    await expectFullJobListSearchBeforeLimit(BuildServiceType.Jenkins);
  });

  it('searches the full Travis job list before limiting results', async () => {
    await expectFullJobListSearchBeforeLimit(BuildServiceType.Travis);
  });

  it('clears master refresh state when Igor rejects', async () => {
    let rejectMasters: (error: Error) => void;
    const mastersPromise = new Promise<string[]>((_resolve, reject) => (rejectMasters = reject));
    vi.mocked(IgorService.listMasters).mockReturnValue(mastersPromise);

    const { container } = render(<CiBuildStageConfig {...createProps()} />);
    expect(container.querySelector('[title="Refresh masters list"] .fa-spin')).toBeInTheDocument();

    rejectMasters(new Error('failed to load masters'));
    await waitFor(() =>
      expect(container.querySelector('[title="Refresh masters list"] .fa-spin')).not.toBeInTheDocument(),
    );
  });

  it('does not update after async job list responses resolve after unmount', async () => {
    let resolveJobs: (jobs: string[]) => void;
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(new Promise((resolve) => (resolveJobs = resolve)));
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    const view = render(<CiBuildStageConfig {...createProps({ master: 'master', job: 'job' })} />);

    view.unmount();
    resolveJobs(['job']);
    await flushPromises();

    expect(consoleError).not.toHaveBeenCalled();
  });

  it('ignores stale job list responses for an old master', async () => {
    let resolveOldMasterJobs: (jobs: string[]) => void;
    vi.mocked(IgorService.listJobsForMaster).mockImplementation((master: string) => {
      if (master === 'old-master') {
        return new Promise<string[]>((resolve) => (resolveOldMasterJobs = resolve));
      }
      return Promise.resolve(['new-job']);
    });
    const props = createProps({ master: 'old-master', job: 'old-job' });

    render(<CiBuildStageConfig {...props} />);
    props.stage.master = 'new-master';
    props.stage.job = 'new-job';
    resolveOldMasterJobs(['old-job']);
    await flushPromises();

    expect(props.stage.job).toBe('new-job');
    expect(props.updateStageField).not.toHaveBeenCalledWith({ job: '' });
  });

  it('ignores stale job list responses for a newer job on the same master', async () => {
    let resolveJobs: (jobs: string[]) => void;
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(new Promise((resolve) => (resolveJobs = resolve)));
    const props = createProps({ master: 'master', job: 'old-job' });

    render(<CiBuildStageConfig {...props} />);
    props.stage.job = 'new-job';
    resolveJobs(['old-job']);
    await flushPromises();

    expect(props.stage.job).toBe('new-job');
    expect(props.updateStageField).not.toHaveBeenCalledWith({ job: '' });
  });

  it('ignores stale job config responses after a saved job is cleared', async () => {
    let resolveJobConfig: (config: any) => void;
    const props = createProps({ master: 'master', job: 'old-job' });
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(Promise.resolve(['new-job']));
    vi.mocked(IgorService.getJobConfig).mockReturnValue(new Promise((resolve) => (resolveJobConfig = resolve)));

    render(<CiBuildStageConfig {...props} showJenkinsParameters={true} />);
    await waitFor(() => expect(props.stage.job).toBe(''));
    resolveJobConfig({
      parameterDefinitionList: [
        { name: 'OLD_PARAM', type: 'StringParameterDefinition', defaultValue: 'old', description: 'Old job param' },
      ],
    });
    await flushPromises();

    expect(props.stage.parameters).toBeUndefined();
    expect(screen.queryByText('OLD_PARAM')).not.toBeInTheDocument();
  });

  it('adds inline parameters through a React modal result', async () => {
    const user = setupUser();
    vi.spyOn(AddCiBuildParameterModal, 'show').mockReturnValue(Promise.resolve({ key: 'branch', value: 'main' }));
    const props = createProps({ parameters: {} });

    render(<CiBuildStageConfig {...props} showInlineParameters={true} />);
    await user.click(screen.getByRole('button', { name: 'Add Parameter' }));

    await waitFor(() => expect(props.updateStageField).toHaveBeenCalledWith({ parameters: { branch: 'main' } }));
    expect(props.stageFieldUpdated).toHaveBeenCalled();
  });

  it('renders Jenkins parameter descriptions as help fields', async () => {
    const props = createProps({ master: 'master', job: 'job', parameters: {} });
    vi.mocked(IgorService.listJobsForMaster).mockReturnValue(Promise.resolve(['job']));
    vi.mocked(IgorService.getJobConfig).mockReturnValue(
      Promise.resolve({
        parameterDefinitionList: [
          { name: 'BRANCH', type: 'StringParameterDefinition', defaultValue: 'main', description: 'Branch to build' },
        ],
      } as any),
    );

    render(
      <HelpTextExpandedContext.Provider value={true}>
        <CiBuildStageConfig {...props} showJenkinsParameters={true} />
      </HelpTextExpandedContext.Provider>,
    );

    expect(await screen.findByText('Branch to build')).toBeVisible();
  });

  it('renders unstable build help fields from the configured help key prefix', () => {
    HelpContentsRegistry.register('pipeline.config.jenkins.markUnstableAsSuccessful.false', 'Fail unstable help');
    HelpContentsRegistry.register('pipeline.config.jenkins.markUnstableAsSuccessful.true', 'Accept unstable help');

    render(
      <HelpTextExpandedContext.Provider value={true}>
        <CiBuildStageConfig {...createProps()} />
      </HelpTextExpandedContext.Provider>,
    );

    expect(screen.getByText('Fail unstable help')).toBeVisible();
    expect(screen.getByText('Accept unstable help')).toBeVisible();
  });
});
