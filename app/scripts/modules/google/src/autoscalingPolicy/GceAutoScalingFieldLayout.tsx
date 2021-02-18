import { isEmpty } from 'lodash';
import React from 'react';

import { ILayoutProps } from '@spinnaker/core';

import './gceAutoscalingFieldLayout.less';

// todo(mneterval): remove when GCE Autoscaling Controls are entirely converted to React & Formik
export function GceAutoScalingFieldLayout(props: ILayoutProps) {
  const { label, help, input, actions, validation, required } = props;

  const showLabel = !isEmpty(label) || !isEmpty(help);
  const { hidden, messageNode } = validation;

  return (
    <div className="gce-autoscaling-layout">
      {showLabel && (
        <label className="col-md-3 sm-label-right">
          {label}
          {required && <span>*</span>} {help}
        </label>
      )}
      <div className="col-md-2 content-fields">
        <div>
          {input} {actions}
        </div>
        {!hidden && <div className="message">{messageNode}</div>}
      </div>
    </div>
  );
}
