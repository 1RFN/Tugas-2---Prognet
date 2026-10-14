import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Tampilan (GUI) aplikasi Download File.
 *
 * Fitur: input URL, tombol Download, progress bar, pop-up selesai,
 * deteksi file ZIP, dan pilihan ekstraksi.
 */
public class MainFrame extends JFrame {

    private final JTextField urlField = new JTextField(30);
    private final JTextField folderField = new JTextField(30);
    private final JButton browseButton = new JButton("Pilih...");
    private final JButton downloadButton = new JButton("Download");
    private final JButton cancelButton = new JButton("Batal");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Masukkan URL file lalu klik Download.");

    private DownloadWorker worker;

    public MainFrame() {
        super("Aplikasi Download File");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Folder tujuan bawaan: ~/Downloads (atau folder home jika tidak ada)
        File home = new File(System.getProperty("user.home"));
        File downloads = new File(home, "Downloads");
        folderField.setText((downloads.isDirectory() ? downloads : home).getAbsolutePath());

        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 24));
        cancelButton.setEnabled(false);

        buildLayout();
        addListeners();

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    //  Susunan tampilan
    // ------------------------------------------------------------------
    private void buildLayout() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Baris 1: URL
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        form.add(new JLabel("URL File:"), c);
        c.gridx = 1; c.weightx = 1; c.gridwidth = 2;
        form.add(urlField, c);

        // Baris 2: folder tujuan
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.gridwidth = 1;
        form.add(new JLabel("Simpan ke:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(folderField, c);
        c.gridx = 2; c.weightx = 0;
        form.add(browseButton, c);

        // Baris 3: progress bar
        c.gridx = 0; c.gridy = 2; c.weightx = 1; c.gridwidth = 3;
        form.add(progressBar, c);

        // Baris 4: status
        c.gridy = 3;
        form.add(statusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        buttons.add(cancelButton);
        buttons.add(downloadButton);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void addListeners() {
        browseButton.addActionListener(e -> chooseFolder());
        downloadButton.addActionListener(e -> startDownload());
        urlField.addActionListener(e -> startDownload());      // tekan Enter = Download
        cancelButton.addActionListener(e -> {
            if (worker != null) {
                worker.cancel(true);
            }
        });
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(folderField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Pilih folder tujuan");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------
    //  Download
    // ------------------------------------------------------------------
    private void startDownload() {
        if (!downloadButton.isEnabled()) {
            return;
        }
        String urlText = urlField.getText().trim();
        String folderText = folderField.getText().trim();

        if (urlText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "URL tidak boleh kosong.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (folderText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Folder tujuan belum dipilih.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true);
        resetProgress();
        statusLabel.setText("Menghubungkan ke server...");

        worker = new DownloadWorker(urlText, new File(folderText), new DownloadWorker.Listener() {

            @Override
            public void onUpdate(int percent, String status) {
                statusLabel.setText(status);
                if (percent < 0) {
                    progressBar.setIndeterminate(true);     // ukuran file tidak diketahui
                } else {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(percent);
                    progressBar.setString(percent + "%");
                }
            }

            @Override
            public void onDone(File file) {
                setBusy(false);
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                progressBar.setString("100%");
                statusLabel.setText("Download selesai: " + file.getName());

                // Pop-up download selesai
                JOptionPane.showMessageDialog(MainFrame.this,
                        "Download selesai!\n\nFile disimpan di:\n" + file.getAbsolutePath(),
                        "Download Selesai", JOptionPane.INFORMATION_MESSAGE);

                // Cek apakah file yang diunduh adalah ZIP
                if (ZipHelper.isZip(file)) {
                    askToExtract(file);
                }
            }

            @Override
            public void onError(Throwable error) {
                setBusy(false);
                resetProgress();
                statusLabel.setText("Download gagal.");

                String message;
                if (error instanceof MalformedURLException) {
                    message = "Format URL tidak valid.\nContoh: https://contoh.com/file.zip";
                } else {
                    message = "Download gagal:\n" + error.getMessage();
                }
                JOptionPane.showMessageDialog(MainFrame.this, message,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public void onCancelled() {
                setBusy(false);
                resetProgress();
                statusLabel.setText("Download dibatalkan.");
            }
        });
        worker.execute();
    }

    // ------------------------------------------------------------------
    //  Ekstraksi ZIP
    // ------------------------------------------------------------------
    private void askToExtract(File zipFile) {
        Object[] options = {"Ekstrak", "Tidak"};
        int choice = JOptionPane.showOptionDialog(this,
                "File \"" + zipFile.getName() + "\" adalah file ZIP.\n"
                        + "Apakah Anda ingin mengekstraknya?",
                "File ZIP Terdeteksi",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice != 0) {
            statusLabel.setText("Download selesai (tanpa ekstraksi).");
            return;
        }

        setBusy(true);
        cancelButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Mengekstrak...");
        statusLabel.setText("Mengekstrak " + zipFile.getName() + "...");

        // Ekstraksi juga di thread terpisah supaya GUI tetap responsif
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return ZipHelper.extract(zipFile);
            }

            @Override
            protected void done() {
                setBusy(false);
                progressBar.setIndeterminate(false);
                try {
                    File dir = get();
                    progressBar.setValue(100);
                    progressBar.setString("100%");
                    statusLabel.setText("Ekstraksi selesai.");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Ekstraksi selesai!\n\nIsi ZIP diekstrak ke:\n" + dir.getAbsolutePath(),
                            "Ekstraksi Selesai", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    resetProgress();
                    statusLabel.setText("Ekstraksi gagal.");
                    Throwable cause = (ex instanceof ExecutionException && ex.getCause() != null)
                            ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Ekstraksi gagal:\n" + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------
    //  Fungsi bantu GUI
    // ------------------------------------------------------------------
    private void setBusy(boolean busy) {
        downloadButton.setEnabled(!busy);
        browseButton.setEnabled(!busy);
        urlField.setEnabled(!busy);
        folderField.setEnabled(!busy);
        cancelButton.setEnabled(busy);
    }

    private void resetProgress() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("0%");
    }
}
