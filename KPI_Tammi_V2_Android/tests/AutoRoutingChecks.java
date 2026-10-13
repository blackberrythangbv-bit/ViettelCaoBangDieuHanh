import vn.viettel.caobang.kpitammi.v2.AutoRouteMatcher;

public final class AutoRoutingChecks {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(AutoRouteMatcher.exact("CBG Huệ _ AM", " CBG Huệ - AM "));
        check(AutoRouteMatcher.exact("Bế Thị Hoài CBG Hoài - AM", "Bế Thị Hoài CBG Hoài - AM"));
        check(!AutoRouteMatcher.exact("Hoàng Thị Lan", "Lan"));
        check(!AutoRouteMatcher.exact("Nông Phương Thảo CBG Thảo - AM", "Đinh Phương Thảo CBG Thảo - AM"));
        check(!AutoRouteMatcher.exact("CBG - BÁO CÁO NGÀY", "CBG - Kênh SME (DNS)"));
        check(!AutoRouteMatcher.exact("CBG Hue AM", "CBG Huệ AM"));
        check(!AutoRouteMatcher.exact("", ""));
        System.out.println("Auto routing checks passed");
    }
}
