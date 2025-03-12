<#assign executionUrl="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}">
<#if (notification.additionalContext.stageId)??>
  <#assign executionUrl="${executionUrl}?refId=${notification.additionalContext.stageId}">
</#if>
<#if (notification.additionalContext.restrictExecutionDuringTimeWindow)??>
  <#assign executionUrl="${executionUrl}&step=1">
</#if>
<#assign stageDescription>Stage <a href="${executionUrl}">${notification.additionalContext.stageName}</a> for <b>${notification.source.application}</b></#assign>
<#if (notification.additionalContext.execution.name)??>
  <#assign stageDescription>${stageDescription}'s <b>${notification.additionalContext.execution.name}</b> pipeline</#assign>
</#if>
<#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
  <#assign stageDescription>${stageDescription} build #<b>${notification.additionalContext.execution.trigger.buildInfo.number?c}</b></#assign>
</#if>
