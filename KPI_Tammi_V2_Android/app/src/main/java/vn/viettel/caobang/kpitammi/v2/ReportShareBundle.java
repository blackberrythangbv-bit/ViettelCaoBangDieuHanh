package vn.viettel.caobang.kpitammi.v2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts one immutable, fully checked snapshot. Never exposes partial downloads. */
public final class ReportShareBundle {
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public final String day;
    public final Map<String, File> files;
    public final Map<String, String> hashes;

    private ReportShareBundle(String day, Map<String, File> files, Map<String, String> hashes) {
        this.day = day;
        this.files = Collections.unmodifiableMap(files);
        this.hashes = Collections.unmodifiableMap(hashes);
    }

    public static ReportShareBundle extract(File zip, File directory, String day) throws Exception {
        if (directory.exists() || !directory.mkdirs()) throw new Exception("Không tạo được vùng chia sẻ mới");
        Map<String, String> expected = ReportRecipients.expectedFiles(day);
        Map<String, File> result = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                String key = null;
                for (Map.Entry<String, String> candidate : expected.entrySet()) {
                    if (candidate.getValue().equals(name)) key = candidate.getKey();
                }
                if (entry.isDirectory() || key == null || !seen.add(name))
                    throw new Exception("ZIP chứa file sai ngày, sai AM hoặc trùng tên");
                File output = new File(directory, name);
                if (!output.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))
                    throw new Exception("Đường dẫn ZIP không hợp lệ");
                byte[] signature = name.endsWith(".png")
                    ? new byte[]{(byte)137,80,78,71,13,10,26,10} : new byte[]{80,75,3,4};
                byte[] first = new byte[signature.length];
                int got = 0, read;
                while (got < first.length && (read = input.read(first, got, first.length - got)) != -1) got += read;
                if (!Arrays.equals(first, signature) || got != signature.length)
                    throw new Exception("File sai định dạng: " + name);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = first.length;
                total += size;
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    stream.write(first); digest.update(first);
                    while ((read = input.read(buffer)) != -1) {
                        size += read; total += read;
                        if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES)
                            throw new Exception("Bộ báo cáo quá lớn");
                        stream.write(buffer, 0, read); digest.update(buffer, 0, read);
                    }
                }
                input.closeEntry(); // Also verifies the entry CRC.
                result.put(key, output);
                hashes.put(key, hex(digest.digest()));
            }
            if (result.size() != 8 || !result.keySet().equals(expected.keySet()))
                throw new Exception("Bộ báo cáo chưa đủ 8 file");
            return new ReportShareBundle(day, result, hashes);
        } catch (Exception error) {
            delete(directory);
            throw error;
        }
    }

    public static String textHash(String text) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Recipient and payload are bound together so changing either cannot inherit a confirmation. */
    public String itemId(String key, String recipient) throws Exception {
        if (!hashes.containsKey(key)) throw new Exception("Không có file " + key);
        return day + ":" + key + ":" + textHash(recipient) + ":" + hashes.get(key);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format(java.util.Locale.US, "%02x", value & 255));
        return out.toString();
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}

