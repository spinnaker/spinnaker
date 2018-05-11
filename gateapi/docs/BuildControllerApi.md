# \BuildControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetBuildMastersUsingGET**](BuildControllerApi.md#GetBuildMastersUsingGET) | **Get** /v2/builds | Get build masters
[**GetBuildUsingGET**](BuildControllerApi.md#GetBuildUsingGET) | **Get** /v2/builds/{buildMaster}/build/{number}/** | Get build for build master
[**GetBuildsUsingGET**](BuildControllerApi.md#GetBuildsUsingGET) | **Get** /v2/builds/{buildMaster}/builds/** | Get builds for build master
[**GetJobConfigUsingGET**](BuildControllerApi.md#GetJobConfigUsingGET) | **Get** /v2/builds/{buildMaster}/jobs/** | Get job config
[**GetJobsForBuildMasterUsingGET**](BuildControllerApi.md#GetJobsForBuildMasterUsingGET) | **Get** /v2/builds/{buildMaster}/jobs | Get jobs for build master


# **GetBuildMastersUsingGET**
> []interface{} GetBuildMastersUsingGET(ctx, )
Get build masters

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetBuildUsingGET**
> map[string]interface{} GetBuildUsingGET(ctx, buildMaster, number)
Get build for build master

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **buildMaster** | **string**| buildMaster | 
  **number** | **string**| number | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetBuildsUsingGET**
> []interface{} GetBuildsUsingGET(ctx, buildMaster)
Get builds for build master

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetJobConfigUsingGET**
> map[string]interface{} GetJobConfigUsingGET(ctx, buildMaster)
Get job config

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetJobsForBuildMasterUsingGET**
> []interface{} GetJobsForBuildMasterUsingGET(ctx, buildMaster)
Get jobs for build master

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **buildMaster** | **string**| buildMaster | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

