<%--
  Tag: ui.actions
  Usage: Container for actions of a form. Buttons and links are supplied as the body.
  
  Available variables:
  
  classes - String of classes supplied
  actionsClass - Class to apply to the actions container
  body - The body of the actions container
--%>
<div class="${p.joinClasses(values:[actionsClass, classes])}">
    ${bodyContent}
</div>