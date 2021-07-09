export interface IService {
  name: string;
  servicePlans: IServicePlan[];
}

export interface IServicePlan {
  name: string;
}
