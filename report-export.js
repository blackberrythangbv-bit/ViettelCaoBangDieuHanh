
(() => {
  const btn = document.getElementById('exportExcel');
  if (!btn) return;

  function loadScript(url, testFn){
    return new Promise((resolve,reject)=>{
      if(testFn()) return resolve();
      const s=document.createElement('script');
      s.src=url;
      s.onload=()=>testFn()?resolve():reject(new Error('Không tải được thư viện xuất Excel'));
      s.onerror=()=>reject(new Error('Không tải được thư viện xuất Excel'));
      document.head.appendChild(s);
    });
  }

  const pct=(a,b)=>b?Math.round(a*100/b):0;
  const warningRows=rows=>rows.filter(r=>/từ chối|đối thủ/i.test(n(r.ketqua)));
  const considerRows=rows=>rows.filter(r=>/cân nhắc/i.test(n(r.ketqua)));

  function overall(){
    const total=D.length;
    const done=D.filter(r=>st(r)==='done').length;
    const pending=total-done;
    const p1=D.filter(r=>n(r.uutien).includes('1'));
    const p1done=p1.filter(r=>st(r)==='done').length;
    const p1pending=p1.length-p1done;
    const warning=warningRows(D).length;
    const consider=considerRows(D).length;
    const rate=total?done/total:0;

    let level, lead;
    if(rate>=.9){level='Tốt';lead='Tiến độ tiếp xúc toàn tỉnh ở mức tốt, cơ bản hoàn thành kế hoạch tiếp xúc.';}
    else if(rate>=.7){level='Khá';lead='Tiến độ đạt mức khá nhưng vẫn còn khối lượng trường chưa tiếp xúc cần bám sát.';}
    else if(rate>=.5){level='Chậm';lead='Tiến độ tiếp xúc còn chậm, cần tăng tốc triển khai và kiểm soát theo từng AM.';}
    else{level='Cần tăng tốc';lead='Tiến độ tiếp xúc đang thấp, cần tập trung nguồn lực và lịch tiếp xúc cụ thể theo từng AM.';}

    const comment = [
      lead,
      `Đã tiếp xúc ${done}/${total} trường (${pct(done,total)}%), còn ${pending} trường.`,
      `Ưu tiên 1: đã tiếp xúc ${p1done}/${p1.length}, còn ${p1pending} trường.`,
      warning ? `Có ${warning} trường có cảnh báo liên quan đối thủ hoặc từ chối.` : 'Chưa phát sinh cảnh báo đối thủ/từ chối.',
      consider ? `Có ${consider} trường cần thời gian cân nhắc, cần lập lịch tái tiếp xúc.` : 'Không có trường ở trạng thái cần cân nhắc.'
    ].join(' ');

    const action = p1pending>0
      ? `Ưu tiên hoàn thành ${p1pending} trường Ưu tiên 1 chưa tiếp xúc; đồng thời phân rã ${pending} trường còn lại đến từng AM và theo dõi kết quả cập nhật hằng ngày.`
      : `Tập trung hoàn thành ${pending} trường còn lại và chuyển trọng tâm sang xử lý cơ hội, đối thủ và tái tiếp xúc.`;

    return {total,done,pending,p1:p1.length,p1done,p1pending,warning,consider,rate,level,comment,action};
  }

  function byAM(){
    return [...new Set(D.map(am))].sort((a,b)=>a.localeCompare(b,'vi')).map((name,index)=>{
      const rows=D.filter(r=>am(r)===name);
      const total=rows.length;
      const done=rows.filter(r=>st(r)==='done').length;
      const pending=total-done;
      const rate=total?done/total:0;
      const p1=rows.filter(r=>n(r.uutien).includes('1'));
      const p1done=p1.filter(r=>st(r)==='done').length;
      const p1pending=p1.length-p1done;
      const warning=warningRows(rows).length;
      const consider=considerRows(rows).length;

      let level;
      if(rate>=.9) level='Tốt';
      else if(rate>=.7) level='Khá';
      else if(rate>=.5) level='Chậm';
      else level='Cần tăng tốc';

      let review=`Đã tiếp xúc ${done}/${total} trường (${pct(done,total)}%), còn ${pending} trường.`;
      if(p1pending) review+=` Còn ${p1pending} trường Ưu tiên 1 chưa tiếp xúc.`;
      if(warning) review+=` Có ${warning} trường cảnh báo đối thủ/từ chối.`;
      if(consider) review+=` Có ${consider} trường cần tái tiếp xúc.`;

      let action;
      if(pending===0) action='Đã hoàn thành tiếp xúc; tiếp tục chăm sóc, xử lý cơ hội và chuyển đổi dịch vụ.';
      else if(p1pending>0) action=`Ưu tiên xử lý ngay ${p1pending} trường Ưu tiên 1; đồng thời hoàn thành ${pending} trường còn lại.`;
      else if(rate<.5) action=`Tăng tốc tiếp xúc ${pending} trường còn lại; lập lịch triển khai cụ thể và cập nhật kết quả sau từng lần tiếp xúc.`;
      else action=`Tiếp tục bám ${pending} trường chưa tiếp xúc; ưu tiên các trường có đối thủ/cần cân nhắc.`;

      return {index:index+1,name,total,done,pending,rate,p1:p1.length,p1done,p1pending,warning,consider,level,review,action};
    });
  }

  function border(){
    return {
      top:{style:'thin',color:{argb:'FFD9DDE0'}},
      left:{style:'thin',color:{argb:'FFD9DDE0'}},
      bottom:{style:'thin',color:{argb:'FFD9DDE0'}},
      right:{style:'thin',color:{argb:'FFD9DDE0'}}
    };
  }
  function styleHeader(row, color='42494D'){
    row.eachCell(c=>{
      c.font={bold:true,color:{argb:'FFFFFFFF'}};
      c.fill={type:'pattern',pattern:'solid',fgColor:{argb:'FF'+color}};
      c.alignment={horizontal:'center',vertical:'middle',wrapText:true};
      c.border=border();
    });
    row.height=26;
  }
  function styleBody(ws, first, last){
    for(let r=first;r<=last;r++){
      ws.getRow(r).eachCell(c=>{
        c.alignment={vertical:'top',wrapText:true};
        c.border=border();
      });
    }
  }
  function title(ws,text,lastCol){
    ws.mergeCells(1,1,1,lastCol);
    const c=ws.getCell(1,1);
    c.value=text;
    c.font={bold:true,size:16,color:{argb:'FFFFFFFF'}};
    c.fill={type:'pattern',pattern:'solid',fgColor:{argb:'FFE80500'}};
    c.alignment={horizontal:'center',vertical:'middle'};
    ws.getRow(1).height=28;

    ws.mergeCells(2,1,2,lastCol);
    const d=ws.getCell(2,1);
    d.value='Viettel Cao Bằng · Thời điểm xuất: '+new Date().toLocaleString('vi-VN');
    d.font={italic:true,color:{argb:'FF6B7278'}};
    d.alignment={horizontal:'center'};
  }

  async function exportExcel(){
    const old=btn.textContent;
    btn.disabled=true;
    btn.textContent='ĐANG TẠO EXCEL...';
    try{
      await loadScript('https://cdn.jsdelivr.net/npm/exceljs@4.4.0/dist/exceljs.min.js',()=>typeof ExcelJS!=='undefined');
      await loadScript('https://cdnjs.cloudflare.com/ajax/libs/FileSaver.js/2.0.5/FileSaver.min.js',()=>typeof saveAs!=='undefined');

      // Luôn lấy lại dữ liệu mới nhất từ Google Sheet trước khi xuất.
      const fresh=await jp({action:'list'});
      D=(fresh.data||[]).filter(r=>/^\d+$/.test(n(r.stt)));
      render();

      const wb=new ExcelJS.Workbook();
      wb.creator='Viettel Cao Bằng';
      wb.lastModifiedBy='Dashboard Tiếp xúc trường học';
      wb.created=new Date();
      wb.modified=new Date();

      const ov=overall();
      const amRows=byAM();

      // 1. TỔNG HỢP
      const s1=wb.addWorksheet('Tong_hop',{views:[{state:'frozen',ySplit:4}]});
      title(s1,'BÁO CÁO TỔNG HỢP TIẾP XÚC TRƯỜNG HỌC SAU SÁP NHẬP',8);
      s1.getRow(4).values=['STT','Chỉ tiêu','Kết quả','Tỷ lệ','Đánh giá','Nhận xét/Ghi chú','',''];
      styleHeader(s1.getRow(4));
      [
        [1,'Tổng số trường',ov.total,1],
        [2,'Đã tiếp xúc',ov.done,ov.rate],
        [3,'Chưa tiếp xúc',ov.pending,ov.total?ov.pending/ov.total:0],
        [4,'Ưu tiên 1',ov.p1,''],
        [5,'Ưu tiên 1 đã tiếp xúc',ov.p1done,ov.p1?ov.p1done/ov.p1:0],
        [6,'Ưu tiên 1 chưa tiếp xúc',ov.p1pending,ov.p1?ov.p1pending/ov.p1:0],
        [7,'Cảnh báo đối thủ/từ chối',ov.warning,''],
        [8,'Cần thời gian cân nhắc',ov.consider,'']
      ].forEach(v=>{
        const row=s1.addRow(v);
        if(typeof v[3]==='number') row.getCell(4).numFmt='0%';
      });
      styleBody(s1,5,12);

      s1.mergeCells('A14:H14');
      s1.getCell('A14').value='ĐÁNH GIÁ, NHẬN XÉT CHUNG';
      styleHeader(s1.getRow(14),'E80500');

      s1.mergeCells('A15:B15'); s1.getCell('A15').value='Mức đánh giá';
      s1.mergeCells('C15:H15'); s1.getCell('C15').value=ov.level;
      s1.mergeCells('A16:B16'); s1.getCell('A16').value='Nhận xét chung';
      s1.mergeCells('C16:H18'); s1.getCell('C16').value=ov.comment;
      s1.mergeCells('A19:B19'); s1.getCell('A19').value='Đề xuất hành động';
      s1.mergeCells('C19:H21'); s1.getCell('C19').value=ov.action;

      ['A15','A16','A19'].forEach(a=>{
        s1.getCell(a).font={bold:true};
        s1.getCell(a).fill={type:'pattern',pattern:'solid',fgColor:{argb:'FFF1F2F3'}};
        s1.getCell(a).alignment={vertical:'top',wrapText:true};
      });
      ['C16','C19'].forEach(a=>s1.getCell(a).alignment={vertical:'top',wrapText:true});
      s1.getCell('C15').font={bold:true,color:{argb:ov.level==='Tốt'?'FF1BA94C':ov.level==='Khá'?'FF0077C8':ov.level==='Chậm'?'FFFF8A00':'FFE80500'}};
      s1.columns=[{width:7},{width:30},{width:14},{width:14},{width:18},{width:25},{width:25},{width:25}];

      // 2. THEO AM
      const s2=wb.addWorksheet('Theo_AM',{views:[{state:'frozen',ySplit:4}]});
      title(s2,'ĐÁNH GIÁ TIẾN ĐỘ TIẾP XÚC THEO TỪNG AM',13);
      s2.getRow(4).values=['STT','AM phụ trách','Tổng trường','Đã TX','Chưa TX','% HT','ƯT1','ƯT1 đã TX','ƯT1 chưa TX','Cảnh báo','Đánh giá','Nhận xét','Việc cần làm'];
      styleHeader(s2.getRow(4));
      amRows.forEach(a=>{
        const row=s2.addRow([a.index,a.name,a.total,a.done,a.pending,a.rate,a.p1,a.p1done,a.p1pending,a.warning,a.level,a.review,a.action]);
        row.getCell(6).numFmt='0%';
        row.getCell(11).font={bold:true,color:{argb:a.level==='Tốt'?'FF1BA94C':a.level==='Khá'?'FF0077C8':a.level==='Chậm'?'FFFF8A00':'FFE80500'}};
      });
      styleBody(s2,5,4+amRows.length);
      s2.autoFilter={from:'A4',to:'M'+(4+amRows.length)};
      s2.columns=[
        {width:7},{width:24},{width:12},{width:10},{width:10},{width:10},
        {width:9},{width:11},{width:13},{width:10},{width:16},{width:52},{width:55}
      ];

      // 3. CHI TIẾT
      const s3=wb.addWorksheet('Chi_tiet',{views:[{state:'frozen',ySplit:4,xSplit:4}]});
      title(s3,'CHI TIẾT KẾT QUẢ TIẾP XÚC 150 TRƯỜNG HỌC',21);
      s3.getRow(4).values=[
        'STT','Xã/Phường','Cấp học','Tên trường','Mức ưu tiên','GIAO AM','AM phụ trách','AM theo dõi',
        'Trạng thái','Ngày tiếp xúc','Người đại diện','Chức vụ','SĐT','Dùng DV Viettel',
        'Dịch vụ Viettel đang sử dụng','Đối thủ/NCC khác','Nhu cầu/Vấn đề ghi nhận',
        'Giải pháp đề xuất','Kết quả tiếp xúc','Ghi chú','Cập nhật lúc'
      ];
      styleHeader(s3.getRow(4));
      [...D].sort((a,b)=>Number(a.stt)-Number(b.stt)).forEach(r=>{
        s3.addRow([
          r.stt,r.xa,r.cap,r.truong,r.uutien,r.giaoam||'',r.am||'',am(r),
          st(r)==='done'?'Đã tiếp xúc':'Chưa tiếp xúc',r.ngay||'',r.nguoi||'',r.chucvu||'',r.sdt||'',
          r.dasudung||'',r.dichvu||'',r.ncc||'',r.nhucau||'',r.giaiphap||'',r.ketqua||'',r.ghichu||'',r.capnhatluc||''
        ]);
      });
      styleBody(s3,5,4+D.length);
      s3.autoFilter={from:'A4',to:'U'+(4+D.length)};
      s3.columns=[
        {width:7},{width:20},{width:16},{width:38},{width:28},{width:22},{width:22},{width:22},
        {width:15},{width:14},{width:22},{width:18},{width:16},{width:18},{width:28},{width:22},
        {width:42},{width:42},{width:30},{width:35},{width:20}
      ];

      for(let i=5;i<=4+D.length;i++){
        const status=n(s3.getCell(i,9).value);
        const result=n(s3.getCell(i,19).value);
        s3.getCell(i,9).fill={type:'pattern',pattern:'solid',fgColor:{argb:status==='Đã tiếp xúc'?'FFE6F7ED':'FFFFF3E0'}};
        if(/từ chối|đối thủ/i.test(result)){
          s3.getCell(i,19).fill={type:'pattern',pattern:'solid',fgColor:{argb:'FFFDEAEA'}};
        }
      }

      const buffer=await wb.xlsx.writeBuffer();
      const d=new Date(),p=x=>String(x).padStart(2,'0');
      const filename=`Bao_cao_tong_hop_tiep_xuc_truong_hoc_CBG_${d.getFullYear()}${p(d.getMonth()+1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}.xlsx`;
      saveAs(new Blob([buffer],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}),filename);
    }catch(err){
      console.error(err);
      alert('Không xuất được Excel: '+err.message);
    }finally{
      btn.disabled=false;
      btn.textContent=old;
    }
  }

  btn.addEventListener('click',exportExcel);
})();
