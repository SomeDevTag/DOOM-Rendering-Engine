import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main extends JFrame {
    public Main() {
        setTitle("DOOM Rendering Engine");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        add(new RenderPanel());
        pack(); // Size the window to fit the panel
        setLocationRelativeTo(null); // Center the frame on the screen
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main frame = new Main();
            frame.setVisible(true);
        });
    }
}
