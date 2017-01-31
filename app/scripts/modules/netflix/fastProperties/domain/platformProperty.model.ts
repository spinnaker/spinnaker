// This represents the model of a Fast Property that comes from the API that is managed by the
// Platform team.
export interface IPlatformProperty {
  [k: string]: any // Index signature
  propertyId: string;
  cmcTicket: string;
  constraints: string;
  createdAsCanary: boolean;
  description: string;
  email: string;
  key: string;
  sourceOfUpdate: string;
  ts: string;
  ttl: number;
  updatedBy: string;
  value: string;
  env: string;
  region: string;
  appId: string;
  stack: string;
  cluster: string;
  asg: string;
  zone: string;
  serverId: string;
}
