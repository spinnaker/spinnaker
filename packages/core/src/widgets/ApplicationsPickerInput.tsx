import React, { useMemo } from 'react';

import { ApplicationReader } from '../application';
import {
  asyncMessage,
  errorMessage,
  IFormInputProps,
  ReactSelectInput,
  useData,
  useInternalValidator,
  useLatestCallback,
} from '../presentation';

interface IApplicationsPickerInputProps extends IFormInputProps {
  /** When true, selects multiple apps. The picker returns an array of applications */
  multi?: boolean;
}

/** This input supports single or multiple selection of applications */
export function ApplicationsPickerInput(props: IApplicationsPickerInputProps) {
  const getAppNames = () => ApplicationReader.listApplications().then((apps) => apps.map((app) => app.name).sort());
  const { result: apps, status } = useData(getAppNames, [], []);
  const options = useMemo(() => apps.map((app) => ({ label: app, value: app })), [apps]);

  const validator = useLatestCallback((value: string | string[]) => {
    if (status === 'PENDING') {
      return asyncMessage('Loading apps...');
    }

    const selectedApps: string[] = !value ? [] : typeof value === 'string' ? [value] : value;
    const missing = selectedApps.find((app) => !apps.includes(app));
    return missing ? errorMessage(`Application ${missing} does not exist.`) : undefined;
  });

  useInternalValidator(props.validation, validator, [apps, status]);

  return <ReactSelectInput {...props} mode="VIRTUALIZED" options={options} isLoading={status === 'PENDING'} />;
}
