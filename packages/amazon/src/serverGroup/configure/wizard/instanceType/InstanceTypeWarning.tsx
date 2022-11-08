import React from 'react';

import type { IAmazonServerGroupCommandDirty } from '../../serverGroupConfiguration.service';

export function InstanceTypeWarning(props: { dirty: IAmazonServerGroupCommandDirty; clearWarnings: () => void }) {
  if (
    props.dirty.instanceType ||
    (props.dirty.launchTemplateOverridesForInstanceType &&
      props.dirty.launchTemplateOverridesForInstanceType.length > 0)
  ) {
    return (
      <div className="col-md-12">
        <div className="alert alert-warning">
          <p>
            <i className="fa fa-exclamation-triangle" />
            {props.dirty.instanceType &&
              'The following instance type was found incompatible with the selected image/region and was removed:'}
            {props.dirty.launchTemplateOverridesForInstanceType &&
              props.dirty.launchTemplateOverridesForInstanceType.length > 0 &&
              'The following instance type(s) were found incompatible with the selected image/region and were removed:'}
          </p>
          <ul>
            {props.dirty.instanceType && <li key={props.dirty.instanceType}>{props.dirty.instanceType}</li>}
            {props.dirty.launchTemplateOverridesForInstanceType &&
              props.dirty.launchTemplateOverridesForInstanceType.length > 0 &&
              props.dirty.launchTemplateOverridesForInstanceType.map((it) => (
                <li key={it.instanceType}>
                  {it.instanceType}
                  {it.weightedCapacity ? ' with weight ' + it.weightedCapacity : ''}
                </li>
              ))}
          </ul>
          <p className="text-right">
            <a className="btn btn-sm btn-default dirty-flag-dismiss clickable" onClick={() => props.clearWarnings()}>
              Okay
            </a>
          </p>
        </div>
      </div>
    );
  } else {
    return null;
  }
}
