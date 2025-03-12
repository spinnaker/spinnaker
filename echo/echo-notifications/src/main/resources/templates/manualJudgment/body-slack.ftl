<#include "../manualJudgment/variables-slack.ftl">
${stageDescription} is awaiting manual judgment.

<#if (notification.additionalContext.instructions)??>
*Instructions:*
${htmlToText.convert(notification.additionalContext.instructions)}
</#if>
