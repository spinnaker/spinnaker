export interface ITagResult {
  isValid: boolean;
  error: TagError | null;
  errorMessage?: string;
}

export enum TagError {
  TAG_NUMBER_EXCEED,
  TAG_KEY_LENGTH_EXCEED,
  TAG_VALUE_LENGTH_EXCEED,
  TAG_KEY_INVALID_CHARACTER,
  TAG_VALUE_INVALID_CHARACTER,
  TAG_OBJECT_UNDEFINED,
}

export interface IAzureLoadBalancer {
  type: string;
  description: string;
}

export const AzureLoadBalancerTypes: IAzureLoadBalancer[] = [
  {
    type: 'Azure Load Balancer',
    description: '',
  },
  {
    type: 'Azure Application Gateway',
    description: '',
  },
];

export default class Utility {
  public static readonly TAG_LIMITATION: number = 8;
  public static readonly TAG_KEY_LENGTH_LIMITATION: number = 512;
  public static readonly TAG_VALUE_LENGTH_LIMITATION: number = 256;
  public static readonly TAG_INVALID_CHAR_REG_EXR: RegExp = /[<>%&\\?/]/;

  public static checkTags(tagsObject: { [s: string]: string }): ITagResult {
    if (!tagsObject) {
      return {
        isValid: false,
        error: TagError.TAG_OBJECT_UNDEFINED,
        errorMessage: 'instanceTags is not defined',
      };
    }
    const length: number = Object.keys(tagsObject).length;
    if (!(length >= 0 && length <= Utility.TAG_LIMITATION)) {
      return {
        isValid: false,
        error: TagError.TAG_NUMBER_EXCEED,
        errorMessage: `Number of tags exceeds the limit: ${Utility.TAG_LIMITATION}`,
      };
    }

    for (const [k, v] of Object.entries(tagsObject)) {
      if (k.length > Utility.TAG_KEY_LENGTH_LIMITATION) {
        return {
          isValid: false,
          error: TagError.TAG_KEY_LENGTH_EXCEED,
          errorMessage: `Length of Tag key: ${k} exceeds the limit: ${Utility.TAG_KEY_LENGTH_LIMITATION}`,
        };
      }
      if (v.length > Utility.TAG_VALUE_LENGTH_LIMITATION) {
        return {
          isValid: false,
          error: TagError.TAG_VALUE_LENGTH_EXCEED,
          errorMessage: `Length of Tag value: ${v} exceeds the limit: ${Utility.TAG_VALUE_LENGTH_LIMITATION}`,
        };
      }
      if (Utility.TAG_INVALID_CHAR_REG_EXR.test(k)) {
        return {
          isValid: false,
          error: TagError.TAG_KEY_INVALID_CHARACTER,
          errorMessage: `Invalid characters in Tag key: ${k}`,
        };
      }
      if (Utility.TAG_INVALID_CHAR_REG_EXR.test(v)) {
        return {
          isValid: false,
          error: TagError.TAG_VALUE_INVALID_CHARACTER,
          errorMessage: `Invalid characters in Tag value: ${v}`,
        };
      }
    }
    return {
      isValid: true,
      error: null,
    };
  }

  public static getLoadBalancerType(typeString: string): IAzureLoadBalancer | null {
    typeString = typeString.toLowerCase().split('_').join(' ');
    return AzureLoadBalancerTypes.find((lb: IAzureLoadBalancer) => lb.type.toLowerCase() === typeString) || null;
  }
}
