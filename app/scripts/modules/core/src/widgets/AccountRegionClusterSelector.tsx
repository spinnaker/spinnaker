import { Application, IAccount } from 'core';

export interface IAccountRegionClusterSelectorProps {
  application: Application;
  component: Object;
  accounts: IAccount[] | string[];
  clusterField?: string;
  singleRegion?: string;
  showAllRegions?: boolean;
  onAccountUpdate?: (account: string) => void;
  disableRegionSelect?: boolean;
}
