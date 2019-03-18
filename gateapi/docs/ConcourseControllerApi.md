# \ConcourseControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**JobsUsingGET**](ConcourseControllerApi.md#JobsUsingGET) | **Get** /concourse/{buildMaster}/teams/{team}/pipelines/{pipeline}/jobs | Retrieve the list of job names for a given pipeline available to triggers
[**PipelinesUsingGET**](ConcourseControllerApi.md#PipelinesUsingGET) | **Get** /concourse/{buildMaster}/teams/{team}/pipelines | Retrieve the list of pipeline names for a given team available to triggers


# **JobsUsingGET**
> []interface{} JobsUsingGET(ctx, buildMaster, team, pipeline)
Retrieve the list of job names for a given pipeline available to triggers

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **buildMaster** | **string**| buildMaster | 
  **team** | **string**| team | 
  **pipeline** | **string**| pipeline | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PipelinesUsingGET**
> []interface{} PipelinesUsingGET(ctx, buildMaster, team)
Retrieve the list of pipeline names for a given team available to triggers

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **buildMaster** | **string**| buildMaster | 
  **team** | **string**| team | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

