import React from 'react';

import { REST, SETTINGS } from '@spinnaker/core';

export interface ICanaryAnalysisNameSelectorProps {
  value: string;
  className?: string;
  onChange: (value: string) => void;
}

export function CanaryAnalysisNameSelector({ value, className, onChange }: ICanaryAnalysisNameSelectorProps) {
  const [nameList, setNameList] = React.useState<string[]>([]);
  const queryListUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.queryListUrl : null;

  React.useEffect(() => {
    REST('/canaryConfig/names')
      .get()
      .then((results: string[]) => setNameList(results.sort()))
      .catch(() => setNameList([]));
  }, []);

  return (
    <div>
      {nameList.length > 0 ? (
        <select
          required={true}
          value={value || ''}
          className={`${className || ''} visible-sm-inline-block visible-md-inline-block visible-lg-inline-block`}
          style={{ width: 245 }}
          onChange={(e) => onChange(e.target.value)}
        >
          <option value="" />
          {nameList.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      ) : (
        <input
          required={true}
          type="text"
          value={value || ''}
          className={className}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
      {queryListUrl && (
        <a className="btn btn-link" href={queryListUrl} target="_blank">
          ACA Configurations &nbsp;
          <i className="fa fa-external-link" />
        </a>
      )}
    </div>
  );
}
