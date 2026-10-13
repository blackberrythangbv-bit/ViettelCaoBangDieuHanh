import vn.viettel.caobang.kpitammi.v2.ReportRecipients;
import vn.viettel.caobang.kpitammi.v2.ReportShareBundle;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

public class ManualReportChecks {
    static final String DAY = "12-10-2026";
    static File root;
    static int passed;
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); passed++; }
    static File zip(Map<String,String> names, String corrupt, int revision) throws Exception {
        File file = Files.createTempFile(root.toPath(),"reports",".zip").toFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (String name : names.values()) {
                out.putNextEntry(new ZipEntry(name));
                out.write(name.equals(corrupt) ? new byte[]{0,0,0,0} : name.endsWith(".png") ? new byte[]{(byte)137,80,78,71,13,10,26,10} : new byte[]{80,75,3,4});
                out.write(revision); out.closeEntry();
            }
        }
        return file;
    }
    static File target() { return new File(root,UUID.randomUUID().toString()); }
    static void reject(Map<String,String> names, String corrupt, String label) throws Exception {
        File dest=target(); boolean rejected=false;
        try { ReportShareBundle.extract(zip(names,corrupt,0),dest,DAY); } catch (Exception e) { rejected=true; }
        check(rejected,label); check(!dest.exists(),label+" removes partial files");
    }
    public static void main(String[] args) throws Exception {
        root=Files.createTempDirectory("manual-report-checks").toFile();
        Map<String,String> names=ReportRecipients.expectedFiles(DAY);
        ReportShareBundle a=ReportShareBundle.extract(zip(names,null,0),target(),DAY);
        check(a.files.size()==8,"exact eight files");
        for (String key:names.keySet()) check(a.files.get(key).getName().equals(names.get(key)),"route "+key);
        check(ReportRecipients.AMS.stream().filter(r->r.code.equals("THAODP7")).findFirst().get().tammiName.equals("Đinh Phương Thảo CBG Thảo - AM"),"correct Thao");
        check(ReportRecipients.AMS.stream().filter(r->r.code.equals("QUYENLTN")).findFirst().get().tammiName.equals("Lô Thị Ngọc Quyên CBG Quyên - AM"),"exact Quyen");
        check(ReportRecipients.day(Date.from(Instant.parse("2026-10-11T17:01:00Z"))).equals(DAY),"Vietnam day boundary");
        check(!a.itemId("HOAIBT4","Hoài").equals(a.itemId("HOAIBT4","Nương")),"recipient change invalidates confirmation");
        ReportShareBundle b=ReportShareBundle.extract(zip(names,null,1),target(),DAY);
        check(!a.itemId("HOAIBT4","Hoài").equals(b.itemId("HOAIBT4","Hoài")),"payload change invalidates confirmation");
        ReportShareBundle c=ReportShareBundle.extract(zip(names,null,0),target(),DAY);
        check(a.itemId("HOAIBT4","Hoài").equals(c.itemId("HOAIBT4","Hoài")),"same payload preserves confirmation");
        Map<String,String> changed=new LinkedHashMap<>(names); changed.remove("NUONGPM"); reject(changed,null,"missing recipient");
        changed=new LinkedHashMap<>(names); changed.put("NUONGPM","../escape.png"); reject(changed,null,"path traversal");
        changed=new LinkedHashMap<>(names); changed.put("NUONGPM",names.get("NUONGPM").replace(DAY,"11-10-2026")); reject(changed,null,"wrong date");
        changed=new LinkedHashMap<>(names); changed.put("NUONGPM",names.get("NUONGPM").replace("NUONGPM","UNKNOWN")); reject(changed,null,"unknown recipient");
        reject(names,names.get("GROUP_TOTAL"),"invalid PNG signature");
        reject(names,names.get("GROUP_EXCEL"),"invalid XLSX signature");
        System.out.println("PASS: "+passed+" manual report checks");
    }
}

