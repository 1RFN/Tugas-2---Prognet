import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

/**
 * Tampilan (GUI) Modern aplikasi Download File.
 *
 * Fitur:
 * - Input URL + tombol Tempel (Paste) cepat
 * - Pilihan folder simpan + tombol Buka Folder
 * - Deteksi / Filter Jenis File Otomatis (ZIP .zip vs Non-ZIP) tanpa perlu dipilih manual
 * - Jika terdeteksi .zip (melalui ekstensi/MIME & Magic Number ZipHelper), ZipHelper otomatis bekerja
 * - Indikator progres real-time (persentase, ukuran, kecepatan unduh, jenis file)
 * - Tabel riwayat unduhan dengan pengelompokan jenis file (ZIP / Non-ZIP)
 */
public class MainFrame extends JFrame {

    // Palet warna modern
    private static final Color BG_MAIN = new Color(241, 245, 249);
    private static final Color BG_CARD = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color TEXT_PRIMARY = new Color(15, 23, 42);
    private static final Color TEXT_SECONDARY = new Color(100, 116, 139);
    private static final Color ACCENT_BLUE = new Color(37, 99, 235);
    private static final Color ACCENT_BLUE_HOVER = new Color(29, 78, 216);
    private static final Color ACCENT_RED = new Color(220, 38, 38);
    private static final Color ACCENT_RED_HOVER = new Color(185, 28, 28);
    private static final Color ACCENT_GREEN = new Color(22, 163, 74);
    private static final Color ACCENT_AMBER = new Color(217, 119, 6);
    private static final Color BTN_NEUTRAL_BG = new Color(248, 250, 252);
    private static final Color BTN_NEUTRAL_HOVER = new Color(226, 232, 240);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_LABEL = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_METRIC_VALUE = new Font("Segoe UI", Font.BOLD, 13);

    // Komponen input
    private final JTextField urlField = new JTextField(30);
    private final JTextField folderField = new JTextField(30);

    // Tombol aksi form
    private final JButton pasteButton = createNeutralButton("Tempel URL");
    private final JButton browseButton = createNeutralButton("Pilih Folder...");
    private final JButton openFolderTopButton = createNeutralButton("Buka Folder");
    private final JButton downloadButton = createAccentButton("Download Sekarang", ACCENT_BLUE, ACCENT_BLUE_HOVER);
    private final JButton cancelButton = createAccentButton("Batalkan", ACCENT_RED, ACCENT_RED_HOVER);

    // Komponen progres & metrik
    private final JLabel stateBadgeLabel = new JLabel("SIAP", SwingConstants.CENTER);
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Masukkan URL file lalu klik Download Sekarang.");
    private final JLabel metricTypeValue = new JLabel("-");
    private final JLabel metricSizeValue = new JLabel("0 B / -");
    private final JLabel metricSpeedValue = new JLabel("0 B/dtk");

    // Komponen Riwayat & Filter Tabel
    private final String[] tableColumns = {"Nama File", "Jenis File", "Ukuran", "Kecepatan", "Status", "Waktu"};
    private final DefaultTableModel historyModel = new DefaultTableModel(tableColumns, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable historyTable = new JTable(historyModel);
    private final TableRowSorter<DefaultTableModel> rowSorter = new TableRowSorter<>(historyModel);
    private final JComboBox<String> historyCategoryFilter = new JComboBox<>(new String[]{
            "Semua Jenis", FileTypeFilter.LABEL_ZIP, FileTypeFilter.LABEL_NON_ZIP
    });
    private final JTextField searchHistoryField = new JTextField(14);
    private final JLabel historyCountLabel = new JLabel("0 riwayat");

    // Tombol aksi riwayat
    private final JButton openFileButton = createNeutralButton("Buka File");
    private final JButton openFileFolderButton = createNeutralButton("Buka Lokasi");
    private final JButton extractSelectedButton = createNeutralButton("Ekstrak ZIP");
    private final JButton clearHistoryButton = createNeutralButton("Bersihkan Riwayat");

    // Penyimpanan objek File per baris riwayat (selaras dengan index model)
    private final List<File> historyFiles = new ArrayList<>();

    private DownloadWorker worker;

    public MainFrame() {
        super("Download Manager & Auto-Detect ZIP — Tugas 2 Prognet");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_MAIN);

        // Folder tujuan bawaan: ~/Downloads (atau folder home jika tidak ada)
        File home = new File(System.getProperty("user.home"));
        File downloads = new File(home, "Downloads");
        folderField.setText((downloads.isDirectory() ? downloads : home).getAbsolutePath());

        configureComponents();
        buildLayout();
        addListeners();
        updateHistoryButtonsState();

        pack();
        setMinimumSize(new Dimension(780, 680));
        setSize(new Dimension(840, 720));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    //  Konfigurasi komponen & gaya visual
    // ------------------------------------------------------------------
    private void configureComponents() {
        styleTextField(urlField, "Contoh: https://contoh.com/arsip-data.zip");
        styleTextField(folderField, "Lokasi folder penyimpanan");
        styleTextField(searchHistoryField, "Cari nama file...");

        // Progress bar modern
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(400, 26));
        progressBar.setForeground(ACCENT_BLUE);
        progressBar.setBackground(new Color(226, 232, 240));
        progressBar.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225), 1));
        progressBar.setUI(new BasicProgressBarUI() {
            @Override
            protected Color getSelectionBackground() {
                return TEXT_PRIMARY;
            }

            @Override
            protected Color getSelectionForeground() {
                return Color.WHITE;
            }
        });

        statusLabel.setFont(FONT_REGULAR);
        statusLabel.setForeground(TEXT_PRIMARY);

        cancelButton.setEnabled(false);

        // Konfigurasi Tabel Riwayat
        historyTable.setRowSorter(rowSorter);
        historyTable.setFont(FONT_REGULAR);
        historyTable.setRowHeight(30);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setShowVerticalLines(false);
        historyTable.setGridColor(new Color(241, 245, 249));
        historyTable.setSelectionBackground(new Color(219, 234, 254));
        historyTable.setSelectionForeground(TEXT_PRIMARY);
        historyTable.setFillsViewportHeight(true);

        JTableHeader header = historyTable.getTableHeader();
        header.setFont(FONT_LABEL);
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(TEXT_SECONDARY);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));

        // Lebar kolom proporsional
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(230); // Nama File
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(95);  // Jenis File
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(95);  // Ukuran
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(95);  // Kecepatan
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(110); // Status
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(75);  // Waktu

        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(new EmptyBorder(0, 8, 0, 8));

                if (!isSelected) {
                    comp.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                    comp.setForeground(TEXT_PRIMARY);
                }

                String text = (value != null) ? value.toString() : "";
                if (column == 1) { // Jenis File (ZIP vs Non-ZIP)
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(FONT_LABEL);
                    if (!isSelected) {
                        setForeground(getCategoryColor(text));
                    }
                } else if (column == 4) { // Status
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setFont(FONT_LABEL);
                    if (!isSelected) {
                        setForeground(getStatusColor(text));
                    }
                } else if (column == 2 || column == 3 || column == 5) {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setFont(FONT_REGULAR);
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                    setFont(FONT_REGULAR);
                }
                return comp;
            }
        };

        for (int i = 0; i < tableColumns.length; i++) {
            historyTable.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }

        historyCategoryFilter.setFont(FONT_REGULAR);
        historyCategoryFilter.setBackground(Color.WHITE);
    }

    // ------------------------------------------------------------------
    //  Susunan tampilan (Modern Dashboard Layout)
    // ------------------------------------------------------------------
    private void buildLayout() {
        setLayout(new BorderLayout(0, 0));

        // 1. Header Banner di bagian atas
        add(buildHeaderPanel(), BorderLayout.NORTH);

        // 2. Konten utama (3 Card)
        JPanel mainContent = new JPanel(new BorderLayout(0, 12));
        mainContent.setBackground(BG_MAIN);
        mainContent.setBorder(new EmptyBorder(14, 16, 16, 16));

        JPanel topCards = new JPanel();
        topCards.setLayout(new BoxLayout(topCards, BoxLayout.Y_AXIS));
        topCards.setOpaque(false);

        JPanel formCard = buildFormCard();
        JPanel progressCard = buildProgressCard();

        topCards.add(formCard);
        topCards.add(Box.createVerticalStrut(12));
        topCards.add(progressCard);

        mainContent.add(topCards, BorderLayout.NORTH);
        mainContent.add(buildHistoryCard(), BorderLayout.CENTER);

        add(mainContent, BorderLayout.CENTER);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(15, 23, 42),
                        getWidth(), getHeight(), new Color(29, 78, 216)
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel titleLabel = new JLabel("Aplikasi Download File & Auto-Detect ZIP");
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel(
                "Deteksi otomatis jenis file (.zip vs Non-ZIP), pantau kecepatan real-time, & ekstraksi otomatis via ZipHelper"
        );
        subtitleLabel.setFont(FONT_SUBTITLE);
        subtitleLabel.setForeground(new Color(191, 219, 254));

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleLabel);

        // Status Badge di kanan header
        stateBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        stateBadgeLabel.setForeground(Color.WHITE);
        stateBadgeLabel.setOpaque(true);
        stateBadgeLabel.setBackground(new Color(255, 255, 255, 40));
        stateBadgeLabel.setBorder(new EmptyBorder(6, 14, 6, 14));

        JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        badgeWrapper.setOpaque(false);
        badgeWrapper.add(stateBadgeLabel);

        header.add(titleBox, BorderLayout.CENTER);
        header.add(badgeWrapper, BorderLayout.EAST);
        return header;
    }

    private JPanel buildFormCard() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 10));

        JLabel sectionTitle = new JLabel("Pengaturan Unduhan & Deteksi Jenis File Otomatis");
        sectionTitle.setFont(FONT_SECTION);
        sectionTitle.setForeground(TEXT_PRIMARY);
        card.add(sectionTitle, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Baris 1: URL File
        c.gridx = 0; c.gridy = 0; c.weightx = 0; c.gridwidth = 1;
        form.add(createFormLabel("URL File:"), c);

        c.gridx = 1; c.weightx = 1; c.gridwidth = 2;
        form.add(urlField, c);

        c.gridx = 3; c.weightx = 0; c.gridwidth = 1;
        form.add(pasteButton, c);

        // Baris 2: Folder Tujuan
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.gridwidth = 1;
        form.add(createFormLabel("Simpan ke:"), c);

        c.gridx = 1; c.weightx = 1; c.gridwidth = 1;
        form.add(folderField, c);

        c.gridx = 2; c.weightx = 0; c.gridwidth = 1;
        form.add(browseButton, c);

        c.gridx = 3; c.weightx = 0; c.gridwidth = 1;
        form.add(openFolderTopButton, c);

        // Baris 3: Tombol Aksi
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);
        actionButtons.add(cancelButton);
        actionButtons.add(downloadButton);

        c.gridx = 0; c.gridy = 2; c.weightx = 1; c.gridwidth = 4;
        form.add(actionButtons, c);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildProgressCard() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 8));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);

        JLabel title = new JLabel("Status & Progres Unduhan");
        title.setFont(FONT_SECTION);
        title.setForeground(TEXT_PRIMARY);

        topRow.add(title, BorderLayout.WEST);
        topRow.add(statusLabel, BorderLayout.EAST);

        JPanel metricsGrid = new JPanel(new GridLayout(1, 3, 10, 0));
        metricsGrid.setOpaque(false);
        metricsGrid.add(createMetricBox("Hasil Deteksi Jenis File (MIME)", metricTypeValue));
        metricsGrid.add(createMetricBox("Ukuran Terunduh / Total", metricSizeValue));
        metricsGrid.add(createMetricBox("Kecepatan & Estimasi Waktu", metricSpeedValue));

        card.add(topRow, BorderLayout.NORTH);
        card.add(progressBar, BorderLayout.CENTER);
        card.add(metricsGrid, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 10));

        // Toolbar atas: judul + filter tampilan riwayat + pencarian
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);

        JPanel titleGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleGroup.setOpaque(false);
        JLabel title = new JLabel("Riwayat Unduhan");
        title.setFont(FONT_SECTION);
        title.setForeground(TEXT_PRIMARY);

        historyCountLabel.setFont(FONT_SMALL);
        historyCountLabel.setForeground(ACCENT_BLUE);
        historyCountLabel.setOpaque(true);
        historyCountLabel.setBackground(new Color(219, 234, 254));
        historyCountLabel.setBorder(new EmptyBorder(2, 8, 2, 8));

        titleGroup.add(title);
        titleGroup.add(historyCountLabel);

        JPanel filterGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterGroup.setOpaque(false);
        JLabel filterLbl = new JLabel("Tampilkan:");
        filterLbl.setFont(FONT_LABEL);
        filterLbl.setForeground(TEXT_SECONDARY);

        JLabel searchLbl = new JLabel("Cari:");
        searchLbl.setFont(FONT_LABEL);
        searchLbl.setForeground(TEXT_SECONDARY);

        filterGroup.add(filterLbl);
        filterGroup.add(historyCategoryFilter);
        filterGroup.add(searchLbl);
        filterGroup.add(searchHistoryField);

        topBar.add(titleGroup, BorderLayout.WEST);
        topBar.add(filterGroup, BorderLayout.EAST);

        // Tabel dengan scroll pane
        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(Color.WHITE);

        // Toolbar bawah: aksi terhadap file terpilih
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JLabel tipLabel = new JLabel("Tip: Klik 2x pada baris tabel untuk langsung membuka file hasil download.");
        tipLabel.setFont(FONT_SMALL);
        tipLabel.setForeground(TEXT_SECONDARY);

        JPanel historyActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        historyActions.setOpaque(false);
        historyActions.add(openFileButton);
        historyActions.add(openFileFolderButton);
        historyActions.add(extractSelectedButton);
        historyActions.add(clearHistoryButton);

        bottomBar.add(tipLabel, BorderLayout.WEST);
        bottomBar.add(historyActions, BorderLayout.EAST);

        card.add(topBar, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        card.add(bottomBar, BorderLayout.SOUTH);
        return card;
    }

    // ------------------------------------------------------------------
    //  Event Listeners
    // ------------------------------------------------------------------
    private void addListeners() {
        pasteButton.addActionListener(e -> pasteUrlFromClipboard());
        browseButton.addActionListener(e -> chooseFolder());
        openFolderTopButton.addActionListener(e -> openDirectory(new File(folderField.getText().trim())));
        downloadButton.addActionListener(e -> startDownload());
        urlField.addActionListener(e -> startDownload());      // tekan Enter = Download

        cancelButton.addActionListener(e -> {
            if (worker != null) {
                worker.cancel(true);
            }
        });

        // Filter tampilan tabel riwayat
        historyCategoryFilter.addActionListener(e -> applyHistoryTableFilter());
        searchHistoryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyHistoryTableFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyHistoryTableFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyHistoryTableFilter();
            }
        });

        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateHistoryButtonsState();
            }
        });

        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedHistoryFile();
                }
            }
        });

        openFileButton.addActionListener(e -> openSelectedHistoryFile());
        openFileFolderButton.addActionListener(e -> openSelectedHistoryFolder());
        extractSelectedButton.addActionListener(e -> {
            File selected = getSelectedHistoryFile();
            if (selected != null && selected.exists() && ZipHelper.isZip(selected)) {
                askToExtract(selected, historyTable.convertRowIndexToModel(historyTable.getSelectedRow()));
            }
        });
        clearHistoryButton.addActionListener(e -> {
            historyModel.setRowCount(0);
            historyFiles.clear();
            updateHistoryCount();
            updateHistoryButtonsState();
        });
    }

    private void pasteUrlFromClipboard() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data != null) {
                urlField.setText(data.toString().trim());
                urlField.requestFocusInWindow();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Clipboard tidak berisi teks URL.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(folderField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Pilih folder tujuan penyimpanan");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------
    //  Proses Download & Deteksi Otomatis ZIP vs Non-ZIP
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
            urlField.requestFocusInWindow();
            return;
        }
        if (folderText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Folder tujuan belum dipilih.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            folderField.requestFocusInWindow();
            return;
        }

        setBusy(true);
        resetProgress();
        updateBadge("MENGHUBUNGKAN...", ACCENT_BLUE);
        statusLabel.setText("Menghubungkan ke server & mendeteksi jenis file...");

        worker = new DownloadWorker(urlText, new File(folderText), new DownloadWorker.Listener() {

            @Override
            public void onUpdate(int percent, String status) {
                statusLabel.setText(status);
                if (percent < 0) {
                    progressBar.setIndeterminate(true);
                } else {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(percent);
                    progressBar.setString(percent + "%");
                }
            }

            @Override
            public void onDetailedUpdate(DownloadWorker.Update update) {
                onUpdate(update.percent, update.status);
                updateBadge("MENGUNDUH...", ACCENT_BLUE);
                metricTypeValue.setText(update.fileTypeInfo);
                metricSizeValue.setText(update.sizeInfo);
                metricSpeedValue.setText(update.speedInfo);
            }

            @Override
            public void onDone(File file) {
                setBusy(false);
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                progressBar.setString("100%");
                updateBadge("SELESAI", ACCENT_GREEN);
                statusLabel.setText("Download selesai: " + file.getName());

                // Deteksi akhir menggunakan ZipHelper.isZip(file) (magic number 0x504B0304)
                boolean isZipFile = ZipHelper.isZip(file);
                String catLabel = isZipFile ? FileTypeFilter.LABEL_ZIP : FileTypeFilter.LABEL_NON_ZIP;
                String sizeText = DownloadWorker.formatSize(file.length());
                String speedText = worker.getAverageSpeedText();

                metricTypeValue.setText(catLabel + " (" + worker.getDetectedContentType() + ")");
                metricSizeValue.setText(sizeText);
                metricSpeedValue.setText("Rata-rata " + speedText);

                int historyIndex = addHistoryEntry(
                        file.getName(),
                        catLabel,
                        sizeText,
                        speedText,
                        "Selesai",
                        file
                );

                // Pop-up download selesai beserta hasil deteksi jenis file
                JOptionPane.showMessageDialog(MainFrame.this,
                        "Download selesai!\n\n"
                                + "Nama File        : " + file.getName() + "\n"
                                + "Deteksi Jenis    : " + catLabel + "\n"
                                + "Ukuran File      : " + sizeText + "\n"
                                + "Lokasi Simpan    : " + file.getAbsolutePath(),
                        "Download Selesai", JOptionPane.INFORMATION_MESSAGE);

                // Jika terdeteksi .zip, ZipHelper langsung bekerja
                if (isZipFile) {
                    askToExtract(file, historyIndex);
                }
            }

            @Override
            public void onError(Throwable error) {
                setBusy(false);
                resetProgress();
                updateBadge("GAGAL", ACCENT_RED);
                statusLabel.setText("Download gagal.");

                String message;
                if (error instanceof MalformedURLException) {
                    message = "Format URL tidak valid.\nContoh: https://contoh.com/file.zip";
                } else {
                    message = "Download gagal:\n" + error.getMessage();
                }

                addHistoryEntry(
                        worker.getDetectedFileName(),
                        worker.getDetectedCategory(),
                        "-",
                        "-",
                        "Gagal",
                        null
                );

                JOptionPane.showMessageDialog(MainFrame.this, message,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public void onCancelled() {
                setBusy(false);
                resetProgress();
                updateBadge("DIBATALKAN", ACCENT_AMBER);
                statusLabel.setText("Download dibatalkan oleh pengguna.");

                addHistoryEntry(
                        worker.getDetectedFileName(),
                        worker.getDetectedCategory(),
                        DownloadWorker.formatSize(worker.getTotalBytesRead()),
                        "-",
                        "Dibatalkan",
                        null
                );
            }
        });
        worker.execute();
    }

    // ------------------------------------------------------------------
    //  Ekstraksi ZIP melalui ZipHelper
    // ------------------------------------------------------------------
    private void askToExtract(File zipFile, int historyModelRow) {
        Object[] options = {"Ekstrak dengan ZipHelper", "Jangan Ekstrak"};
        int choice = JOptionPane.showOptionDialog(this,
                "Sistem mendeteksi bahwa \"" + zipFile.getName() + "\" adalah file ZIP (.zip)\n"
                        + "berdasarkan pemeriksaan Magic Number (0x504B0304) pada ZipHelper.\n\n"
                        + "Apakah Anda ingin mengekstrak isinya sekarang?",
                "Deteksi File ZIP — ZipHelper",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice != 0) {
            statusLabel.setText("Download selesai (file ZIP disimpan tanpa ekstraksi).");
            return;
        }

        setBusy(true);
        cancelButton.setEnabled(false);
        updateBadge("MENGEKSTRAK...", ACCENT_BLUE);
        progressBar.setIndeterminate(true);
        progressBar.setString("ZipHelper mengekstrak...");
        statusLabel.setText("ZipHelper sedang mengekstrak " + zipFile.getName() + "...");

        // Ekstraksi di thread terpisah supaya GUI tetap responsif
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
                    updateBadge("DIEKSTRAK", ACCENT_GREEN);
                    statusLabel.setText("Ekstraksi selesai ke folder: " + dir.getName());

                    if (historyModelRow >= 0 && historyModelRow < historyModel.getRowCount()) {
                        historyModel.setValueAt("Diekstrak", historyModelRow, 4);
                    }

                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Ekstraksi oleh ZipHelper berhasil!\n\nIsi ZIP diekstrak ke folder:\n" + dir.getAbsolutePath(),
                            "Ekstraksi Selesai", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    resetProgress();
                    updateBadge("GAGAL EKSTRAK", ACCENT_RED);
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
    //  Manajemen Riwayat & Filter Tabel
    // ------------------------------------------------------------------
    private int addHistoryEntry(String fileName, String category, String size, String speed, String status, File file) {
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String cleanName = (fileName == null || fileName.equals("-")) ? "Tidak diketahui" : fileName;
        String cleanCat = (category == null || category.equals("-")) ? FileTypeFilter.LABEL_NON_ZIP : category;

        historyFiles.add(file);
        historyModel.addRow(new Object[]{cleanName, cleanCat, size, speed, status, timeStr});
        int modelRow = historyModel.getRowCount() - 1;

        int viewRow = historyTable.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            historyTable.setRowSelectionInterval(viewRow, viewRow);
            historyTable.scrollRectToVisible(historyTable.getCellRect(viewRow, 0, true));
        }

        updateHistoryCount();
        updateHistoryButtonsState();
        return modelRow;
    }

    private void applyHistoryTableFilter() {
        String selectedCategory = (String) historyCategoryFilter.getSelectedItem();
        String keyword = searchHistoryField.getText().trim().toLowerCase();

        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        if (selectedCategory != null && !selectedCategory.equals("Semua Jenis")) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(selectedCategory) + "$", 1));
        }
        if (!keyword.isEmpty()) {
            filters.add(new RowFilter<Object, Object>() {
                @Override
                public boolean include(Entry<?, ?> entry) {
                    String name = entry.getStringValue(0).toLowerCase();
                    String cat = entry.getStringValue(1).toLowerCase();
                    String status = entry.getStringValue(4).toLowerCase();
                    return name.contains(keyword) || cat.contains(keyword) || status.contains(keyword);
                }
            });
        }

        if (filters.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.andFilter(filters));
        }
        updateHistoryCount();
        updateHistoryButtonsState();
    }

    private void updateHistoryCount() {
        int visible = historyTable.getRowCount();
        int total = historyModel.getRowCount();
        if (visible == total) {
            historyCountLabel.setText(total + " riwayat");
        } else {
            historyCountLabel.setText(visible + " dari " + total + " riwayat");
        }
    }

    private File getSelectedHistoryFile() {
        int viewRow = historyTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = historyTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= historyFiles.size()) {
            return null;
        }
        return historyFiles.get(modelRow);
    }

    private void updateHistoryButtonsState() {
        File selected = getSelectedHistoryFile();
        boolean exists = (selected != null && selected.exists());
        openFileButton.setEnabled(exists);
        openFileFolderButton.setEnabled(exists || (selected != null && selected.getParentFile() != null));
        extractSelectedButton.setEnabled(exists && ZipHelper.isZip(selected));
        clearHistoryButton.setEnabled(historyModel.getRowCount() > 0);
    }

    private void openSelectedHistoryFile() {
        File f = getSelectedHistoryFile();
        if (f == null || !f.exists()) {
            JOptionPane.showMessageDialog(this,
                    "File tidak ditemukan atau unduhan tersebut tidak menghasilkan file.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(f);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Gagal membuka file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelectedHistoryFolder() {
        File f = getSelectedHistoryFile();
        if (f != null) {
            File dir = f.isDirectory() ? f : f.getParentFile();
            openDirectory(dir);
        }
    }

    private void openDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Folder tidak ditemukan: " + (dir != null ? dir.getAbsolutePath() : "-"),
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Gagal membuka folder:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    //  Fungsi bantu GUI & Komponen Kustom
    // ------------------------------------------------------------------
    private void setBusy(boolean busy) {
        downloadButton.setEnabled(!busy);
        browseButton.setEnabled(!busy);
        pasteButton.setEnabled(!busy);
        urlField.setEnabled(!busy);
        folderField.setEnabled(!busy);
        cancelButton.setEnabled(busy);
    }

    private void resetProgress() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("0%");
        metricSizeValue.setText("0 B / -");
        metricSpeedValue.setText("0 B/dtk");
    }

    private void updateBadge(String text, Color bg) {
        stateBadgeLabel.setText(text);
        stateBadgeLabel.setBackground(bg);
        stateBadgeLabel.repaint();
    }

    private static JPanel createCardPanel() {
        JPanel card = new JPanel();
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 16, 12, 16)
        ));
        return card;
    }

    private static JPanel createMetricBox(String labelText, JLabel valueLabel) {
        JPanel box = new JPanel(new BorderLayout(0, 3));
        box.setBackground(new Color(248, 250, 252));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(7, 10, 7, 10)
        ));

        JLabel title = new JLabel(labelText);
        title.setFont(FONT_SMALL);
        title.setForeground(TEXT_SECONDARY);

        valueLabel.setFont(FONT_METRIC_VALUE);
        valueLabel.setForeground(TEXT_PRIMARY);

        box.add(title, BorderLayout.NORTH);
        box.add(valueLabel, BorderLayout.CENTER);
        return box;
    }

    private static JLabel createFormLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(TEXT_PRIMARY);
        return lbl;
    }

    private static void styleTextField(JTextField field, String tooltip) {
        field.setFont(FONT_REGULAR);
        field.setToolTipText(tooltip);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    private static JButton createAccentButton(String text, Color baseColor, Color hoverColor) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(203, 213, 225));
                } else if (hovered) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(baseColor);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        return btn;
    }

    private static JButton createNeutralButton(String text) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(241, 245, 249));
                } else if (hovered) {
                    g2.setColor(BTN_NEUTRAL_HOVER);
                } else {
                    g2.setColor(BTN_NEUTRAL_BG);
                }
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(7, 12, 7, 12));
        return btn;
    }

    private static Color getCategoryColor(String cat) {
        if (FileTypeFilter.LABEL_ZIP.equals(cat)) {
            return ACCENT_AMBER;
        }
        return ACCENT_BLUE;
    }

    private static Color getStatusColor(String status) {
        if ("Selesai".equals(status) || "Diekstrak".equals(status)) {
            return ACCENT_GREEN;
        }
        if ("Dibatalkan".equals(status)) {
            return ACCENT_AMBER;
        }
        if ("Gagal".equals(status)) {
            return ACCENT_RED;
        }
        return TEXT_PRIMARY;
    }
}
