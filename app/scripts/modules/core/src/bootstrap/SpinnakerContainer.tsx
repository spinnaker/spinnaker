import * as React from 'react';
import { UIView } from '@uirouter/react';
import { SpinErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { RecoilRoot } from 'recoil';

import { CustomBanner } from '../header/customBanner/CustomBanner';
import { Notifier } from '../widgets/notifier/Notifier';
import { SpinnakerHeader } from '../header/SpinnakerHeader';
import { Spinner } from '../widgets/spinners/Spinner';

export interface ISpinnakerContainerProps {
  authenticating: boolean;
  routing: boolean;
}

export const SpinnakerContainer = ({ authenticating, routing }: ISpinnakerContainerProps) => (
  <SpinErrorBoundary category="SpinnakerContainer">
    <RecoilRoot>
      <div className="spinnaker-container grid-container">
        {!authenticating && routing && (
          <div className="transition-overlay">
            <Spinner size="medium" />
          </div>
        )}
        <div className="navbar-inverse grid-header">
          <CustomBanner />
          <SpinnakerHeader />
        </div>
        <div className="spinnaker-content grid-contents">{!authenticating && <UIView name="main" />}</div>
      </div>
      <Notifier />
    </RecoilRoot>
  </SpinErrorBoundary>
);
