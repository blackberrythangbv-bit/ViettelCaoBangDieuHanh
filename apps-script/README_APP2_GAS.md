# APP 2 -> CUNG NEN TANG GOOGLE APPS SCRIPT VOI KPI

Muc tieu: dua Trường học + Doanh nghiệp ve cung mot deployment Google Apps Script voi app KPI, giu nguyen logic KPI/report/plan/login/admin. Man hinh chung mac dinh mo KPI; cac module khac chi nap khi bam de toi uu toc do.

## File can dua vao CUNG project Apps Script cua app KPI
- App2_Module.gs
- App2_Portal.html
- App2_School.html
- App2_SchoolStyle.html
- App2_SchoolUI.html
- App2_SchoolCore.html
- App2_SchoolEnhance.html
- App2_ReportExport.html
- App2_Enterprise.html

## Tich hop vao doGet(e) hien co
Dat 2 dong sau o DAU ham doGet(e), truoc cac nhanh view hien tai:

```javascript
var v = String((e && e.parameter && e.parameter.view) || '').toLowerCase();
if (v === 'app2' || v === 'school' || v === 'enterprise') return renderApp2_(v);
```

Khong xoa/sua logic cu cua cac view KPI, report, plan, login, admin.

## Dieu huong tren CUNG deployment
- Man hinh chung: ?view=app2
- KPI: ?view=kpi
- Bao cao ngay: ?view=report
- KH tuan: ?view=plan
- Truong hoc: ?view=school
- Doanh nghiep: ?view=enterprise

## Nguyen tac toc do
- Khi mo ?view=app2 chi nap KPI.
- Bao cao ngay/KH tuan/Truong hoc/Doanh nghiep khong nap du lieu cho den khi nguoi dung bam tab.
- Chuyen tab khong tai lai module da mo; nut tai lai chi tai lai tab dang dung.

## Bao toan chuc nang
- KPI mac dinh khi mo app.
- Bao cao ngay, KH tuan giu logic cu.
- Dashboard Truong hoc, tong quan toan tinh, tien do theo AM, tim/loc, cap nhat ket qua, xuat Excel.
- Doanh nghiep co endpoint cung deployment (?view=enterprise); hien tai giao dien DN cu duoc nap ben trong endpoint nay de khong lam mat chuc nang trong qua trinh hop nhat.
- Login/admin cua app KPI khong bi xoa hay thay doi.

## Deploy de GIU NGUYEN URL app KPI
Sau khi them/cap nhat cac file: Deploy > Manage deployments > Edit > New version > Deploy. Khong tao deployment moi.

Deployment app KPI hien dung:
https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec

Sau deploy, dung URL:
https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec?view=app2
