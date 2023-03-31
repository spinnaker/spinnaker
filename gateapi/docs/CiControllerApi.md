# \CiControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetBuildOutputByIdUsingGET**](CiControllerApi.md#GetBuildOutputByIdUsingGET) | **Get** /ci/builds/{buildId}/output | getBuildOutputById
[**GetBuildsUsingGET1**](CiControllerApi.md#GetBuildsUsingGET1) | **Get** /ci/builds | getBuilds


# **GetBuildOutputByIdUsingGET**
> interface{} GetBuildOutputByIdUsingGET(ctx, buildId)
getBuildOutputById

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **buildId** | **string**| buildId | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetBuildsUsingGET1**
> []Mapstringobject GetBuildsUsingGET1(ctx, optional)
getBuilds

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***CiControllerApiGetBuildsUsingGET1Opts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CiControllerApiGetBuildsUsingGET1Opts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **buildNumber** | **optional.String**| buildNumber | 
 **commitId** | **optional.String**| commitId | 
 **completionStatus** | **optional.String**| completionStatus | 
 **projectKey** | **optional.String**| projectKey | 
 **repoSlug** | **optional.String**| repoSlug | 

### Return type

[**[]Mapstringobject**](Map«string,object».md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

