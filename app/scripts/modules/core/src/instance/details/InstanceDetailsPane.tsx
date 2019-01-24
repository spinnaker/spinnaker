import * as React from 'react';
import { ReactInjector } from 'core/reactShims';
import { UISref } from '@uirouter/react';

export const InstanceDetailsPane = (props: { children: React.ReactNode }) => {
  const isStandalone = ReactInjector.$uiRouter.globals.current.name === 'instanceDetails';

  return (
    <div className="details-panel">
      <div className="header">
        <div className="close-button">
          <UISref to={isStandalone ? 'home.infrastructure' : '^'}>
            <a className="btn btn-link">
              <span className="glyphicon glyphicon-remove" />
            </a>
          </UISref>
        </div>
        {props.children}
      </div>
    </div>
  );
};
