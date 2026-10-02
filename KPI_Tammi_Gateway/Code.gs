/**
 * KPI -> Tammi Gateway V2
 * Đọc file KPI_ngay_latest.zip trên Drive và truyền theo từng chunk base64.
 * Triển khai Web app: Execute as Me / Who has access: Anyone.
 */
const KPI_FILE_ID = '1Y4gYhMkSUis12ebwaZOpM2WzvbPDwxAd';
const KPI_TOKEN = 'CBG-KPI-TAMMI-2026';
const CHUNK_BYTES = 180000;

function setupKpiTammiGateway() {
  PropertiesService.getScriptProperties().setProperties({
    FILE_ID: KPI_FILE_ID,
    TOKEN: KPI_TOKEN
  }, true);
  return {ok:true, fileId:KPI_FILE_ID};
}

function doGet(e) {
  try {
    const p = PropertiesService.getScriptProperties();
    const fileId = p.getProperty('FILE_ID') || KPI_FILE_ID;
    const expectedToken = p.getProperty('TOKEN') || KPI_TOKEN;
    const token = String((e && e.parameter && e.parameter.token) || '');

    if (expectedToken && token !== expectedToken) {
      return json_({ok:false,error:'Unauthorized'});
    }

    const action = String((e && e.parameter && e.parameter.action) || 'info');
    const file = DriveApp.getFileById(fileId);

    if (action === 'ping') {
      return json_({
        ok:true,
        service:'KPI-Tammi-Gateway-V2',
        name:file.getName(),
        size:file.getSize(),
        modifiedTime:file.getLastUpdated().toISOString()
      });
    }

    if (action === 'info') {
      const size = file.getSize();
      return json_({
        ok:true,
        mode:'chunked-base64',
        name:file.getName(),
        size:size,
        chunkBytes:CHUNK_BYTES,
        totalChunks:Math.ceil(size / CHUNK_BYTES),
        modifiedTime:file.getLastUpdated().toISOString()
      });
    }

    if (action === 'chunk') {
      const index = Number((e && e.parameter && e.parameter.index) || -1);
      if (!Number.isInteger(index) || index < 0) {
        return json_({ok:false,error:'Invalid chunk index'});
      }
      const bytes = file.getBlob().getBytes();
      const start = index * CHUNK_BYTES;
      if (start >= bytes.length) return json_({ok:false,error:'Chunk index out of range'});
      const end = Math.min(start + CHUNK_BYTES, bytes.length);
      return json_({
        ok:true,
        index:index,
        totalChunks:Math.ceil(bytes.length / CHUNK_BYTES),
        dataBase64:Utilities.base64Encode(bytes.slice(start,end))
      });
    }

    return json_({ok:false,error:'Unknown action'});
  } catch (err) {
    return json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
