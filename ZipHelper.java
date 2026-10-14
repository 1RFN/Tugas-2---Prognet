import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pengecekan dan ekstraksi file ZIP.
 */
public class ZipHelper {

    private static final int BUFFER_SIZE = 4096;

    // Empat byte pertama file ZIP: 'P' 'K' 0x03 0x04 (ZIP biasa)
    // atau 'P' 'K' 0x05 0x06 (ZIP kosong)
    private static final int ZIP_SIGNATURE = 0x504B0304;
    private static final int ZIP_EMPTY_SIGNATURE = 0x504B0506;

    /**
     * Memeriksa apakah file adalah ZIP dengan membaca 4 byte pertamanya
     * (magic number), bukan hanya melihat ekstensi.
     * Memakai DataInputStream (Modul 2): readInt() membaca 4 byte sekaligus.
     */
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

    /**
     * Mengekstrak ZIP ke folder baru di samping file ZIP-nya.
     * Contoh: data.zip -> folder "data".
     *
     * @return folder hasil ekstraksi
     */
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

                // Cegah "Zip Slip": entry bernama ../../x tidak boleh keluar dari folder tujuan
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
