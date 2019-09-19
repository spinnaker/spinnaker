# \SecurityGroupControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllByAccountUsingGET1**](SecurityGroupControllerApi.md#AllByAccountUsingGET1) | **Get** /securityGroups/{account} | Retrieve a list of security groups for a given account, grouped by region
[**AllUsingGET5**](SecurityGroupControllerApi.md#AllUsingGET5) | **Get** /securityGroups | Retrieve a list of security groups, grouped by account, cloud provider, and region
[**GetSecurityGroupUsingGET1**](SecurityGroupControllerApi.md#GetSecurityGroupUsingGET1) | **Get** /securityGroups/{account}/{region}/{name} | Retrieve a security group&#39;s details


# **AllByAccountUsingGET1**
> interface{} AllByAccountUsingGET1(ctx, account, optional)
Retrieve a list of security groups for a given account, grouped by region

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **AllUsingGET5**
> interface{} AllUsingGET5(ctx, optional)
Retrieve a list of security groups, grouped by account, cloud provider, and region

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **id** | **string**| id | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetSecurityGroupUsingGET1**
> interface{} GetSecurityGroupUsingGET1(ctx, account, name, region, optional)
Retrieve a security group's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **name** | **string**| name | 
  **region** | **string**| region | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **name** | **string**| name | 
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]
 **vpcId** | **string**| vpcId | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

