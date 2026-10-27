import java.io.File;
import java.util.Locale;

/** Kelas bantu untuk mendeteksi secara otomatis jenis file ZIP (.zip) atau Non-ZIP. */
public class FileTypeFilter {

    public static final String LABEL_ZIP = "ZIP (.zip)";
    public static final String LABEL_NON_ZIP = "Non-ZIP";

    /** Memeriksa apakah nama file atau header Content-Type menunjukkan file ZIP. */
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

    /** Mendeteksi label jenis file ("ZIP (.zip)" atau "Non-ZIP") menggunakan ZipHelper atau header. */
    public static String detectCategoryLabel(String fileName, String contentType, File file) {
        if (file != null && file.exists()) {
            return ZipHelper.isZip(file) ? LABEL_ZIP : LABEL_NON_ZIP;
        }
        return isZipByHeaderOrName(fileName, contentType) ? LABEL_ZIP : LABEL_NON_ZIP;
    }

    /** Mengambil ekstensi file dalam huruf kecil tanpa tanda titik. */
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

    /** Membersihkan nilai header Content-Type dari parameter tambahan seperti charset. */
    public static String normalizeMime(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0) ? contentType.substring(0, semi) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** Menebak ekstensi file berdasarkan MIME type jika URL tidak memiliki ekstensi. */
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
