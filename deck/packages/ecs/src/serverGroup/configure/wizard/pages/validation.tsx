import React from 'react';

import type { IWizardPageComponent } from '@spinnaker/core';

import type { IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';

type ValidationErrors = { [key: string]: any };
type Validator = (values: IEcsServerGroupCommand) => ValidationErrors;

const required = (value: any): boolean => value !== undefined && value !== null && value !== '';
const validInteger = (value: any): boolean => Number.isFinite(value) && Number.isInteger(value) && value >= 0;
const usableCapacityProviderStrategyItem = (item: any): boolean =>
  required(item?.capacityProvider) && validInteger(item?.base) && validInteger(item?.weight);
const hasRowErrors = (errors: ValidationErrors[]): boolean => errors.some((error) => Object.keys(error).length > 0);
const isExpressionLanguage = (value: string): boolean => value?.includes('${');
const isStackPattern = (value = ''): boolean =>
  isExpressionLanguage(value) || /^([a-zA-Z_0-9._${}]*(\${.+})*)*$/.test(value);
const isDetailPattern = (value = ''): boolean =>
  isExpressionLanguage(value) || /^([a-zA-Z_0-9._${}-]*(\${.+})*)*$/.test(value);

const validateTargetGroupMappings = (values: IEcsServerGroupCommand): ValidationErrors => {
  const targetGroupMappings = (values.targetGroupMappings || []).map((mapping) => {
    const errors: ValidationErrors = {};
    if (values.useTaskDefinitionArtifact && !required(mapping.containerName)) {
      errors.containerName = 'Container name is required.';
    }
    if (!required(mapping.targetGroup)) {
      errors.targetGroup = 'Target group is required.';
    }
    if (!validInteger(mapping.containerPort)) {
      errors.containerPort = 'Container port must be a non-negative integer.';
    }
    return errors;
  });
  return hasRowErrors(targetGroupMappings) ? { targetGroupMappings } : {};
};

export const validateEcsBasicSettings: Validator = (values) => {
  const errors: ValidationErrors = {};
  if (!required(values.credentials)) {
    errors.credentials = 'Account is required.';
  }
  if (!required(values.region)) {
    errors.region = 'Region is required.';
  }
  if (!required(values.ecsClusterName)) {
    errors.ecsClusterName = 'ECS cluster is required.';
  }
  if (!isStackPattern(values.stack)) {
    errors.stack = 'Only dot(.) and underscore(_) special characters are allowed in the Stack field.';
  }
  if (!isDetailPattern(values.freeFormDetails)) {
    errors.freeFormDetails =
      'Only dot(.), underscore(_), and dash(-) special characters are allowed in the Detail field.';
  }
  return errors;
};

export const validateEcsTaskDefinition: Validator = (values) => {
  if (!values.useTaskDefinitionArtifact) {
    return {};
  }
  const errors: ValidationErrors = validateTargetGroupMappings(values);
  if (!values.taskDefinitionArtifact?.artifact && !values.taskDefinitionArtifact?.artifactId) {
    errors.taskDefinitionArtifact = 'Task definition artifact is required.';
  }
  const containerMappings = (values.containerMappings || []).map((mapping) => {
    const mappingErrors: ValidationErrors = {};
    if (!required(mapping.containerName)) {
      mappingErrors.containerName = 'Container name is required.';
    }
    if (!required(mapping.imageDescription?.imageId)) {
      mappingErrors.imageDescription = 'Container image is required.';
    }
    return mappingErrors;
  });
  if (hasRowErrors(containerMappings)) {
    errors.containerMappings = containerMappings;
  }
  return errors;
};

export const validateEcsContainer: Validator = (values) => {
  if (values.useTaskDefinitionArtifact) {
    return {};
  }
  const errors: ValidationErrors = validateTargetGroupMappings(values);
  if (!required(values.imageDescription?.imageId)) {
    errors.imageDescription = { imageId: 'Container image is required.' };
  }
  return errors;
};

export const validateEcsServiceDiscovery: Validator = (values) => {
  const serviceDiscoveryAssociations = (values.serviceDiscoveryAssociations || []).map((association) => {
    const errors: ValidationErrors = {};
    if (values.useTaskDefinitionArtifact && !required(association.containerName)) {
      errors.containerName = 'Container name is required.';
    }
    if (!required(association.registry?.id) && !required(association.registry?.displayName)) {
      errors.registry = 'Service registry is required.';
    }
    if (!validInteger(association.containerPort)) {
      errors.containerPort = 'Container port must be a non-negative integer.';
    }
    return errors;
  });
  return hasRowErrors(serviceDiscoveryAssociations) ? { serviceDiscoveryAssociations } : {};
};

export const validateEcsCapacity: Validator = (values) => {
  const capacity = values.capacity || {};
  const useCapacityProviders =
    values.computeOption === 'capacityProviders' ||
    (!values.computeOption && (values.capacityProviderStrategy || []).length > 0);
  const validationErrors: ValidationErrors = {};
  const errors: ValidationErrors = {};
  ['min', 'desired', 'max'].forEach((field) => {
    if (!validInteger(capacity[field])) {
      errors[field] = 'Capacity must be a non-negative integer.';
    }
  });
  if (Object.keys(errors).length === 0) {
    if (capacity.min > capacity.max) {
      errors.min = 'Minimum capacity cannot exceed maximum capacity.';
    }
    if (capacity.desired < capacity.min || capacity.desired > capacity.max) {
      errors.desired = 'Desired capacity must be between minimum and maximum capacity.';
    }
  }
  if (!useCapacityProviders && !required(values.launchType)) {
    validationErrors.launchType = 'Launch type is required.';
  }
  if (useCapacityProviders && values.useDefaultCapacityProviders !== false) {
    const defaultStrategy = values.backingData?.filtered?.defaultCapacityProviderStrategy || [];
    if (!defaultStrategy.length || !defaultStrategy.every(usableCapacityProviderStrategyItem)) {
      validationErrors.capacityProviderStrategy = 'A usable default capacity provider strategy is required.';
    }
  }
  if (useCapacityProviders && values.useDefaultCapacityProviders === false) {
    const strategy = values.capacityProviderStrategy || [];
    if (!strategy.length) {
      validationErrors.capacityProviderStrategy = 'At least one capacity provider is required.';
    } else {
      const strategyErrors = strategy.map((item) => {
        const itemErrors: ValidationErrors = {};
        if (!required(item.capacityProvider)) {
          itemErrors.capacityProvider = 'Capacity provider is required.';
        }
        if (!validInteger(item.base)) {
          itemErrors.base = 'Base must be a non-negative integer.';
        }
        if (!validInteger(item.weight)) {
          itemErrors.weight = 'Weight must be a non-negative integer.';
        }
        return itemErrors;
      });
      if (hasRowErrors(strategyErrors)) {
        validationErrors.capacityProviderStrategy = strategyErrors;
      }
    }
  }
  if (Object.keys(errors).length) {
    validationErrors.capacity = errors;
  }
  return validationErrors;
};

const hasLoadBalancer = (values: IEcsServerGroupCommand): boolean =>
  required(values.targetGroup) || (values.targetGroupMappings || []).some((mapping) => required(mapping?.targetGroup));

export const validateEcsNativeDeployment: Validator = (values) => {
  const errors: ValidationErrors = {};
  if (values.cloudProvider !== 'ecs-native') {
    return errors;
  }
  const strategy = values.strategy;
  if (required(strategy) && strategy !== 'none' && strategy !== '') {
    errors.strategy = 'Native ECS deployments require the Spinnaker deployment strategy to be None.';
  }
  const minimumHealthyPercent = values.minimumHealthyPercent;
  const maximumPercent = values.maximumPercent;
  if (
    required(minimumHealthyPercent) &&
    (!Number.isInteger(minimumHealthyPercent) || minimumHealthyPercent < 0 || minimumHealthyPercent > 100)
  ) {
    // ECS accepts 0..100 (0 lets ECS stop every old task before starting new ones).
    errors.minimumHealthyPercent = 'Minimum healthy percent must be an integer from 0 to 100.';
  }
  if (required(maximumPercent) && (!Number.isInteger(maximumPercent) || maximumPercent < 100)) {
    errors.maximumPercent = 'Maximum percent must be an integer of at least 100.';
  }
  if (
    required(minimumHealthyPercent) &&
    required(maximumPercent) &&
    Number.isInteger(minimumHealthyPercent) &&
    Number.isInteger(maximumPercent) &&
    maximumPercent < minimumHealthyPercent
  ) {
    errors.maximumPercent = 'Maximum percent cannot be less than minimum healthy percent.';
  }

  const blueGreenFields = [
    'alternateTargetGroupArn',
    'productionListenerRule',
    'testListenerRule',
    'blueGreenRoleArn',
  ] as const;
  const setCount = blueGreenFields.filter((field) => required(values[field])).length;
  const isBlueGreen = values.deploymentStrategy === 'BLUE_GREEN';

  // AWS ECS requires the ALB traffic-shift config on every load balancer for a BLUE_GREEN deploy,
  // and rejects the deploy with a 400 otherwise ("advancedConfiguration field is required for all
  // loadBalancers when using the Blue/green deployment strategy"). So when the strategy is
  // BLUE_GREEN and a load balancer is attached, all four fields are required -- not optional.
  // (With no load balancer, BLUE_GREEN needs none of them.)
  if (isBlueGreen && hasLoadBalancer(values) && setCount < blueGreenFields.length) {
    const message =
      'The Blue/Green strategy on a load-balanced service requires all four fields: alternate target group ARN, production listener rule, test listener rule, and blue/green IAM role. Provide all four, or use the Rolling strategy.';
    blueGreenFields.forEach((field) => {
      if (!required(values[field])) {
        errors[field] = message;
      }
    });
    return errors;
  }

  // Otherwise the four fields remain all-or-nothing: a partial set is always a misconfiguration.
  if (setCount > 0 && setCount < blueGreenFields.length) {
    const message =
      'Alternate target group ARN, production listener rule, test listener rule, and blue/green IAM role must all be set together, or all left blank.';
    blueGreenFields.forEach((field) => {
      if (!required(values[field])) {
        errors[field] = message;
      }
    });
  }
  return errors;
};

export const validateEcsServerGroup: Validator = (values) => ({
  ...validateEcsBasicSettings(values),
  ...validateEcsTaskDefinition(values),
  ...validateEcsContainer(values),
  ...validateEcsServiceDiscovery(values),
  ...validateEcsCapacity(values),
  ...validateEcsNativeDeployment(values),
});

interface IEcsWizardPageValidationProps {
  children: React.ReactElement;
  validator: Validator;
}

export class EcsWizardPageValidation
  extends React.Component<IEcsWizardPageValidationProps>
  implements IWizardPageComponent<IEcsServerGroupCommand> {
  public validate = (values: IEcsServerGroupCommand) => this.props.validator(values);

  public render() {
    return React.Children.only(this.props.children);
  }
}
