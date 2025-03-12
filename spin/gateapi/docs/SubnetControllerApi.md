# \SubnetControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllByCloudProviderUsingGET1**](SubnetControllerApi.md#AllByCloudProviderUsingGET1) | **Get** /subnets/{cloudProvider} | Retrieve a list of subnets for a given cloud provider


# **AllByCloudProviderUsingGET1**
> []interface{} AllByCloudProviderUsingGET1(ctx, cloudProvider)
Retrieve a list of subnets for a given cloud provider

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **cloudProvider** | **string**| cloudProvider | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

