import { isUndefined } from 'lodash';
import React from 'react';

import { IFormInputValidation, ValidationMessage } from '@spinnaker/core';
import { ILayoutProps } from '@spinnaker/core';
import './TitusMapLayout.less';

export function TitusMapLayout(props: ILayoutProps) {
  const { label, help, input, actions, validation = {} as IFormInputValidation } = props;
  const { hidden, messageNode, category } = validation;
  const showLabel = !isUndefined(label) || !isUndefined(help);

  return (
    <div className="TitusMapLayout flex-container-v">
      {showLabel && (
        <h4>
          {label} {help}
        </h4>
      )}

      <div className="flex-container-h baseline margin-between-lg TitusMapLayout_Contents">
        {input} {actions}
      </div>

      {!hidden && <ValidationMessage message={messageNode} type={category} />}
    </div>
  );
}
