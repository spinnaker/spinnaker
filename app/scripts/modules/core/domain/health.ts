
import { LoadBalancer } from "./loadBalancer";

export class Health {
  type: string;
  loadBalancers: LoadBalancer[];
}
