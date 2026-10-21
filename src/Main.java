import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main extends JFrame {
    public Main() {
        setTitle("DOOM Rendering Engine");
        setSize(1300, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center the frame on the screen

        add(new RenderPanel());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main frame = new Main();
            frame.setVisible(true);
        });
    }
}
