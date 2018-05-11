# \InstanceControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetConsoleOutputUsingGET**](InstanceControllerApi.md#GetConsoleOutputUsingGET) | **Get** /instances/{account}/{region}/{instanceId}/console | Retrieve an instance&#39;s console output
[**GetInstanceDetailsUsingGET**](InstanceControllerApi.md#GetInstanceDetailsUsingGET) | **Get** /instances/{account}/{region}/{instanceId} | Retrieve an instance&#39;s details


# **GetConsoleOutputUsingGET**
> interface{} GetConsoleOutputUsingGET(ctx, account, region, instanceId, optional)
Retrieve an instance's console output

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **region** | **string**| region | 
  **instanceId** | **string**| instanceId | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **region** | **string**| region | 
 **instanceId** | **string**| instanceId | 
 **provider** | **string**| provider | [default to aws]
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetInstanceDetailsUsingGET**
> interface{} GetInstanceDetailsUsingGET(ctx, account, region, instanceId, optional)
Retrieve an instance's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **region** | **string**| region | 
  **instanceId** | **string**| instanceId | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **region** | **string**| region | 
 **instanceId** | **string**| instanceId | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

