import java.io.File;
import java.net.URL;
import java.util.Locale;

/**
 * Pendeteksi & pengklasifikasi otomatis jenis file: ZIP (.zip) vs Non-ZIP.
 *
 * Deteksi dilakukan secara otomatis tanpa perlu dipilih manual oleh pengguna:
 * 1. Pratinjau awal dari ekstensi URL saat pengguna mengetik/menempel URL.
 * 2. Deteksi dari header HTTP Content-Type & Content-Disposition saat terhubung ke server.
 * 3. Verifikasi akhir menggunakan ZipHelper.isZip(file) (magic number 0x504B0304)
 *    setelah file selesai diunduh. Jika terdeteksi ZIP, ZipHelper akan dijalankan.
 */
public class FileTypeFilter {

    public static final String LABEL_ZIP = "ZIP (.zip)";
    public static final String LABEL_NON_ZIP = "Non-ZIP";

    /**
     * Memeriksa apakah nama file atau Content-Type dari server merupakan file ZIP.
     */
    public static boolean isZipByHeaderOrName(String fileName, String contentType) {
        String ext = getExtension(fileName);
        if ("zip".equals(ext)) {
            return true;
        }
        String mime = normalizeMime(contentType);
        return "application/zip".equals(mime)
                || "application/x-zip-compressed".equals(mime)
                || "application/x-zip".equals(mime)
                || "multipart/x-zip".equals(mime);
    }

    /**
     * Mendeteksi label jenis file ("ZIP (.zip)" atau "Non-ZIP") secara otomatis.
     * Jika objek File sudah ada di disk, prioritas utama adalah ZipHelper.isZip(file)
     * yang memeriksa magic number 4 byte pertama ('P','K',0x03,0x04).
     */
    public static String detectCategoryLabel(String fileName, String contentType, File file) {
        if (file != null && file.exists()) {
            return ZipHelper.isZip(file) ? LABEL_ZIP : LABEL_NON_ZIP;
        }
        return isZipByHeaderOrName(fileName, contentType) ? LABEL_ZIP : LABEL_NON_ZIP;
    }

    /**
     * Mendeteksi jenis file secara instan dari teks URL yang diketik pengguna
     * untuk ditampilkan pada indikator deteksi otomatis di UI.
     */
    @SuppressWarnings("deprecation")
    public static String previewFromUrlText(String urlText) {
        if (urlText == null || urlText.trim().isEmpty()) {
            return "Menunggu input URL...";
        }
        try {
            URL url = new URL(urlText.trim());
            String path = url.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            String ext = getExtension(name);
            if ("zip".equals(ext)) {
                return LABEL_ZIP + " — ZipHelper akan aktif setelah unduhan selesai";
            } else if (!ext.isEmpty()) {
                return LABEL_NON_ZIP + " (." + ext + ") — File disimpan langsung tanpa ekstraksi";
            } else {
                return "Akan dideteksi otomatis dari server & ZipHelper (Magic Number)";
            }
        } catch (Exception e) {
            String lower = urlText.trim().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".zip") || lower.contains(".zip?")) {
                return LABEL_ZIP + " — ZipHelper akan aktif setelah unduhan selesai";
            }
            return "Format URL belum lengkap";
        }
    }

    /**
     * Mengambil ekstensi file dalam huruf kecil tanpa titik (contoh: "Data.ZIP" -> "zip").
     */
    public static String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Membersihkan header Content-Type (contoh: "application/zip; charset=UTF-8" -> "application/zip").
     */
    public static String normalizeMime(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0) ? contentType.substring(0, semi) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Menebak ekstensi populer berdasarkan MIME type jika URL tidak memiliki ekstensi.
     */
    public static String guessExtensionFromMime(String contentType) {
        String mime = normalizeMime(contentType);
        switch (mime) {
            case "application/zip":
            case "application/x-zip-compressed":
            case "application/x-zip": return "zip";
            case "application/pdf": return "pdf";
            case "application/x-rar-compressed":
            case "application/vnd.rar": return "rar";
            case "application/x-7z-compressed": return "7z";
            case "application/gzip": return "gz";
            case "image/jpeg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            case "image/svg+xml": return "svg";
            case "video/mp4": return "mp4";
            case "audio/mpeg": return "mp3";
            case "text/plain": return "txt";
            case "text/csv": return "csv";
            case "text/html": return "html";
            case "application/json": return "json";
            default: return "";
        }
    }
}
