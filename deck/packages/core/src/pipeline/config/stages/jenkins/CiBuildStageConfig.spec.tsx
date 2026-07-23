import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';
import Select from 'react-select';

import { BuildServiceType, IgorService } from '../../../../ci/igor.service';
import { HelpField } from '../../../../help';
import { ReactModal } from '../../../../presentation';
import { REACT_MODULE } from '../../../../reactShims';
import { CiBuildStageConfig } from './CiBuildStageConfig';

describe('<CiBuildStageConfig />', () => {
  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  beforeEach(() => {
    spyOn(IgorService, 'listMasters').and.returnValue(Promise.resolve([]));
    spyOn(IgorService, 'listJobsForMaster').and.returnValue(Promise.resolve([]));
    spyOn(IgorService, 'getJobConfig').and.returnValue(Promise.resolve({ parameterDefinitionList: [] } as any));
  });

  const flushPromises = () => new Promise((resolve) => setTimeout(resolve));

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
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage: jasmine.createSpy('updateStage'),
      updateStageField: jasmine
        .createSpy('updateStageField')
        .and.callFake((changes: any) => Object.assign(stage, changes)),
      waitForCompletionHelpKey: 'pipeline.config.jenkins.waitForCompletion',
    };
  };

  const expectFullJobListSearchBeforeLimit = async (buildServiceType: BuildServiceType) => {
    const jobs = Array.from({ length: 500 }, (_value, index) => `common-job-${index}`);
    jobs.push('target-job-after-limit');
    (IgorService.listJobsForMaster as any).and.returnValue(Promise.resolve(jobs));
    const props = createProps({ master: 'master' });

    const component = mount(<CiBuildStageConfig {...props} buildServiceType={buildServiceType} />);
    await flushPromises();
    component.update();

    const jobSelect = component.find(Select).filterWhere((select) => select.prop('placeholder') === 'Start typing...');
    const options = jobSelect.prop('options') as any[];
    const filterOptions = jobSelect.prop('filterOptions') as any;
    const filter = (query: string) => filterOptions(options, query, [], jobSelect.props());

    expect(filterOptions).toBeDefined();
    expect(filter('')).toHaveSize(100);
    expect(filter('common-job')).toHaveSize(100);
    expect(filter('target-job-after-limit')).toEqual([
      { label: 'target-job-after-limit', value: 'target-job-after-limit' },
    ]);
  };

  it('sets legacy defaults without marking the stage dirty', () => {
    const props = createProps();

    mount(<CiBuildStageConfig {...props} />);

    expect(props.stage.failPipeline).toBe(true);
    expect(props.stage.continuePipeline).toBe(false);
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('initializes missing job parameters without marking the stage dirty', async () => {
    const props = createProps({ master: 'master', job: 'job' });
    (IgorService.listJobsForMaster as any).and.returnValue(Promise.resolve(['job']));

    mount(<CiBuildStageConfig {...props} />);
    await flushPromises();

    expect(props.stage.parameters).toEqual({});
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('clears saved jobs missing from Igor through updateStageField', async () => {
    const props = createProps({ master: 'master', job: 'missing-job' });
    (IgorService.listJobsForMaster as any).and.returnValue(Promise.resolve(['current-job']));

    mount(<CiBuildStageConfig {...props} />);
    await flushPromises();

    expect(props.stage.job).toBe('');
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
    (IgorService.listMasters as any).and.returnValue(mastersPromise);
    const props = createProps();

    const component = mount(<CiBuildStageConfig {...props} />);

    expect(component.state('mastersRefreshing')).toBe(true);
    rejectMasters!(new Error('failed to load masters'));
    await mastersPromise.catch(() => undefined);
    await flushPromises();

    expect(component.state('mastersRefreshing')).toBe(false);
  });

  it('does not set state when async job list responses resolve after unmount', async () => {
    let resolveJobs: (jobs: string[]) => void;
    (IgorService.listJobsForMaster as any).and.returnValue(new Promise((resolve) => (resolveJobs = resolve)));
    const props = createProps({ master: 'master', job: 'job' });

    const component = mount(<CiBuildStageConfig {...props} />);
    const instance = component.instance() as CiBuildStageConfig;
    const setStateSpy = spyOn(instance, 'setState').and.callThrough();

    component.unmount();
    resolveJobs!(['job']);
    await flushPromises();

    expect(setStateSpy).not.toHaveBeenCalled();
  });

  it('ignores stale job list responses for an old master', async () => {
    let resolveOldMasterJobs: (jobs: string[]) => void;
    (IgorService.listJobsForMaster as any).and.callFake((master: string) => {
      if (master === 'old-master') {
        return new Promise<string[]>((resolve) => (resolveOldMasterJobs = resolve));
      }
      return Promise.resolve(['new-job']);
    });
    const props = createProps({ master: 'old-master', job: 'old-job' });

    mount(<CiBuildStageConfig {...props} />);
    props.stage.master = 'new-master';
    props.stage.job = 'new-job';
    resolveOldMasterJobs!(['old-job']);
    await flushPromises();

    expect(props.stage.job).toBe('new-job');
    expect(props.updateStageField).not.toHaveBeenCalledWith({ job: '' });
  });

  it('ignores stale job list responses for a newer job on the same master', async () => {
    let resolveJobs: (jobs: string[]) => void;
    (IgorService.listJobsForMaster as any).and.returnValue(new Promise((resolve) => (resolveJobs = resolve)));
    const props = createProps({ master: 'master', job: 'old-job' });

    mount(<CiBuildStageConfig {...props} />);
    props.stage.job = 'new-job';
    resolveJobs!(['old-job']);
    await flushPromises();

    expect(props.stage.job).toBe('new-job');
    expect(props.updateStageField).not.toHaveBeenCalledWith({ job: '' });
  });

  it('ignores stale job config responses after a saved job is cleared', async () => {
    let resolveJobConfig: (config: any) => void;
    const props = createProps({ master: 'master', job: 'old-job' });
    (IgorService.listJobsForMaster as any).and.returnValue(Promise.resolve(['new-job']));
    (IgorService.getJobConfig as any).and.returnValue(new Promise((resolve) => (resolveJobConfig = resolve)));

    const component = mount(<CiBuildStageConfig {...props} showJenkinsParameters={true} />);
    await flushPromises();

    expect(props.stage.job).toBe('');
    resolveJobConfig!({
      parameterDefinitionList: [
        { name: 'OLD_PARAM', type: 'StringParameterDefinition', defaultValue: 'old', description: 'Old job param' },
      ],
    });
    await flushPromises();
    component.update();

    expect(props.stage.parameters).toBeUndefined();
    expect(
      component
        .find('b.break-word')
        .filterWhere((parameterName) => parameterName.text() === 'OLD_PARAM')
        .exists(),
    ).toBe(false);
  });

  it('adds inline parameters through a React modal result', async () => {
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve({ key: 'branch', value: 'main' }));
    const props = createProps({ parameters: {} });

    const component = mount(<CiBuildStageConfig {...props} showInlineParameters={true} />);
    component.find('button.add-new').simulate('click');
    await flushPromises();

    expect(ReactModal.show).toHaveBeenCalled();
    expect(props.updateStageField).toHaveBeenCalledWith({ parameters: { branch: 'main' } });
    expect(props.stageFieldUpdated).toHaveBeenCalled();
  });

  it('renders Jenkins parameter descriptions as help fields', async () => {
    const props = createProps({ master: 'master', job: 'job', parameters: {} });
    (IgorService.listJobsForMaster as any).and.returnValue(Promise.resolve(['job']));
    (IgorService.getJobConfig as any).and.returnValue(
      Promise.resolve({
        parameterDefinitionList: [
          { name: 'BRANCH', type: 'StringParameterDefinition', defaultValue: 'main', description: 'Branch to build' },
        ],
      } as any),
    );

    const component = mount(<CiBuildStageConfig {...props} showJenkinsParameters={true} />);
    await flushPromises();
    component.update();

    expect(
      component.find(HelpField).filterWhere((helpField) => helpField.prop('content') === 'Branch to build').length,
    ).toBe(1);
  });

  it('renders unstable build help fields from the configured help key prefix', () => {
    const component = mount(<CiBuildStageConfig {...createProps()} />);

    const helpFieldIds = component.find(HelpField).map((helpField) => helpField.prop('id'));

    expect(helpFieldIds).toContain('pipeline.config.jenkins.markUnstableAsSuccessful.false');
    expect(helpFieldIds).toContain('pipeline.config.jenkins.markUnstableAsSuccessful.true');
  });
});
