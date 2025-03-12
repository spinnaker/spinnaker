<#assign executionUrl="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}">
<#if (notification.additionalContext.stageId)??>
  <#assign executionUrl="${executionUrl}?refId=${notification.additionalContext.stageId}">
</#if>
<#if (notification.additionalContext.restrictExecutionDuringTimeWindow)??>
  <#assign executionUrl="${executionUrl}&step=1">
</#if>
<#assign stageDescription="Stage <${executionUrl}|${notification.additionalContext.stageName}> for *${notification.source.application}*">
<#if (notification.additionalContext.execution.name)??>
  <#assign stageDescription="${stageDescription}'s *${notification.additionalContext.execution.name}* pipeline">
</#if>
<#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
  <#assign stageDescription="${stageDescription} build #*${notification.additionalContext.execution.trigger.buildInfo.number?c}*">
</#if>
