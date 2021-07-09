import { IAccount } from '../account';
import { Application } from '../application';

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
