<#include "../manualJudgment/variables-email.ftl">
<html>
${stageDescription} is awaiting manual judgment.

<#if (notification.additionalContext.instructions)??>
<br/>
<b>Instructions:</b>
${markdownToHtml.convert(notification.additionalContext.instructions)}
</#if>
</html>
