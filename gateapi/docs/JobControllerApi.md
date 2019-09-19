# \JobControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetJobUsingGET**](JobControllerApi.md#GetJobUsingGET) | **Get** /applications/{applicationName}/jobs/{account}/{region}/{name} | Get job


# **GetJobUsingGET**
> map[string]interface{} GetJobUsingGET(ctx, account, applicationName, name, region, optional)
Get job

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **applicationName** | **string**| applicationName | 
  **name** | **string**| name | 
  **region** | **string**| region | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **applicationName** | **string**| applicationName | 
 **name** | **string**| name | 
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **expand** | **string**| expand | [default to false]

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

