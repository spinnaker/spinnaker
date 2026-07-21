import React from 'react';

import type { IAccount } from '../account';
import type { Application } from '../application';
import { AngularJSAdapter } from '../reactShims';

export interface IAccountRegionClusterSelectorProps {
  application: Application;
  component: Record<string, any>;
  accounts: IAccount[] | string[];
  clusterField?: string;
  singleRegion?: string;
  showAllRegions?: boolean;
  onAccountUpdate?: (account: string) => void;
  disableRegionSelect?: boolean;
}

export function AccountRegionClusterSelector(props: IAccountRegionClusterSelectorProps) {
  return (
    <AngularJSAdapter
      template={`
        <account-region-cluster-selector-wrapper
          application="props.application"
          component="props.component"
          accounts="props.accounts"
          cluster-field="props.clusterField"
          single-region="props.singleRegion"
          show-all-regions="props.showAllRegions"
          on-account-update="props.onAccountUpdate"
          disable-region-select="props.disableRegionSelect">
        </account-region-cluster-selector-wrapper>
      `}
      locals={props}
    />
  );
}
