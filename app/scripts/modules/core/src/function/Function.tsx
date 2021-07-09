import { UISref, UISrefActive } from '@uirouter/react';
import React from 'react';

import { Application } from '../application/application.model';
import { IFunction } from '../domain';
import { EntityNotifications } from '../entityTag/notifications/EntityNotifications';

interface IFunctionProps {
  application: Application;
  functionDef: IFunction;
}

const Function = (props: IFunctionProps) => {
  const { application, functionDef } = props;
  const params = {
    application: application.name,
    region: functionDef.region,
    account: functionDef.account,
    functionName: functionDef.functionName,
    cloudProvider: functionDef.cloudProvider,
  };
  return (
    <div className="pod-subgroup function">
      <div className="function-header sticky-header-2">
        <UISrefActive class="active">
          <UISref to=".functionDetails" params={params}>
            <h6 className="clickable clickable-row horizontal middle">
              <i className="fa fa-xs fa-fw fa-asterisk" />
              &nbsp; {(functionDef.region || '').toUpperCase()}
              <div className="flex-1">
                <EntityNotifications
                  entity={functionDef}
                  application={application}
                  placement="bottom"
                  entityType="function"
                  pageLocation="pod"
                  onUpdate={() => application.functions.refresh()}
                />
              </div>
            </h6>
          </UISref>
        </UISrefActive>
      </div>
    </div>
  );
};
export default Function;
