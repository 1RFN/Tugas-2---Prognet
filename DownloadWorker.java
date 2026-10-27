import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

/** Pekerja background (SwingWorker) untuk mengunduh file dan mendeteksi jenis file tanpa membekukan GUI. */
public class DownloadWorker extends SwingWorker<File, DownloadWorker.Update> {

    /** Data kemajuan unduhan yang dikirim dari thread background ke thread GUI. */
    public static class Update {
        final int percent;
        final String status;
        final String sizeInfo;
        final String speedInfo;
        final String fileTypeInfo;

        Update(int percent, String status, String sizeInfo, String speedInfo, String fileTypeInfo) {
            this.percent = percent;
            this.status = status;
            this.sizeInfo = sizeInfo;
            this.speedInfo = speedInfo;
            this.fileTypeInfo = fileTypeInfo;
        }
    }

    /** Antarmuka callback yang dipanggil di thread GUI untuk memperbarui tampilan. */
    public interface Listener {
        void onUpdate(int percent, String status);
        default void onDetailedUpdate(Update update) {
            onUpdate(update.percent, update.status);
        }
        void onDone(File file);
        void onError(Throwable error);
        void onCancelled();
    }

    private static final int BUFFER_SIZE = 4096;

    private final String urlText;
    private final File folder;
    private final Listener listener;

    private volatile String detectedFileName = "-";
    private volatile String detectedContentType = "-";
    private volatile String detectedCategory = "-";
    private volatile long totalBytesRead = 0;
    private volatile String averageSpeedText = "-";

    public DownloadWorker(String urlText, File folder, Listener listener) {
        this.urlText = urlText;
        this.folder = folder;
        this.listener = listener;
    }

    public String getDetectedFileName() {
        return detectedFileName;
    }

    public String getDetectedContentType() {
        return detectedContentType;
    }

    public String getDetectedCategory() {
        return detectedCategory;
    }

    public long getTotalBytesRead() {
        return totalBytesRead;
    }

    public String getAverageSpeedText() {
        return averageSpeedText;
    }

    // Menjalankan proses pengunduhan file di thread terpisah.
    @Override
    @SuppressWarnings("deprecation")
    protected File doInBackground() throws Exception {
        URL url = new URL(urlText);

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        // Pastikan server HTTP mengembalikan kode status 200 OK.
        if (connection instanceof HttpURLConnection) {
            int code = ((HttpURLConnection) connection).getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server menjawab dengan kode HTTP " + code);
            }
        }

        String rawContentType = connection.getContentType();
        String cleanMime = FileTypeFilter.normalizeMime(rawContentType);
        detectedContentType = cleanMime.isEmpty() ? "application/octet-stream" : cleanMime;

        String resolvedName = resolveFileName(url, connection, detectedContentType);
        detectedFileName = resolvedName;
        detectedCategory = FileTypeFilter.detectCategoryLabel(resolvedName, detectedContentType, null);

        long total = connection.getContentLengthLong();

        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Folder tujuan tidak dapat dibuat: " + folder);
        }
        File target = uniqueFile(folder, resolvedName);
        detectedFileName = target.getName();

        boolean finished = false;
        long totalRead = 0;
        long startTimeNs = System.nanoTime();
        long lastPublishMs = 0;

        String typeDisplay = detectedCategory + " (" + detectedContentType + ")";

        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            int lastPercent = -2;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (isCancelled()) {
                    throw new IOException("Download dibatalkan");
                }
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                totalBytesRead = totalRead;

                long nowMs = System.currentTimeMillis();
                double elapsedSec = Math.max(0.001, (System.nanoTime() - startTimeNs) / 1_000_000_000.0);
                long speedBps = (long) (totalRead / elapsedSec);

                if (total > 0) {
                    int percent = (int) Math.min(100, (totalRead * 100 / total));
                    if (percent != lastPercent || (nowMs - lastPublishMs) >= 200) {
                        lastPercent = percent;
                        lastPublishMs = nowMs;
                        String sizeStr = formatSize(totalRead) + " / " + formatSize(total);
                        String speedStr = formatSpeed(speedBps) + formatEta(total - totalRead, speedBps);
                        String statusStr = "Mengunduh " + target.getName() + " (" + percent + "%)";
                        publish(new Update(percent, statusStr, sizeStr, speedStr, typeDisplay));
                    }
                } else {
                    if ((nowMs - lastPublishMs) >= 150) {
                        lastPublishMs = nowMs;
                        String sizeStr = formatSize(totalRead) + " / Tidak diketahui";
                        String speedStr = formatSpeed(speedBps);
                        String statusStr = "Mengunduh " + target.getName() + "...";
                        publish(new Update(-1, statusStr, sizeStr, speedStr, typeDisplay));
                    }
                }
            }
            out.flush();
            finished = true;

            double totalElapsedSec = Math.max(0.05, (System.nanoTime() - startTimeNs) / 1_000_000_000.0);
            averageSpeedText = formatSpeed((long) (totalRead / totalElapsedSec));
        } finally {
            // Hapus file jika proses unduhan gagal atau dibatalkan.
            if (!finished && target.exists()) {
                target.delete();
            }
        }

        // Ubah ekstensi .bin menjadi .zip jika magic number file terdeteksi sebagai ZIP oleh ZipHelper.
        if (ZipHelper.isZip(target) && target.getName().endsWith(".bin")) {
            String baseName = target.getName().substring(0, target.getName().length() - 4);
            File renamedZip = uniqueFile(folder, baseName + ".zip");
            if (target.renameTo(renamedZip)) {
                target = renamedZip;
                detectedFileName = target.getName();
            }
        }

        detectedCategory = FileTypeFilter.detectCategoryLabel(target.getName(), detectedContentType, target);
        return target;
    }

    // Meneruskan pembaruan progres terakhir ke komponen GUI.
    @Override
    protected void process(List<Update> updates) {
        Update last = updates.get(updates.size() - 1);
        listener.onDetailedUpdate(last);
    }

    // Menangani penyelesaian, pembatalan, atau error setelah proses background berakhir.
    @Override
    protected void done() {
        try {
            listener.onDone(get());
        } catch (CancellationException e) {
            listener.onCancelled();
        } catch (ExecutionException e) {
            listener.onError(e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onCancelled();
        }
    }

    /** Menentukan nama file dari header Content-Disposition atau dari path URL. */
    private static String resolveFileName(URL url, URLConnection connection, String cleanMime) {
        String disposition = connection.getHeaderField("Content-Disposition");
        if (disposition != null) {
            String fromHeader = parseContentDispositionFileName(disposition);
            if (!fromHeader.isEmpty()) {
                return sanitizeFileName(fromHeader, cleanMime);
            }
        }
        return sanitizeFileName(fileNameFromUrl(url), cleanMime);
    }

    private static String parseContentDispositionFileName(String header) {
        String lower = header.toLowerCase();
        int idxStar = lower.indexOf("filename*=");
        if (idxStar >= 0) {
            String value = header.substring(idxStar + 10).trim();
            int semi = value.indexOf(';');
            if (semi >= 0) {
                value = value.substring(0, semi).trim();
            }
            int quoteIdx = value.indexOf("''");
            if (quoteIdx >= 0) {
                value = value.substring(quoteIdx + 2);
            }
            try {
                return URLDecoder.decode(value.replace("\"", ""), StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
                return value.replace("\"", "");
            }
        }
        int idx = lower.indexOf("filename=");
        if (idx >= 0) {
            String value = header.substring(idx + 9).trim();
            int semi = value.indexOf(';');
            if (semi >= 0) {
                value = value.substring(0, semi).trim();
            }
            return value.replace("\"", "").trim();
        }
        return "";
    }

    /** Mengambil nama file dari bagian akhir path URL. */
    private static String fileNameFromUrl(URL url) {
        String path = url.getPath();
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            name = "download";
        }
        return name;
    }

    private static String sanitizeFileName(String rawName, String cleanMime) {
        String clean = rawName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) {
            clean = "download";
        }
        String ext = FileTypeFilter.getExtension(clean);
        if (ext.isEmpty()) {
            String guessed = FileTypeFilter.guessExtensionFromMime(cleanMime);
            if (!guessed.isEmpty()) {
                clean = clean + "." + guessed;
            } else {
                clean = clean + ".bin";
            }
        }
        return clean;
    }

    /** Menambahkan nomor urut (1), (2), dst. jika nama file sudah ada di folder tujuan. */
    static File uniqueFile(File folder, String name) {
        File f = new File(folder, name);
        if (!f.exists()) {
            return f;
        }
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        String ext = (dot > 0) ? name.substring(dot) : "";
        int i = 1;
        while (f.exists()) {
            f = new File(folder, base + " (" + i + ")" + ext);
            i++;
        }
        return f;
    }

    static String formatSize(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) {
            return "0 B/dtk";
        }
        return formatSize(bytesPerSec) + "/dtk";
    }

    private static String formatEta(long remainingBytes, long speedBps) {
        if (remainingBytes <= 0 || speedBps <= 0) {
            return "";
        }
        long sec = remainingBytes / speedBps;
        if (sec < 1) {
            return " • <1 dtk";
        }
        if (sec < 60) {
            return " • ~" + sec + " dtk";
        }
        long min = sec / 60;
        long remSec = sec % 60;
        return " • ~" + min + "m " + remSec + "d";
    }
}
