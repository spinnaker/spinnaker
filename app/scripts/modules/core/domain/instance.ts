import { Health } from './health';

export class Instance {
  id: string;
  availabilityZone?: string;
  healthState?: string;
  health: Health[];
}
