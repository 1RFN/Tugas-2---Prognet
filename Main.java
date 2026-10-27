import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Titik awal program untuk menjalankan aplikasi Download File. */
public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Jika gagal, gunakan tampilan bawaan Swing.
        }

        // Buat dan tampilkan GUI Swing pada Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
