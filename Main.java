import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Titik awal program. Jalankan file ini (Run Java) di VS Code.
 */
public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // jika gagal, pakai tampilan bawaan Swing
        }

        // GUI Swing harus dibuat di Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
