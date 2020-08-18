# \CiControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetBuildsUsingGET1**](CiControllerApi.md#GetBuildsUsingGET1) | **Get** /ci/builds | getBuilds


# **GetBuildsUsingGET1**
> []interface{} GetBuildsUsingGET1(ctx, projectKey, repoSlug, optional)
getBuilds

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **projectKey** | **string**| projectKey | 
  **repoSlug** | **string**| repoSlug | 
 **optional** | ***CiControllerApiGetBuildsUsingGET1Opts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CiControllerApiGetBuildsUsingGET1Opts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **completionStatus** | **optional.String**| completionStatus | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

