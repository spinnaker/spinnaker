import { isEmpty } from 'lodash';
import * as React from 'react';

export interface IServerGroupNamePreviewProps {
  createsNewCluster: boolean;
  latestServerGroupName: string;
  mode: string;
  namePreview: string;
  navigateToLatestServerGroup: () => void;
}

const SPEL_EXPRESSION_REGEX = /\${[^}]+}/g;
const SPEL_WITH_DEFAULT_AND_ALPHANUMERICAL_REGEX = /^\${\s*#alphanumerical\(.*\)\s*\?:\s*[^\s]+\s*}$/;

export const ServerGroupNamePreview = ({
  createsNewCluster,
  latestServerGroupName,
  mode,
  namePreview,
  navigateToLatestServerGroup,
}: IServerGroupNamePreviewProps) => {
  const spelMatches = namePreview.match(SPEL_EXPRESSION_REGEX);
  if (!isEmpty(spelMatches)) {
    const anyInvalidSpel = [...spelMatches].some((match) => !SPEL_WITH_DEFAULT_AND_ALPHANUMERICAL_REGEX.test(match));
    return (
      <div className="form-group">
        <div className="col-md-12">
          <div className={`well-compact ${anyInvalidSpel ? 'alert alert-warning' : 'well'}`}>
            <h5 className="text-center">
              <p>Your server group cluster will be determined by evaluating the expressions dynamically</p>
              {anyInvalidSpel && (
                <div className="text-left">
                  NOTE: Please wrap your expression with #alphanumerical and provide a default value to make sure only
                  valid values are used after evaluation. i.e {"${#alphanumerical(yourExpression) ?: 'defaultValue'}"}
                </div>
              )}
            </h5>
          </div>
        </div>
      </div>
    );
  }

  const showPreviewAsWarning = (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);

  return (
    <div className="form-group">
      <div className="col-md-12">
        <div className={`well-compact ${showPreviewAsWarning ? 'alert alert-warning' : 'well'}`}>
          <h5 className="text-center">
            <p>Your server group will be in the cluster:</p>
            <p>
              <strong>
                {namePreview}
                {createsNewCluster && <span> (new cluster)</span>}
              </strong>
            </p>
            {!createsNewCluster && mode === 'create' && latestServerGroupName && (
              <div className="text-left">
                <p>There is already a server group in this cluster. Do you want to clone it?</p>
                <p>
                  Cloning copies the entire configuration from the selected server group, allowing you to modify
                  whichever fields (e.g. image) you need to change in the new server group.
                </p>
                <p>
                  To clone a server group, select "Clone" from the "Server Group Actions" menu in the details view of
                  the server group.
                </p>
                <p>
                  <a className="clickable" onClick={navigateToLatestServerGroup}>
                    Go to details for {latestServerGroupName}
                  </a>
                </p>
              </div>
            )}
          </h5>
        </div>
      </div>
    </div>
  );
};
