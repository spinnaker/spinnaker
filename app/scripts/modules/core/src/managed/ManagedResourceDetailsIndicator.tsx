import * as React from 'react';
import { get } from 'lodash';

import { IEntityTags } from 'core/domain';
import { HoverablePopover } from 'core/presentation';

import './ManagedResourceDetailsIndicator.less';

export const MANAGED_BY_SPINNAKER_TAG_NAME = 'spinnaker_ui_notice:managed_by_spinnaker';

export interface IManagedResourceDetailsIndicatorProps {
  entityTags: IEntityTags[];
}

export const ManagedResourceDetailsIndicator = ({ entityTags }: IManagedResourceDetailsIndicatorProps) => {
  const isManaged =
    get(entityTags, 'length') &&
    entityTags.some(({ tags }) => tags.some(({ name }) => name === MANAGED_BY_SPINNAKER_TAG_NAME));

  if (!isManaged) {
    return null;
  }

  const helpText = (
    <>
      <p>
        <b>Spinnaker is continuously managing this resource.</b>
      </p>
      <p>Changes made in the UI will be stomped in favor of the existing declarative configuration.</p>
    </>
  );

  return (
    <HoverablePopover template={helpText} placement="left">
      <div className="band band-info ManagedResourceDetailsIndicator">
        <span className="icon">🌈</span>
        Managed by Spinnaker
      </div>
    </HoverablePopover>
  );
};
