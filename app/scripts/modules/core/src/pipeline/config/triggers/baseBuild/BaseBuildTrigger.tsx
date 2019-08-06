import * as React from 'react';
import { FormikProps } from 'formik';

import { BuildServiceType, IgorService } from 'core/ci/igor.service';
import { IBuildTrigger } from 'core/domain';
import { HelpField } from 'core/help';
import { FormikFormField, TextInput, useLatestPromise } from 'core/presentation';

import { RefreshableReactSelectInput } from '../RefreshableReactSelectInput';

export interface IBaseBuildTriggerConfigProps {
  formik: FormikProps<IBuildTrigger>;
  buildTriggerType: BuildServiceType;
}

export function BaseBuildTrigger(buildTriggerProps: IBaseBuildTriggerConfigProps) {
  const { formik, buildTriggerType } = buildTriggerProps;
  const trigger = formik.values;
  const { master, job, type } = trigger;

  const fetchMasters = useLatestPromise(() => {
    return IgorService.listMasters(buildTriggerType);
  }, []);

  const fetchJobs = useLatestPromise(() => {
    return master ? IgorService.listJobsForMaster(master) : null;
  }, [master]);

  React.useEffect(() => {
    const jobsFetched = fetchJobs.status === 'RESOLVED';
    const selectedJobFound = jobsFetched && fetchJobs.result && fetchJobs.result.includes(job);
    if (job && jobsFetched && !selectedJobFound) {
      formik.setFieldValue('job', null);
    }
  }, [fetchJobs.result]);

  return (
    <>
      <FormikFormField
        name="master"
        label="Master"
        fastField={false}
        input={props => (
          <RefreshableReactSelectInput
            {...props}
            stringOptions={fetchMasters.result}
            disabled={fetchMasters.status !== 'RESOLVED'}
            isLoading={fetchMasters.status === 'PENDING'}
            onRefreshClicked={() => fetchMasters.refresh()}
            refreshButtonTooltipText={fetchMasters.status === 'PENDING' ? 'Masters refreshing' : 'Refresh masters list'}
            placeholder="Select a master..."
          />
        )}
      />

      <FormikFormField
        name="job"
        label="Job"
        fastField={false}
        input={props => (
          <RefreshableReactSelectInput
            {...props}
            mode="VIRTUALIZED"
            stringOptions={fetchJobs.result}
            disabled={!master || fetchJobs.status !== 'RESOLVED'}
            isLoading={fetchJobs.status === 'PENDING'}
            onRefreshClicked={() => fetchJobs.refresh()}
            refreshButtonTooltipText={fetchJobs.status === 'PENDING' ? 'Jobs refreshing' : 'Refresh job list'}
            placeholder={'Select a job...'}
          />
        )}
      />

      <FormikFormField
        name="propertyFile"
        label="Property File"
        help={<HelpField id={`pipeline.config.${type}.trigger.propertyFile`} />}
        input={props => <TextInput {...props} />}
      />
    </>
  );
}
