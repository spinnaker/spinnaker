import * as React from 'react';
import { CollapsibleSection, LabeledValue, LabeledValueList } from '@spinnaker/core';
import { ITitusBuildInfo } from '../../domain';

export interface ITitusPackageDetailsSectionProps {
  buildInfo: ITitusBuildInfo;
}

export const TitusPackageDetailsSection = ({ buildInfo }: ITitusPackageDetailsSectionProps) => {
  const packageInfo = buildInfo.jenkins || {};
  const hasPackageInfo = Boolean(Object.keys(packageInfo).length);

  const { commitId, host, name, number, version } = packageInfo;
  const jenkinsLink = `${host}job/${name}/${number}`;

  return (
    <CollapsibleSection heading="Package">
      {!hasPackageInfo && <div>No package information available.</div>}
      {hasPackageInfo && (
        <LabeledValueList className="horizontal-when-filters-collapsed">
          {name && <LabeledValue label="Job" value={name} />}
          {buildInfo.docker?.image && <LabeledValue label="Image Name" value={buildInfo.docker?.image} />}
          {number && <LabeledValue label="Build" value={number} />}
          {commitId && <LabeledValue label="Commit" value={commitId.substring(0, 8)} />}
          {version && <LabeledValue label="Version" value={version} />}
          {host && (
            <LabeledValue
              label="Build Link"
              value={
                <a target="_blank" href={jenkinsLink}>
                  {jenkinsLink}
                </a>
              }
            />
          )}
        </LabeledValueList>
      )}
    </CollapsibleSection>
  );
};
