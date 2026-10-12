package vn.viettel.caobang.kpitammi.v2;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Exact manual routing labels; the user still selects the recipient in Tammi. */
public final class ReportRecipients {
    public static final String GROUP = "CBG - Kênh SME (DNS)";
    public static final List<Recipient> AMS = Collections.unmodifiableList(Arrays.asList(
        new Recipient("HOAIBT4", "Hoài", "Bế Thị Hoài CBG Hoài - AM"),
        new Recipient("NUONGPM", "Nương", "Phan Mỹ Nương CBG Nương- AM"),
        new Recipient("THAODP7", "Đinh Phương Thảo", "Đinh Phương Thảo CBG Thảo - AM"),
        new Recipient("QUYENLTN", "Quyên", "Lô Thị Ngọc Quyên CBG Quyên - AM"),
        new Recipient("LANHT22", "Lan", "Hoàng Thị Lan"),
        new Recipient("HUEHT16", "Huệ", "CBG Huệ _ AM")
    ));

    private ReportRecipients() {}

    public static String day(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(date);
    }

    public static Map<String, String> expectedFiles(String day) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("GROUP_EXCEL", "Bao_cao_KQ_HoanThanh_KPI_ngay_" + day + ".xlsx");
        files.put("GROUP_TOTAL", "KetQuaKPI_ngay_" + day + ".png");
        for (Recipient am : AMS) files.put(am.code, "ChiTieu_" + day + "_" + am.code + ".png");
        return Collections.unmodifiableMap(files);
    }

    public static final class Recipient {
        public final String code, shortName, tammiName;
        private Recipient(String code, String shortName, String tammiName) {
            this.code = code;
            this.shortName = shortName;
            this.tammiName = tammiName;
        }
    }
}
