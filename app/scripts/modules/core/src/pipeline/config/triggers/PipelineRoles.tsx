import React from 'react';
import Select, { Option } from 'react-select';

import { AuthenticationService } from '../../../authentication';
import { HelpField } from '../../../help';

const { useState, useEffect } = React;

export interface IPipelineRolesConfigProps {
  roles: any[];
  updateRoles: (roles: string[]) => void;
}

export const PipelineRoles = (props: IPipelineRolesConfigProps) => {
  const [allowedRoles, setAllowedRoles] = useState([]);

  useEffect(() => {
    setAllowedRoles(AuthenticationService.getAuthenticatedUser().roles);
  }, []);

  const onAllowedRolesChanged = (options: Array<Option<string>>) => {
    const roles = options.map((o) => o.value);
    props.updateRoles(roles);
  };

  return (
    <div className="form-group row">
      <div className="col-md-10">
        <div className="row">
          <label className="col-md-3 sm-label-right">
            <span>Permissions </span>
            <HelpField id="pipeline.config.roles.help" />
          </label>
          <div className="col-md-9">
            <Select
              multi={true}
              onChange={onAllowedRolesChanged}
              options={allowedRoles.map((a) => ({ label: a, value: a }))}
              value={props.roles || []}
            />
          </div>
        </div>
      </div>
    </div>
  );
};
