# \SecurityGroupControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllByAccountUsingGET**](SecurityGroupControllerApi.md#AllByAccountUsingGET) | **Get** /securityGroups/{account} | Retrieve a list of security groups for a given account, grouped by region
[**AllUsingGET2**](SecurityGroupControllerApi.md#AllUsingGET2) | **Get** /securityGroups | Retrieve a list of security groups, grouped by account, cloud provider, and region
[**GetSecurityGroupUsingGET**](SecurityGroupControllerApi.md#GetSecurityGroupUsingGET) | **Get** /securityGroups/{account}/{region}/{name} | Retrieve a security group&#39;s details


# **AllByAccountUsingGET**
> interface{} AllByAccountUsingGET(ctx, account, optional)
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
 **provider** | **string**| provider | [default to aws]
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **AllUsingGET2**
> interface{} AllUsingGET2(ctx, optional)
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
 **id** | **string**| id | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetSecurityGroupUsingGET**
> interface{} GetSecurityGroupUsingGET(ctx, account, region, name, optional)
Retrieve a security group's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **region** | **string**| region | 
  **name** | **string**| name | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **region** | **string**| region | 
 **name** | **string**| name | 
 **provider** | **string**| provider | [default to aws]
 **vpcId** | **string**| vpcId | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

