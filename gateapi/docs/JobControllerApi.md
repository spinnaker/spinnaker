# \JobControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetJobUsingGET**](JobControllerApi.md#GetJobUsingGET) | **Get** /applications/{applicationName}/jobs/{account}/{region}/{name} | Get job
[**GetJobsUsingGET**](JobControllerApi.md#GetJobsUsingGET) | **Get** /applications/{applicationName}/jobs | Get jobs


# **GetJobUsingGET**
> map[string]interface{} GetJobUsingGET(ctx, applicationName, account, region, name, optional)
Get job

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **applicationName** | **string**| applicationName | 
  **account** | **string**| account | 
  **region** | **string**| region | 
  **name** | **string**| name | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **applicationName** | **string**| applicationName | 
 **account** | **string**| account | 
 **region** | **string**| region | 
 **name** | **string**| name | 
 **expand** | **string**| expand | [default to false]
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetJobsUsingGET**
> []interface{} GetJobsUsingGET(ctx, applicationName, optional)
Get jobs

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **applicationName** | **string**| applicationName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **applicationName** | **string**| applicationName | 
 **expand** | **string**| expand | [default to false]
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

