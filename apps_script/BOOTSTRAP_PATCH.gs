/*
PHƯƠNG ÁN B - BOOTSTRAP 1 REQUEST

Chèn nhánh dưới đây vào trong function doGet(e) của file Mã.gs,
ngay sau dòng:
  var action = e && e.parameter ? String(e.parameter.action || '') : '';

Sau đó Lưu -> Triển khai -> Quản lý triển khai -> bút chì -> Phiên bản mới -> Triển khai.

Không xóa các nhánh getTargets/getAmData/getAllAmData hiện có.
*/

// ===== BẮT ĐẦU KHỐI CẦN CHÈN =====
if (action === 'bootstrap') {
  var amCodes = [
    'HOAIBT4',
    'NUONGPM',
    'THAODP7',
    'LANHT22',
    'HUEHT16',
    'QUYENLTN'
  ];

  var allData = {};
  amCodes.forEach(function(amCode) {
    allData[amCode] = readAmSheet_(amCode);
  });

  return ContentService
    .createTextOutput(JSON.stringify({
      ok: true,
      data: {
        all: allData,
        targets: getTargets()
      }
    }))
    .setMimeType(ContentService.MimeType.JSON);
}
// ===== KẾT THÚC KHỐI CẦN CHÈN =====
