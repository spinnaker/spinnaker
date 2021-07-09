import { FormikProps } from 'formik';
import { uniq } from 'lodash';
import React from 'react';

import { RefreshableReactSelectInput } from '../RefreshableReactSelectInput';
import { Application } from '../../../../application';
import { IBaseBuildTriggerConfigProps } from '../baseBuild/BaseBuildTrigger';
import { BuildServiceType, IgorService } from '../../../../ci/igor.service';
import { IWerckerTrigger } from '../../../../domain';
import { FormikFormField, ReactSelectInput, useLatestPromise } from '../../../../presentation';

export interface IWerckerTriggerConfigProps extends IBaseBuildTriggerConfigProps {
  formik: FormikProps<IWerckerTrigger>;
  trigger: IWerckerTrigger;
  application: Application;
  pipelineId: string;
  triggerUpdated: (trigger: IWerckerTrigger) => void;
}

// given a job name i.e., "git/organization/repo/job" or "pipeline/organization/repo/jobname"
// returns { app: 'organization/repo', pipeline: 'job' } or { app: 'organization/repo', pipeline: 'jobname' }
function getJobParts(job: string) {
  const firstSlash = job.indexOf('/');
  const lastSlash = job.lastIndexOf('/');

  const app = job.substring(firstSlash + 1, lastSlash);
  const pipeline = job.substring(lastSlash + 1);
  return { app, pipeline };
}

export function WerckerTrigger(werckerTriggerProps: IWerckerTriggerConfigProps) {
  const { formik } = werckerTriggerProps;
  const { app, master, pipeline } = formik.values;

  const fetchMasters = useLatestPromise(() => IgorService.listMasters(BuildServiceType.Wercker), []);

  const mastersLoading = fetchMasters.status === 'PENDING';
  const mastersLoaded = fetchMasters.status === 'RESOLVED';

  const fetchJobs = useLatestPromise(() => {
    return master && IgorService.listJobsForMaster(master).then((x) => x.map(getJobParts));
  }, [master]);

  const jobs = fetchJobs.result;
  const jobsLoading = fetchJobs.status === 'PENDING';
  const jobsLoaded = fetchJobs.status === 'RESOLVED';

  const apps = React.useMemo(() => {
    return jobsLoaded ? uniq(jobs.map((job) => job.app)) : [];
  }, [jobsLoaded, jobs]);

  const pipelines = React.useMemo(() => {
    return jobsLoaded ? jobs.filter((parts) => parts.app === app).map((parts) => parts.pipeline) : [];
  }, [jobsLoaded, app, jobs]);

  // Clear out app or pipeline if they aren't in the fetched jobs data
  React.useEffect(() => {
    if (jobsLoaded && !!app && !apps.includes(app)) {
      formik.setFieldValue('app', null);
    }
    if (jobsLoaded && !!pipeline && !pipelines.includes(pipeline)) {
      formik.setFieldValue('pipeline', null);
    }
  }, [jobsLoaded, apps, app, pipelines, pipeline]);

  // Update 'job' field when pipeline or app changes
  React.useEffect(() => {
    const hasJob = !!app && !!pipeline;
    const job = hasJob ? `${app}/${pipeline}` : null;
    formik.setFieldValue('job', job);
  }, [pipeline, app]);

  return (
    <>
      <FormikFormField
        name="master"
        label="Build Service"
        input={(props) => (
          <RefreshableReactSelectInput
            {...props}
            stringOptions={fetchMasters.result}
            placeholder={'Select a build service...'}
            disabled={!mastersLoaded}
            isLoading={mastersLoading}
            onRefreshClicked={() => fetchMasters.refresh()}
            refreshButtonTooltipText={mastersLoading ? 'Masters refreshing' : 'Refresh masters list'}
          />
        )}
      />

      <FormikFormField
        name="app"
        label="Application"
        input={(props) => (
          <RefreshableReactSelectInput
            {...props}
            stringOptions={apps}
            placeholder={'Select an application...'}
            disabled={!master || !jobsLoaded}
            isLoading={jobsLoading}
            onRefreshClicked={() => fetchJobs.refresh()}
            refreshButtonTooltipText={jobsLoading ? 'Apps refreshing' : 'Refresh app list'}
          />
        )}
      />

      <FormikFormField
        name="pipeline"
        label="Pipeline"
        input={(props) => (
          <ReactSelectInput
            {...props}
            disabled={!master || !jobsLoaded}
            isLoading={jobsLoading}
            stringOptions={pipelines}
            placeholder="Select a pipeline"
          />
        )}
      />
    </>
  );
}
