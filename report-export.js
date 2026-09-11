(() => {
  const btn = document.getElementById('exportExcel');
  if (!btn) return;

  function esc(v){
    return String(v ?? '').replace(/[&<>"']/g, c => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'
    }[c]));
  }
  function cell(v, style){
    const isNum = typeof v === 'number' && isFinite(v);
    return `<Cell${style ? ` ss:StyleID="${style}"` : ''}><Data ss:Type="${isNum ? 'Number' : 'String'}">${esc(v)}</Data></Cell>`;
  }
  function sheet(name, heads, rows){
    let x = `<Worksheet ss:Name="${esc(name)}"><Table>`;
    x += '<Row>' + heads.map(h => cell(h,'hdr')).join('') + '</Row>';
    for (const row of rows) x += '<Row>' + row.map(v => cell(v)).join('') + '</Row>';
    return x + '</Table></Worksheet>';
  }
  function download(blob,name){
    const url=URL.createObjectURL(blob), a=document.createElement('a');
    a.href=url; a.download=name; document.body.appendChild(a); a.click();
    setTimeout(()=>{URL.revokeObjectURL(url);a.remove()},1200);
  }

  function fastExport(){
    const old=btn.textContent;
    btn.disabled=true;
    btn.textContent='ĐANG TẠO...';
    const t0=performance.now();
    try{
      if (!Array.isArray(window.D)) throw new Error('Chưa có dữ liệu trường học');
      const rows = window.D;
      const norm = v => String(v ?? '').trim();
      const status = r => norm(r.ngay) ? 'done' : 'pending';
      const amName = r => norm(r.am) || norm(r.giaoam) || 'Chưa giao AM';

      let done=0,p1=0,p1done=0,warn=0;
      const by={};
      for(const r of rows){
        const ok=status(r)==='done', a=amName(r), isP1=norm(r.uutien).includes('1');
        if(ok)done++;
        if(isP1){p1++; if(ok)p1done++;}
        if(/từ chối|đối thủ/i.test(norm(r.ketqua)))warn++;
        const z=by[a]||(by[a]={am:a,total:0,done:0,p1:0,p1done:0,warn:0});
        z.total++; if(ok)z.done++;
        if(isP1){z.p1++; if(ok)z.p1done++;}
        if(/từ chối|đối thủ/i.test(norm(r.ketqua)))z.warn++;
      }

      const total=rows.length;
      const sumHeads=['Chỉ tiêu','Kết quả','Tỷ lệ'];
      const sumRows=[
        ['Tổng số trường',total,1],
        ['Đã tiếp xúc',done,total?done/total:0],
        ['Chưa tiếp xúc',total-done,total?(total-done)/total:0],
        ['Ưu tiên 1',p1,''],
        ['Ưu tiên 1 đã tiếp xúc',p1done,p1?p1done/p1:0],
        ['Ưu tiên 1 chưa tiếp xúc',p1-p1done,p1?(p1-p1done)/p1:0],
        ['Cảnh báo đối thủ/từ chối',warn,'']
      ];
      const amHeads=['STT','AM phụ trách','Tổng trường','Đã TX','Chưa TX','% HT','ƯT1','ƯT1 đã TX','Cảnh báo'];
      const amRows=Object.values(by).sort((a,b)=>a.am.localeCompare(b.am,'vi')).map((x,i)=>[
        i+1,x.am,x.total,x.done,x.total-x.done,x.total?x.done/x.total:0,x.p1,x.p1done,x.warn
      ]);
      const detailHeads=['STT','Xã/Phường','Cấp học','Tên trường','Mức ưu tiên','GIAO AM','AM phụ trách','Ngày tiếp xúc','Người đại diện','Chức vụ','SĐT','Dùng DV Viettel','Dịch vụ Viettel đang sử dụng','Đối thủ/NCC khác','Nhu cầu/Vấn đề ghi nhận','Giải pháp đề xuất','Kết quả tiếp xúc','Ghi chú'];
      const detailRows=[...rows].sort((a,b)=>Number(a.stt)-Number(b.stt)).map(r=>[
        r.stt,r.xa,r.cap,r.truong,r.uutien,r.giaoam,r.am,r.ngay,r.nguoi,r.chucvu,r.sdt,r.dasudung,r.dichvu,r.ncc,r.nhucau,r.giaiphap,r.ketqua,r.ghichu
      ]);

      const xml=`<?xml version="1.0"?>\n<?mso-application progid="Excel.Sheet"?>\n<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">\n<Styles><Style ss:ID="hdr"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#505050" ss:Pattern="Solid"/><Alignment ss:Vertical="Center" ss:WrapText="1"/></Style></Styles>\n${sheet('Tong_hop',sumHeads,sumRows)}\n${sheet('Theo_AM',amHeads,amRows)}\n${sheet('Chi_tiet',detailHeads,detailRows)}\n</Workbook>`;
      const blob=new Blob(['\ufeff',xml],{type:'application/vnd.ms-excel;charset=utf-8'});
      const d=new Date(), stamp=`${String(d.getDate()).padStart(2,'0')}-${String(d.getMonth()+1).padStart(2,'0')}-${d.getFullYear()}`;
      download(blob,`Bao_cao_tiep_xuc_Truong_hoc_${stamp}.xls`);
      const speed=document.getElementById('speed');
      if(speed) speed.textContent=`Xuất Excel tại máy trong ${Math.round(performance.now()-t0)} ms`;
    }catch(e){
      alert('Không xuất được Excel: '+(e?.message||String(e)));
    }finally{
      btn.disabled=false;
      btn.textContent=old;
    }
  }

  // Ghi đè handler cũ để tránh chạy code legacy phụ thuộc render()/ExcelJS/server.
  btn.onclick = fastExport;
})();
