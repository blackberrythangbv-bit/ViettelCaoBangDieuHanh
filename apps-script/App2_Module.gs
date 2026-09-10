// APP 2 MODULE - Viettel Cao Bang
// Chen file nay vao cung project Google Apps Script cua App Web 1.

function includeApp2_(name) {
  return HtmlService.createHtmlOutputFromFile(name).getContent();
}

function renderApp2_(view) {
  var map = {
    school: 'App2_School',
    enterprise: 'App2_Enterprise',
    app2: 'App2_Portal'
  };
  var file = map[String(view || 'app2').toLowerCase()] || 'App2_Portal';
  var tpl = HtmlService.createTemplateFromFile(file);
  tpl.appUrl = ScriptApp.getService().getUrl();
  return tpl.evaluate()
    .setTitle('Dieu hanh Viettel Cao Bang')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover');
}

// TICH HOP VAO doGet(e) HIEN CO CUA APP 1:
// Dat 2 dong nay LEN DAU HAM doGet(e), truoc logic cu cua App 1:
// var v = String((e && e.parameter && e.parameter.view) || '').toLowerCase();
// if (v === 'app2' || v === 'school' || v === 'enterprise') return renderApp2_(v);
//
// Cac view cu (kpi, report, plan, login, admin...) giu NGUYEN logic hien tai.
