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
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

/**
 * Proses download file yang berjalan di thread terpisah (SwingWorker),
 * supaya tampilan GUI tidak "freeze" dan progress bar bisa bergerak.
 *
 * Pola download sama dengan Modul 2:
 *   URL -> URLConnection -> getInputStream() -> FileOutputStream
 * dengan buffer 4096 byte dan try-with-resources (Modul 1).
 */
public class DownloadWorker extends SwingWorker<File, DownloadWorker.Update> {

    /** Data kemajuan yang dikirim dari thread download ke thread GUI. */
    public static class Update {
        final int percent;      // 0-100, atau -1 jika ukuran file tidak diketahui
        final String status;

        Update(int percent, String status) {
            this.percent = percent;
            this.status = status;
        }
    }

    /** Dipanggil di thread GUI, jadi aman untuk mengubah komponen Swing. */
    public interface Listener {
        void onUpdate(int percent, String status);
        void onDone(File file);
        void onError(Throwable error);
        void onCancelled();
    }

    private static final int BUFFER_SIZE = 4096;

    private final String urlText;
    private final File folder;
    private final Listener listener;

    public DownloadWorker(String urlText, File folder, Listener listener) {
        this.urlText = urlText;
        this.folder = folder;
        this.listener = listener;
    }

    // ------------------------------------------------------------------
    //  Dijalankan di thread terpisah (bukan thread GUI)
    // ------------------------------------------------------------------
    @Override
    @SuppressWarnings("deprecation")
    protected File doInBackground() throws Exception {
        URL url = new URL(urlText);     // MalformedURLException jika format URL salah

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        // Jika HTTP, pastikan server menjawab 200 OK (bukan 404, 403, dst.)
        if (connection instanceof HttpURLConnection) {
            int code = ((HttpURLConnection) connection).getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server menjawab dengan kode HTTP " + code);
            }
        }

        long total = connection.getContentLengthLong();     // -1 jika tidak diketahui

        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Folder tujuan tidak dapat dibuat: " + folder);
        }
        File target = uniqueFile(folder, fileNameFromUrl(url));

        boolean finished = false;
        long totalRead = 0;

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

                if (total > 0) {
                    int percent = (int) (totalRead * 100 / total);
                    if (percent != lastPercent) {       // kirim hanya jika persen berubah
                        lastPercent = percent;
                        publish(new Update(percent, "Mengunduh... "
                                + formatSize(totalRead) + " / " + formatSize(total)));
                    }
                } else {
                    publish(new Update(-1, "Mengunduh... " + formatSize(totalRead)
                            + " (ukuran total tidak diketahui)"));
                }
            }
            out.flush();
            finished = true;
        } finally {
            // Jika gagal atau dibatalkan, jangan tinggalkan file setengah jadi
            if (!finished && target.exists()) {
                target.delete();
            }
        }
        return target;
    }

    // ------------------------------------------------------------------
    //  Dijalankan di thread GUI
    // ------------------------------------------------------------------
    @Override
    protected void process(List<Update> updates) {
        Update last = updates.get(updates.size() - 1);
        listener.onUpdate(last.percent, last.status);
    }

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

    // ------------------------------------------------------------------
    //  Fungsi bantu
    // ------------------------------------------------------------------

    /** Mengambil nama file dari path URL. Contoh: .../a/data.zip -> data.zip */
    private static String fileNameFromUrl(URL url) {
        String path = url.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            name = "download.bin";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** Jika nama sudah ada, tambahkan (1), (2), ... agar file lama tidak tertimpa. */
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
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.2f MB", kb / 1024.0);
    }
}
