import type { ISearchResults } from '@spinnaker/core';
import {
  AccountService,
  InfrastructureCaches,
  NetworkReader,
  REST,
  SearchService,
  SubnetReader,
} from '@spinnaker/core';

import { GceCertificateReader } from '../../../certificate/certificate.reader';
import { GceHealthCheckReader } from '../../../healthCheck/healthCheck.read.service';

export interface IGceLoadBalancerDataItem {
  name: string;
}

export interface IGceLoadBalancerData {
  accounts: IGceLoadBalancerDataItem[];
  addresses: IGceLoadBalancerDataItem[];
  backendServices: IGceLoadBalancerDataItem[];
  certificates: IGceLoadBalancerDataItem[];
  healthChecks: IGceLoadBalancerDataItem[];
  networks: IGceLoadBalancerDataItem[];
  regions: IGceLoadBalancerDataItem[];
  subnets: IGceLoadBalancerDataItem[];
}

export interface IGceLoadBalancerDataReaders {
  accounts: () => PromiseLike<IGceLoadBalancerDataItem[]>;
  addresses: () => PromiseLike<IGceLoadBalancerDataItem[]>;
  backendServices: () => PromiseLike<IGceLoadBalancerDataItem[]>;
  certificates: () => PromiseLike<IGceLoadBalancerDataItem[]>;
  healthChecks: () => PromiseLike<IGceLoadBalancerDataItem[]>;
  networks: () => PromiseLike<IGceLoadBalancerDataItem[]>;
  regions: (account: string) => PromiseLike<IGceLoadBalancerDataItem[]>;
  subnets: () => PromiseLike<IGceLoadBalancerDataItem[]>;
}

export type GceLoadBalancerDataStatus = 'idle' | 'loading' | 'ready' | 'error';

export interface IGceLoadBalancerDataState {
  data: IGceLoadBalancerData;
  error?: unknown;
  status: GceLoadBalancerDataStatus;
}

type GceLoadBalancerDataListener = (state: IGceLoadBalancerDataState) => void;

const EMPTY_DATA: IGceLoadBalancerData = {
  accounts: [],
  addresses: [],
  backendServices: [],
  certificates: [],
  healthChecks: [],
  networks: [],
  regions: [],
  subnets: [],
};

export class GceLoadBalancerDataController {
  private disposed = false;
  private listeners = new Set<GceLoadBalancerDataListener>();
  private requestId = 0;
  private state: IGceLoadBalancerDataState = { data: EMPTY_DATA, status: 'idle' };

  constructor(private readers: IGceLoadBalancerDataReaders = gceLoadBalancerDataReaders) {}

  public getState(): IGceLoadBalancerDataState {
    return this.state;
  }

  public subscribe(listener: GceLoadBalancerDataListener): () => void {
    if (!this.disposed) {
      this.listeners.add(listener);
    }
    return () => this.listeners.delete(listener);
  }

  public async load(account: string): Promise<void> {
    const requestId = ++this.requestId;
    this.publish({ data: this.state.data, error: undefined, status: 'loading' });

    try {
      const [
        accounts,
        addresses,
        backendServices,
        certificates,
        healthChecks,
        networks,
        regions,
        subnets,
      ] = await Promise.all([
        this.readers.accounts(),
        this.readers.addresses(),
        this.readers.backendServices(),
        this.readers.certificates(),
        this.readers.healthChecks(),
        this.readers.networks(),
        this.readers.regions(account),
        this.readers.subnets(),
      ]);
      if (this.isCurrent(requestId)) {
        this.publish({
          data: { accounts, addresses, backendServices, certificates, healthChecks, networks, regions, subnets },
          error: undefined,
          status: 'ready',
        });
      }
    } catch (error) {
      if (this.isCurrent(requestId)) {
        this.publish({ data: this.state.data, error, status: 'error' });
      }
    }
  }

  public dispose(): void {
    this.disposed = true;
    this.requestId += 1;
    this.listeners.clear();
  }

  private isCurrent(requestId: number): boolean {
    return !this.disposed && requestId === this.requestId;
  }

  private publish(state: IGceLoadBalancerDataState): void {
    if (this.disposed) {
      return;
    }
    this.state = state;
    this.listeners.forEach((listener) => listener(state));
  }
}

export function mergeGceResourceOptions<T extends IGceLoadBalancerDataItem>(
  available: readonly T[],
  persisted: readonly T[],
): T[] {
  const availableNames = new Set(available.map(({ name }) => name));
  return [...available, ...persisted.filter(({ name }) => name && !availableNames.has(name))];
}

interface IAddressSearchResult {
  account: string;
  address: string;
  name: string;
  provider: string;
  region: string;
  type: string;
}

const certificateReader = new GceCertificateReader();
const healthCheckReader = new GceHealthCheckReader();

export const gceLoadBalancerDataReaders: IGceLoadBalancerDataReaders = {
  accounts: () => AccountService.listAccounts('gce') as PromiseLike<IGceLoadBalancerDataItem[]>,
  regions: (account) =>
    AccountService.getRegionsForAccount(account).then((regions) => {
      if (Array.isArray(regions)) {
        return regions as IGceLoadBalancerDataItem[];
      }
      return Object.keys(regions || {}).map((name) => ({ name }));
    }),
  networks: () => NetworkReader.listNetworksByProvider('gce') as PromiseLike<IGceLoadBalancerDataItem[]>,
  subnets: () => SubnetReader.listSubnetsByProvider('gce') as PromiseLike<IGceLoadBalancerDataItem[]>,
  certificates: () => certificateReader.listCertificates() as PromiseLike<IGceLoadBalancerDataItem[]>,
  healthChecks: () => healthCheckReader.listHealthChecks() as PromiseLike<IGceLoadBalancerDataItem[]>,
  addresses: () =>
    SearchService.search<IAddressSearchResult>(
      { q: '', type: 'addresses', allowShortQuery: 'true' },
      InfrastructureCaches.get('addresses'),
    ).then((searchResults: ISearchResults<IAddressSearchResult>) =>
      (searchResults?.results || [])
        .filter(({ provider }) => provider === 'gce')
        .map((result) => ({ ...JSON.parse(result.address), account: result.account, region: result.region })),
    ),
  backendServices: () =>
    REST('/search')
      .useCache(true)
      .query({ q: '', type: 'backendServices', allowShortQuery: 'true' })
      .get()
      .then(normalizeBackendServiceResults),
};

function normalizeBackendServiceResults(response: unknown): IGceLoadBalancerDataItem[] {
  const first = Array.isArray(response) && response.length === 1 ? response[0] : response;
  const results = first && typeof first === 'object' && 'results' in first ? first.results : first;
  return Array.isArray(results) ? (results as IGceLoadBalancerDataItem[]) : [];
}
