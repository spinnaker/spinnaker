# \BuildControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetBuildMastersUsingGET**](BuildControllerApi.md#GetBuildMastersUsingGET) | **Get** /v2/builds | Get build masters
[**GetBuildUsingGET**](BuildControllerApi.md#GetBuildUsingGET) | **Get** /v2/builds/{buildMaster}/build/{number}/** | Get build for build master
[**GetBuildsUsingGET**](BuildControllerApi.md#GetBuildsUsingGET) | **Get** /v2/builds/{buildMaster}/builds/** | Get builds for build master
[**GetJobConfigUsingGET**](BuildControllerApi.md#GetJobConfigUsingGET) | **Get** /v2/builds/{buildMaster}/jobs/** | Get job config
[**GetJobsForBuildMasterUsingGET**](BuildControllerApi.md#GetJobsForBuildMasterUsingGET) | **Get** /v2/builds/{buildMaster}/jobs | Get jobs for build master
[**V3GetBuildMastersUsingGET**](BuildControllerApi.md#V3GetBuildMastersUsingGET) | **Get** /v3/builds | Get build masters
[**V3GetBuildUsingGET**](BuildControllerApi.md#V3GetBuildUsingGET) | **Get** /v3/builds/{buildMaster}/build/{number} | Get build for build master
[**V3GetBuildsUsingGET**](BuildControllerApi.md#V3GetBuildsUsingGET) | **Get** /v3/builds/{buildMaster}/builds | Get builds for build master
[**V3GetJobConfigUsingGET**](BuildControllerApi.md#V3GetJobConfigUsingGET) | **Get** /v3/builds/{buildMaster}/job | Get job config
[**V3GetJobsForBuildMasterUsingGET**](BuildControllerApi.md#V3GetJobsForBuildMasterUsingGET) | **Get** /v3/builds/{buildMaster}/jobs | Get jobs for build master


# **GetBuildMastersUsingGET**
> []interface{} GetBuildMastersUsingGET(ctx, optional)
Get build masters

Deprecated, use the v3 endpoint instead

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***BuildControllerApiGetBuildMastersUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a BuildControllerApiGetBuildMastersUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **type_** | **optional.String**| type | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetBuildUsingGET**
> map[string]interface{} GetBuildUsingGET(ctx, buildMaster, number)
Get build for build master

Deprecated, use the v3 endpoint instead

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 
  **number** | **string**| number | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetBuildsUsingGET**
> []interface{} GetBuildsUsingGET(ctx, buildMaster)
Get builds for build master

Deprecated, use the v3 endpoint instead

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetJobConfigUsingGET**
> map[string]interface{} GetJobConfigUsingGET(ctx, buildMaster)
Get job config

Deprecated, use the v3 endpoint instead

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetJobsForBuildMasterUsingGET**
> []interface{} GetJobsForBuildMasterUsingGET(ctx, buildMaster)
Get jobs for build master

Deprecated, use the v3 endpoint instead

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V3GetBuildMastersUsingGET**
> []interface{} V3GetBuildMastersUsingGET(ctx, optional)
Get build masters

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***BuildControllerApiV3GetBuildMastersUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a BuildControllerApiV3GetBuildMastersUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **type_** | **optional.String**| type | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V3GetBuildUsingGET**
> map[string]interface{} V3GetBuildUsingGET(ctx, buildMaster, job, number)
Get build for build master

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 
  **job** | **string**| job | 
  **number** | **string**| number | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V3GetBuildsUsingGET**
> []interface{} V3GetBuildsUsingGET(ctx, buildMaster, job)
Get builds for build master

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 
  **job** | **string**| job | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V3GetJobConfigUsingGET**
> map[string]interface{} V3GetJobConfigUsingGET(ctx, buildMaster, job)
Get job config

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 
  **job** | **string**| job | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V3GetJobsForBuildMasterUsingGET**
> []interface{} V3GetJobsForBuildMasterUsingGET(ctx, buildMaster)
Get jobs for build master

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

