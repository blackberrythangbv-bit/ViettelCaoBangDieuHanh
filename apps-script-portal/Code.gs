const PORTAL_FILE = 'Portal';
const KPI_FILE = 'Index'; // Nếu HTML KPI hiện tại có tên khác, chỉ sửa dòng này.

function doGet(e) {
  const view = String((e && e.parameter && e.parameter.view) || 'portal').toLowerCase();

  if (view === 'kpi') {
    return HtmlService.createTemplateFromFile(KPI_FILE)
      .evaluate()
      .setTitle('KPI - Viettel Cao Bằng')
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
      .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover');
  }

  return HtmlService.createTemplateFromFile(PORTAL_FILE)
    .evaluate()
    .setTitle('Điều hành Viettel Cao Bằng')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover');
}

function include(filename) {
  return HtmlService.createHtmlOutputFromFile(filename).getContent();
}
