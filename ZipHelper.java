import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Kelas bantu untuk mendeteksi dan mengekstrak file ZIP. */
public class ZipHelper {

    private static final int BUFFER_SIZE = 4096;

    // Empat byte pertama signature file ZIP biasa dan ZIP kosong.
    private static final int ZIP_SIGNATURE = 0x504B0304;
    private static final int ZIP_EMPTY_SIGNATURE = 0x504B0506;

    /** Memeriksa apakah file adalah ZIP dengan membaca 4 byte pertamanya (magic number). */
    public static boolean isZip(File file) {
        if (file == null || !file.isFile() || file.length() < 4) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            int signature = in.readInt();
            return signature == ZIP_SIGNATURE || signature == ZIP_EMPTY_SIGNATURE;
        } catch (IOException e) {
            return false;
        }
    }

    /** Mengekstrak isi file ZIP ke folder baru di samping file ZIP tersebut. */
    public static File extract(File zipFile) throws IOException {
        String name = zipFile.getName();
        int dot = name.lastIndexOf('.');
        String folderName = (dot > 0) ? name.substring(0, dot) : name + "_extracted";

        File destDir = DownloadWorker.uniqueFile(zipFile.getAbsoluteFile().getParentFile(), folderName);
        if (!destDir.mkdirs()) {
            throw new IOException("Folder ekstraksi tidak dapat dibuat: " + destDir);
        }
        String destPath = destDir.getCanonicalPath() + File.separator;

        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());

                // Cegah Zip Slip agar entry tidak keluar dari folder tujuan.
                if (!outFile.getCanonicalPath().startsWith(destPath)) {
                    throw new IOException("Entry ZIP tidak aman: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (OutputStream out =
                                 new BufferedOutputStream(new FileOutputStream(outFile))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = zin.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zin.closeEntry();
            }
        }
        return destDir;
    }
}
