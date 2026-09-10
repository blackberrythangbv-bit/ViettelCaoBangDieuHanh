# APP 2 -> GOOGLE APPS SCRIPT

Muc tieu: dua App 2 ve cung nen tang Google Apps Script voi App 1, giu nguyen link goc App 1.

## File can dua vao CUNG project Apps Script cua App 1
- App2_Module.gs
- App2_Portal.html
- App2_School.html
- App2_SchoolStyle.html
- App2_SchoolUI.html
- App2_SchoolCore.html
- App2_SchoolEnhance.html
- App2_ReportExport.html

## Tich hop vao doGet(e) hien co
Dat 2 dong sau o DAU ham doGet(e), truoc cac nhanh view hien tai:

```javascript
var v = String((e && e.parameter && e.parameter.view) || '').toLowerCase();
if (v === 'app2' || v === 'school') return renderApp2_(v);
```

Khong xoa/sua logic cu cua cac view KPI, report, plan, login, admin.

## Dieu huong
- App 1 goc: https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec
- App 2 tren cung deployment: ?view=app2
- Truong hoc: ?view=school
- KPI: ?view=kpi
- Bao cao ngay: ?view=report
- KH tuan: ?view=plan

## Bao toan chuc nang
- Dashboard Truong hoc
- Tong quan toan tinh
- Tien do theo AM duoc giao tiep xuc
- Tim/loc truong
- Cap nhat ket qua tiep xuc
- Luu du lieu qua API Apps Script hien co
- Xuat Excel tong hop
- Nut Quay lai tren cung ben trai
- Tiep xuc DN van chi nap khi bam de toi uu toc do

Sau khi them file: Deploy > Manage deployments > Edit > New version > Deploy de GIU NGUYEN URL deployment App 1.
