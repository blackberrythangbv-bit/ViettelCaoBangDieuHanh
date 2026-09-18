package vn.viettel.caobang.kpitammi;

public final class AppConfig {
    private AppConfig() {}

    public static final String PREFS = "kpi_tammi";
    public static final String KEY_SOURCE_URL = "source_url";
    public static final String KEY_HOUR = "hour";
    public static final String KEY_MINUTE = "minute";
    public static final String KEY_AUTO_ENABLED = "auto_enabled";

    public static final int DEFAULT_HOUR = 7;
    public static final int DEFAULT_MINUTE = 30;

    // Web App DNS gốc - nguồn sinh Báo cáo ngày hiện tại.
    public static final String DEFAULT_SOURCE_URL =
            "https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec";
}
