import React from 'react';

import { CollapsibleSection, HelpField } from '@spinnaker/core';

import { IAmazonLaunchTemplateOverrides } from '../../../index';

import './multipleInstanceTypesSubSection.less';

export function MultipleInstanceTypesSubSection(props: { instanceTypeOverrides: IAmazonLaunchTemplateOverrides[] }) {
  if (!props.instanceTypeOverrides) {
    return null;
  }

  return (
    <CollapsibleSection
      heading="Instance Types"
      defaultExpanded={true}
      outerDivClassName="multiple-instance-types-subsection"
      toggleClassName="clickable subsection-heading"
      headingClassName="collapsible-subheading"
    >
      <table className="table table-condensed packed" id="MultipleInstanceTypes">
        <thead>
          <tr>
            <th id="instanceType">
              Type <HelpField id="aws.serverGroup.multipleInstanceTypes"></HelpField>
            </th>
            <th id="weight">
              Weighted Capacity <HelpField id="aws.serverGroup.instanceTypeWeight"></HelpField>
            </th>
          </tr>
        </thead>
        <tbody>
          {props.instanceTypeOverrides.map((override) => [
            <tr>
              <td headers="instanceType">{override.instanceType}</td>
              <td headers="weight">{override.weightedCapacity}</td>
            </tr>,
          ])}
        </tbody>
      </table>
    </CollapsibleSection>
  );
}
