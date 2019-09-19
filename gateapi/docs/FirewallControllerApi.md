# \FirewallControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllByAccountAndRegionUsingGET**](FirewallControllerApi.md#AllByAccountAndRegionUsingGET) | **Get** /firewalls/{account}/{region} | Retrieve a list of firewalls for a given account and region
[**AllByAccountUsingGET**](FirewallControllerApi.md#AllByAccountUsingGET) | **Get** /firewalls/{account} | Retrieve a list of firewalls for a given account, grouped by region
[**AllUsingGET1**](FirewallControllerApi.md#AllUsingGET1) | **Get** /firewalls | Retrieve a list of firewalls, grouped by account, cloud provider, and region
[**GetSecurityGroupUsingGET**](FirewallControllerApi.md#GetSecurityGroupUsingGET) | **Get** /firewalls/{account}/{region}/{name} | Retrieve a firewall&#39;s details


# **AllByAccountAndRegionUsingGET**
> []interface{} AllByAccountAndRegionUsingGET(ctx, account, region, optional)
Retrieve a list of firewalls for a given account and region

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **region** | **string**| region | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **AllByAccountUsingGET**
> interface{} AllByAccountUsingGET(ctx, account, optional)
Retrieve a list of firewalls for a given account, grouped by region

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

# **AllUsingGET1**
> interface{} AllUsingGET1(ctx, optional)
Retrieve a list of firewalls, grouped by account, cloud provider, and region

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

# **GetSecurityGroupUsingGET**
> interface{} GetSecurityGroupUsingGET(ctx, account, name, region, optional)
Retrieve a firewall's details

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

