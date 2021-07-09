import { ILoadBalancer, ILoadBalancerDeleteCommand, ILoadBalancerUpsertCommand, ISubnet } from '@spinnaker/core';

export type ListenerProtocol = 'HTTP' | 'HTTPS' | 'TCP' | 'SSL';
export enum LoadBalancingPolicy {
  ROUND_ROBIN = 'ROUND_ROBIN',
  IP_HASH = 'IP_HASH',
  LEAST_CONNECTIONS = 'LEAST_CONNECTIONS',
}

export interface IOracleSubnet extends ISubnet {
  id: string;
  name: string;
  availabilityDomain: string;
  securityListIds: string[];
  vcnId: string;
}

export interface IOracleLoadBalancer extends ILoadBalancer {
  shape: string; // required
  isPrivate: boolean; // required
  subnets: IOracleSubnet[]; // required 1 for private LB, 2 for public LB
  certificates?: { [name: string]: IOracleListenerCertificate };
  listeners?: { [name: string]: IOracleListener }; // not required to create LB, but useless without it
  hostnames?: IOracleHostname[];
  backendSets?: { [name: string]: IOracleBackEndSet }; // not required to create LB, but useless without it
  freeformTags?: { [tagName: string]: string };
  id?: string; // not required to create LB
  subnetTypeMap?: { [id: string]: 'AD' | 'Regional' };
  // TODO support path route sets
}

// This is created from loadBalancer/loadBalancer.states.ts
export interface ILoadBalancerDetails {
  name: string;
  accountId: string;
  region: string;
  vpcId: string;
}

export interface IOracleListener {
  name: string;
  protocol: ListenerProtocol;
  port: number;
  defaultBackendSetName: string;
  isSsl: boolean;
  sslConfiguration?: IOracleListenerSSLConfiguration;
  hostnames?: IOracleHostname[];
  // TODO support pathRouteSets
}

export interface IOracleListenerSSLConfiguration {
  certificateName: string;
  verifyDepth: number;
  verifyPeerCertificates: boolean;
}

export interface IOracleHostname {
  name: string;
  hostname: string;
}

export interface IOracleBackEndSet {
  name: string;
  policy: LoadBalancingPolicy;
  healthChecker: IOracleBackendSetHealthCheck;
  // TODO desagar sessionPersistenceConfiguration?: IOracleLoadBalancerSessionPersistenceConfiguration;
  backends: any[];
  isNew: boolean;
}

export interface IOracleListenerCertificate {
  certificateName: string;
  publicCertificate: string;
  caCertificate: string;
  privateKey: string;
  passphrase: string;
  isNew: boolean;
}

export interface IOracleBackendSetHealthCheck {
  urlPath: string; // required
  protocol: 'HTTP' | 'TCP';
  port: number;
  intervalMillis?: number;
  timeoutMillis?: number;
  retries?: number;
  returnCode?: number;
  responseBodyRegex?: string;
}

/**
 * IOracleLoadBalancerUpsertCommand is nearly identical to IOracleLoadBalancer -
 * Command objects are the shape of data sent to gate.
 */
export interface IOracleLoadBalancerUpsertCommand extends ILoadBalancerUpsertCommand {
  shape: string; // required
  isPrivate: boolean; // required
  subnetIds: string[]; // required 1 for private LB, 2 for public LB
  certificates?: { [name: string]: IOracleListenerCertificate };
  listeners?: { [name: string]: IOracleListener }; // not required to create LB, but useless without it
  hostnames?: IOracleHostname[];
  backendSets?: { [name: string]: IOracleBackEndSet }; // not required to create LB, but useless without it
  freeformTags?: { [tagName: string]: string };
  vpcId: string;
  subnetTypeMap?: { [id: string]: 'AD' | 'Regional' };
}

export interface IOracleLoadBalancerDeleteCommand extends ILoadBalancerDeleteCommand {
  loadBalancerId: string;
}
