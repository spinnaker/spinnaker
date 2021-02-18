import React from 'react';

import {
  AccountService,
  FormikFormField,
  IAccount,
  IFormikStageConfigInjectedProps,
  NgReact,
  NumberInput,
  StageConfigField,
} from '@spinnaker/core';

const { useEffect, useState } = React;
const { AccountRegionClusterSelector } = NgReact;

export function RollbackClusterStageForm({ application, formik, pipeline }: IFormikStageConfigInjectedProps) {
  const stage = formik.values;
  const { setFieldValue } = formik;
  const [accounts, setAccounts] = useState([]);

  useEffect(() => {
    AccountService.listAccounts('tencentcloud').then((accounts: IAccount[]) => {
      setAccounts(accounts);
    });

    if (
      stage.isNew &&
      application?.attributes?.platformHealthOnlyShowOverride &&
      application?.attributes?.platformHealthOnly
    ) {
      setFieldValue('interestingHealthProviderNames', ['Tencentcloud']);
    }

    if (!stage.credentials && application?.defaultCredentials?.tencentcloud) {
      setFieldValue('credentials', application?.defaultCredentials?.tencentcloud);
    }

    if (!stage?.regions?.length && application?.defaultRegions?.tencentcloud) {
      setFieldValue('regions', [...stage.regions, application?.defaultRegions?.tencentcloud]);
    }

    if (stage.remainingEnabledServerGroups === undefined) {
      setFieldValue('remainingEnabledServerGroups', 1);
    }

    if (stage.preferLargerOverNewer === undefined) {
      setFieldValue('preferLargerOverNewer', 'false');
    }
  }, []);

  return (
    <div className="form-horizontal">
      {!pipeline.strategy && (
        <AccountRegionClusterSelector
          application={application}
          clusterField="cluster"
          component={stage}
          accounts={accounts}
        />
      )}
      <StageConfigField label="Rollback Options">
        <div className="row">
          <div className="col-sm-10 col-sm-offset-2">
            Wait
            <FormikFormField name="wait" required input={(props) => <NumberInput {...props} min={0} />} />
            seconds between regional rollbacks.
          </div>
          <div className="col-sm-10 col-sm-offset-2">
            Consider rollback successful when
            <FormikFormField
              name="remainingEnabledServerGroups"
              required
              input={(props) => <NumberInput {...props} min={0} max={100} />}
            />
            percent of instances are healthy.
          </div>
        </div>
      </StageConfigField>
    </div>
  );
}
