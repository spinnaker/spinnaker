import * as React from 'react';
import { IPromise } from 'angular';
import { FormikProps } from 'formik';
import { $q } from 'ngimport';
import { Option } from 'react-select';

import { BuildServiceType, IgorService } from 'core/ci';
import { IConcourseTrigger } from 'core/domain';
import { FormikFormField, ReactSelectInput, useLatestPromise } from 'core/presentation';

import { ConcourseService } from './concourse.service';

export interface IConcourseTriggerConfigProps {
  trigger: IConcourseTrigger;
  formik: FormikProps<IConcourseTrigger>;
}

export function ConcourseTrigger({ formik, trigger }: IConcourseTriggerConfigProps) {
  const { team, project, master } = trigger;

  const pipeline = project && project.split('/').pop();

  const onTeamChanged = () => {
    formik.setFieldValue('job', '');
    formik.setFieldValue('jobName', '');
    formik.setFieldValue('project', '');
  };

  const onProjectChanged = () => {
    formik.setFieldValue('job', '');
    formik.setFieldValue('jobName', '');
  };

  const onJobChanged = (job: string) => {
    const jobName = job && job.split('/').pop();
    formik.setFieldValue('jobName', jobName);
  };

  // Fetches data when anything in the dependencies list changes
  // Returns an empty array if any dependencies are falsey
  function useData<T>(callback: () => IPromise<T>, deps: any[]) {
    const anyDepsMissing = deps.some(dep => !dep);
    const defaultValueCallback = () => $q.resolve(([] as any) as T);
    return useLatestPromise(anyDepsMissing ? defaultValueCallback : callback, deps);
  }

  const fetchMasters = useData(() => IgorService.listMasters(BuildServiceType.Concourse), []);
  const fetchTeams = useData(() => ConcourseService.listTeamsForMaster(master), [master]);
  const fetchPipelines = useData(() => ConcourseService.listPipelinesForTeam(master, team), [master, team]);
  const fetchJobs = useData(
    () =>
      ConcourseService.listJobsForPipeline(master, team, pipeline).then(jobs =>
        jobs.map(job => `${team}/${pipeline}/${job}`),
      ),
    [master, team, pipeline],
  );

  const lastSegmentOptionRenderer = (option: Option<string>) => <>{option.value.split('/').pop()}</>;

  return (
    <>
      <FormikFormField
        name="master"
        label="Master"
        fastField={false}
        input={props => (
          <ReactSelectInput
            {...props}
            clearable={false}
            disabled={fetchMasters.status === 'PENDING'}
            isLoading={fetchMasters.status === 'PENDING'}
            stringOptions={fetchMasters.result}
            placeholder="Select a master..."
          />
        )}
      />

      <FormikFormField
        name="team"
        label="Team"
        fastField={false}
        onChange={onTeamChanged}
        input={props => (
          <ReactSelectInput
            {...props}
            clearable={false}
            disabled={!master || fetchTeams.status === 'PENDING'}
            isLoading={fetchTeams.status === 'PENDING'}
            stringOptions={fetchTeams.result}
            placeholder="Select a team..."
          />
        )}
      />

      <FormikFormField
        name="project"
        label="Pipeline"
        fastField={false}
        onChange={onProjectChanged}
        input={props => (
          <ReactSelectInput
            {...props}
            clearable={false}
            disabled={!team || fetchPipelines.status === 'PENDING'}
            isLoading={fetchPipelines.status === 'PENDING'}
            stringOptions={fetchPipelines.result}
            optionRenderer={lastSegmentOptionRenderer}
            valueRenderer={lastSegmentOptionRenderer}
            placeholder="Select a pipeline..."
          />
        )}
      />

      <FormikFormField
        name="job"
        label="Job"
        fastField={false}
        onChange={onJobChanged}
        input={props => (
          <ReactSelectInput
            {...props}
            clearable={false}
            disabled={!pipeline || fetchJobs.status === 'PENDING'}
            isLoading={fetchJobs.status === 'PENDING'}
            stringOptions={fetchJobs.result}
            optionRenderer={lastSegmentOptionRenderer}
            valueRenderer={lastSegmentOptionRenderer}
            placeholder="Select a job..."
          />
        )}
      />
    </>
  );
}
