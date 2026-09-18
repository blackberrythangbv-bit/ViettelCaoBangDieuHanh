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

    public static final String DEFAULT_SOURCE_URL =
            "https://script.google.com/macros/s/AKfycbzjwYws7Sx9YNrr67IowsaaaAYaGA3zr8GID1f-p6e5_Wx4qbrmShtBhbbbpyU06chz/exec?token=CBG-KPI-TAMMI-2026";
}
