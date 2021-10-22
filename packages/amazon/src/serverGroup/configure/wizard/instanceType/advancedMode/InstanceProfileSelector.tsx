import React from 'react';

import type { IAmazonInstanceTypeCategory } from '../../../../../instance/awsInstanceType.service';

import './advancedMode.less';

export interface IInstanceProfileSelectorProps {
  currentProfile: string;
  handleProfileChange: (profile: string) => void;
  instanceProfileList: IAmazonInstanceTypeCategory[];
}

export function InstanceProfileSelector(props: IInstanceProfileSelectorProps) {
  return (
    <div>
      <h4 style={{ marginTop: '10px' }}>This application is</h4>
      {props.instanceProfileList.map((profile) => (
        <div key={profile.type} className={`instance-profile-header profile-button`}>
          <button
            type="button"
            onClick={() => props.handleProfileChange(profile.type)}
            className={props.currentProfile === profile.type ? 'instance-profile active' : 'instance-profile'}
          >
            {props.currentProfile === profile.type && <span className="far fa-check-circle selected-indicator" />}
            <div className="panel-heading">
              <h4>
                <span className={`glyphicon glyphicon-${profile.icon}`} />
                <div>{profile.label}</div>
              </h4>
            </div>
          </button>
        </div>
      ))}
    </div>
  );
}
