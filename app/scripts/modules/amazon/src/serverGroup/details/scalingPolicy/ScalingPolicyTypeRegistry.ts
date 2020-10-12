export interface IScalingPolicyTypeConfig {
  type: string;
  summaryTemplateUrl: string;
}

export class ScalingPolicyTypeRegistrar {
  private policyTypes: IScalingPolicyTypeConfig[] = [];

  public registerPolicyType(policyConfig: IScalingPolicyTypeConfig): void {
    this.policyTypes.push(policyConfig);
  }

  public getPolicyConfig(policyType: string): IScalingPolicyTypeConfig {
    return this.policyTypes.find((p) => p.type === policyType);
  }
}

export const ScalingPolicyTypeRegistry = new ScalingPolicyTypeRegistrar();
