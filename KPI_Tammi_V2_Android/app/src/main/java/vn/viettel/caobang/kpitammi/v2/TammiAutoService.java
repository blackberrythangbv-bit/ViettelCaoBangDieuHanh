package vn.viettel.caobang.kpitammi.v2;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** Foreground, explicitly initiated share automation. No coordinates or guessed icon taps. */
public class TammiAutoService extends AccessibilityService {
    static TammiAutoService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String recipient, targetPackage, itemId;
    private int phase;
    private long deadline;
    private long closedSince;
    private boolean active;
    private String observation = "Chưa nhận được màn hình Tammi";
    private boolean focusedSearch;
    private AccessibilityNodeInfo searchIdentity;
    private final Runnable tick = () -> inspect();

    @Override protected void onServiceConnected() { instance = this; }
    static boolean ready() { return instance != null; }
    static boolean running() { return instance != null && instance.active; }
    static void cancel() { if (instance != null && instance.active) instance.stop("Đã dừng. Kiểm tra mục đang gửi trước khi gửi lại.", false); }
    static void begin(String pkg, String name, String id) {
        if (instance == null) throw new IllegalStateException("Chưa bật Trợ năng KPI → Tammi");
        if (instance.active) throw new IllegalStateException("Đang có lượt gửi");
        instance.targetPackage = pkg; instance.recipient = name; instance.itemId = id;
        instance.phase = 0; instance.closedSince = 0; instance.focusedSearch = false; instance.searchIdentity = null; instance.observation = "Chưa nhận được màn hình Tammi"; instance.active = true;
        instance.deadline = SystemClock.elapsedRealtime() + 45000;
        instance.handler.postDelayed(instance.tick, 1000);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!active || !targetPackage.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        handler.removeCallbacks(tick); handler.postDelayed(tick, 350);
    }
    @Override public void onInterrupt() { if (active) stop("Trợ năng bị ngắt; chưa xác minh gửi.", false); }
    @Override public void onDestroy() {
        if (active) stop("Dịch vụ đã dừng; kiểm tra mục đang gửi.", false);
        handler.removeCallbacksAndMessages(null); instance = null; super.onDestroy();
    }
    private void inspect() {
        if (!active) return;
        if (SystemClock.elapsedRealtime() > deadline) {
            stop("Hết 45 giây · bước " + phase + " · " + observation + ". Chưa tự gửi lại.", false); return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            later(); return;
        }
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); collect(root, nodes);
        AccessibilityNodeInfo search = null;
        int editableCount = 0, labelCount = 0;
        for (AccessibilityNodeInfo n : nodes) {
            if (!n.isVisibleToUser()) continue;
            if (n.isEditable() || hasSetText(n)) editableCount++;
            if (!isShareSearch(n)) continue;
            labelCount++;
            AccessibilityNodeInfo input = n;
            // Some custom text fields expose the hint on a child semantics node.
            for (int depth=0; input != null && depth<3; depth++, input=input.getParent()) {
                if (input.isEditable() || hasSetText(input)) break;
            }
            if (input == null || (!input.isEditable() && !hasSetText(input))) continue;
            if (search != null && !search.equals(input)) { stop("Có nhiều ô tìm người nhận; đã dừng.", false); return; }
            search = input;
        }
        if (search == null && phase > 0 && searchIdentity != null) {
            for (AccessibilityNodeInfo n : nodes)
                if (n.equals(searchIdentity) && n.isVisibleToUser() && (n.isEditable() || hasSetText(n))) search = n;
        }
        observation = "Đọc " + nodes.size() + " nút; ô nhập=" + editableCount + "; nhãn tìm người=" + labelCount;
        // The share picker must contain its own search input. Never operate the chat composer.
        if (phase == 0) {
            if (search == null) { later(); return; }
            if (!focusedSearch) {
                search.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                search.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                focusedSearch = true; later(); return;
            }
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, recipient);
            if (!search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                stop("Tammi không cho tự nhập ô tìm kiếm trên máy này.", false); return;
            }
            searchIdentity = AccessibilityNodeInfo.obtain(search);
            phase = 1; later(); return;
        }
        if (phase == 1) {
            if (search == null) { later(); return; }
            List<AccessibilityNodeInfo> matches = new ArrayList<>();
            for (AccessibilityNodeInfo n : nodes)
                if (n.isVisibleToUser() && !n.isEditable() && (AutoRouteMatcher.exact(String.valueOf(n.getText()), recipient) ||
                     AutoRouteMatcher.exact(String.valueOf(n.getContentDescription()), recipient))) matches.add(n);
            if (matches.size() > 1) { stop("Có nhiều kết quả trùng tên: " + recipient, false); return; }
            if (matches.isEmpty()) { later(); return; }
            AccessibilityNodeInfo row = matches.get(0);
            AccessibilityNodeInfo box = null;
            for (int depth = 0; row != null && depth < 4; depth++, row = row.getParent()) {
                List<AccessibilityNodeInfo> children = new ArrayList<>(); collect(row, children);
                List<AccessibilityNodeInfo> boxes = new ArrayList<>();
                for (AccessibilityNodeInfo n : children) if (n.isCheckable()) boxes.add(n);
                if (boxes.size() == 1) { box = boxes.get(0); break; }
                if (boxes.size() > 1) break;
            }
            if (box == null) { stop("Chưa đọc được checkbox của đúng người nhận. Không chọn bằng tọa độ.", false); return; }
            if (box.isChecked()) { stop("Người nhận đã được chọn trước; đã dừng để tránh lẫn lượt.", false); return; }
            if (!box.performAction(AccessibilityNodeInfo.ACTION_CLICK) && !clickAncestor(box)) {
                stop("Không chọn được checkbox người nhận.", false); return;
            }
            phase = 2; later(); return;
        }
        if (phase == 2) {
            if (search == null) { later(); return; }
            int checked = 0; boolean correct = false;
            for (AccessibilityNodeInfo n : nodes) if (n.isCheckable() && n.isChecked()) {
                checked++;
                AccessibilityNodeInfo p = n;
                for (int depth = 0; p != null && depth < 4; depth++, p = p.getParent()) {
                    List<AccessibilityNodeInfo> children = new ArrayList<>(); collect(p, children);
                    for (AccessibilityNodeInfo c : children)
                        if (!c.isEditable() && (AutoRouteMatcher.exact(String.valueOf(c.getText()), recipient) ||
                             AutoRouteMatcher.exact(String.valueOf(c.getContentDescription()), recipient))) correct = true;
                }
            }
            if (checked != 1 || !correct) { stop("Không xác minh được chỉ 1 người nhận đúng tên.", false); return; }
            List<AccessibilityNodeInfo> sends = new ArrayList<>();
            for (AccessibilityNodeInfo n : nodes) {
                String text = String.valueOf(n.getText()), desc = String.valueOf(n.getContentDescription());
                if (n.isVisibleToUser() && n.isEnabled() && n.isClickable() &&
                    (AutoRouteMatcher.exact(text, "Gửi") || AutoRouteMatcher.exact(desc, "Gửi") ||
                     AutoRouteMatcher.exact(desc, "Gửi tin nhắn"))) sends.add(n);
            }
            if (sends.size() != 1) { later(); return; }
            // Persist before the consequential click: never automatically retry an uncertain submission.
            getSharedPreferences("manual_report_send", MODE_PRIVATE).edit().putString(itemId, "auto_pending").commit();
            phase = 3;
            if (!sends.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                stop("Nút Gửi không thực hiện được. Kiểm tra trước khi gửi lại.", false); return;
            }
            deadline = SystemClock.elapsedRealtime() + 30000;
            later(); return;
        }
        if (phase == 3) {
            // Modal closing is submission evidence only, never server delivery acknowledgement.
            if (search == null) {
                if (closedSince == 0) closedSince = SystemClock.elapsedRealtime();
                if (SystemClock.elapsedRealtime() - closedSince >= 2000) {
                    stop("Đã thao tác gửi; Tammi đã đóng bảng chia sẻ. Chưa xác minh máy chủ.", true); return;
                }
                later(); return;
            }
            closedSince = 0;
            for (AccessibilityNodeInfo n : nodes) {
                String t = String.valueOf(n.getText()).toLowerCase(java.util.Locale.ROOT);
                if (t.contains("gửi thất bại") || t.contains("không thể gửi")) {
                    stop("Tammi báo gửi thất bại. Không tự gửi lại.", false); return;
                }
            }
            later();
        }
    }
    private static boolean hasSetText(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo.AccessibilityAction a : n.getActionList())
            if (a.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) return true;
        return false;
    }
    private static boolean isShareSearch(AccessibilityNodeInfo n) {
        CharSequence[] labels = { n.getHintText(), n.getText(), n.getContentDescription() };
        for (CharSequence label : labels)
            if (AutoRouteMatcher.normalize(label == null ? "" : label.toString()).startsWith("tìm kiếm tên, nhóm")) return true;
        return false;
    }
    private void later() { handler.removeCallbacks(tick); handler.postDelayed(tick, 700); }
    private static void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null || out.size() > 1200) return;
        out.add(node); for (int i=0; i<node.getChildCount(); i++) collect(node.getChild(i), out);
    }
    private static boolean clickAncestor(AccessibilityNodeInfo node) {
        for (int i=0; node != null && i<3; i++, node=node.getParent())
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        return false;
    }
    private void stop(String reason, boolean submitted) {
        active = false; handler.removeCallbacks(tick);
        getSharedPreferences("manual_report_send", MODE_PRIVATE).edit()
            .putString(itemId, submitted ? "auto_submitted" : (phase >= 3 ? "auto_uncertain" : "auto_blocked"))
            .putString("auto_reason", reason).commit();
        Intent back = new Intent(this, ManualSendActivity.class);
        back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        back.putExtra("auto_item", itemId); back.putExtra("auto_submitted", submitted);
        back.putExtra("auto_reason", reason);
        startActivity(back);
    }
}
