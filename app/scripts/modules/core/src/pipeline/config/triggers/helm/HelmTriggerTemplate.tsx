import React, { useEffect, useState } from 'react';

import { AccountService } from '../../../../account/AccountService';
import { IHelmTrigger } from '../../../../domain/IHelmTrigger';
import { HelpField } from '../../../../help/HelpField';
import { FormField, ReactSelectInput, TextInput } from '../../../../presentation';

export interface IHelmTriggerTemplateProps {
  trigger: IHelmTrigger;
  isManual: boolean;
  onHelmChanged: (changes: IHelmTriggerTemplateState) => void;
}

export interface IHelmTriggerTemplateState {
  accounts: string[];
  account: string;
  chart: string;
  version: string;
}

export const HelmTriggerTemplate = (props: IHelmTriggerTemplateProps) => {
  const [accounts, setAccounts] = useState<IHelmTriggerTemplateState['accounts']>([]);
  const [account, setAccount] = useState<IHelmTriggerTemplateState['account']>(props.trigger.account);
  const [chart, setChart] = useState<IHelmTriggerTemplateState['chart']>(props.trigger.chart);
  const [version, setVersion] = useState<IHelmTriggerTemplateState['version']>(props.trigger.version);

  useEffect(() => {
    // Fetch Helm-compatible accounts
    AccountService.getArtifactAccounts().then((allAccounts) => {
      const accounts = allAccounts
        .filter((a) => {
          return a.types.includes('helm/chart');
        })
        .map((it) => it.name);

      setAccounts(accounts);
    });
  }, []);

  const stateChanged = (setter: Function, field: string, value: string) => {
    setter(value);

    // Send the data back to any wrapper that needs it
    if (props.onHelmChanged) {
      props.onHelmChanged(
        Object.assign(
          {
            accounts,
            account,
            chart,
            version,
          },
          {
            [field]: value,
          },
        ),
      );
    }
  };

  // The help is slightly different for the manual start vs the trigger config
  const helpId = `pipeline.config.trigger.helm.version${props.isManual ? '.manual' : ''}`;

  return (
    <>
      <FormField
        label="Account"
        value={account}
        onChange={(e: any) => stateChanged(setAccount, 'account', e.target.value || '')}
        input={(props) => <ReactSelectInput {...props} stringOptions={accounts} clearable={false} />}
      />

      <FormField
        label="Chart"
        help={<HelpField id="pipeline.config.trigger.helm.chart" />}
        value={chart || ''}
        onChange={(e: any) => stateChanged(setChart, 'chart', e.target.value || '')}
        input={(props) => <TextInput {...props} />}
      />

      <FormField
        label="Version"
        help={<HelpField id={helpId} />}
        value={version || ''}
        onChange={(e: any) => stateChanged(setVersion, 'version', e.target.value || '')}
        input={(props) => <TextInput {...props} />}
      />
    </>
  );
};
