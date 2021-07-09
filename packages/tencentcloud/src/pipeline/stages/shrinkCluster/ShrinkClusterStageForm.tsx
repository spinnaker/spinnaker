import React from 'react';

import {
  AccountService,
  CheckboxInput,
  FormikFormField,
  IAccount,
  IFormikStageConfigInjectedProps,
  NgReact,
  NumberInput,
  SelectInput,
  StageConfigField,
} from '@spinnaker/core';

const { useEffect, useState } = React;
const { AccountRegionClusterSelector } = NgReact;

export function ShrinkClusterStageForm({ application, formik, pipeline }: IFormikStageConfigInjectedProps) {
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

  const pluralize = function (str: string, val: string | number) {
    if (val === 1) {
      return str;
    }
    return str + 's';
  };

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
      <StageConfigField label="Shrink Options">
        <div className="form-inline">
          Shrink to
          <FormikFormField
            name="remainingEnabledServerGroups"
            required
            input={(props) => <NumberInput {...props} min={0} />}
          />
          {pluralize('server group', stage.shrinkToSize)}, keeping the
          <FormikFormField
            name="retainLargerOverNewer"
            required
            input={(props) => (
              <SelectInput
                options={[
                  { label: 'largest', value: 'true' },
                  { label: 'newest', value: 'false' },
                ]}
                {...props}
              />
            )}
          />
          {pluralize('server group', stage.remainingEnabledServerGroups)} enabled.
        </div>
      </StageConfigField>
      <div className="form-group">
        <div className="col-md-offset-3 col-md-6 checkbox">
          <FormikFormField
            name="allowDeleteActive"
            input={(props) => <CheckboxInput {...props} text="Allow deletion of active server groups" />}
          />
        </div>
      </div>
    </div>
  );
}
