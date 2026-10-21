import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main  extends JFrame {
    public Main() {
        setTitle("RENDER R");
        setSize(1300, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center the frame on the screen

        // Create an instance of the custom panel
        SquarePanel squarePanel = new SquarePanel();
        add(squarePanel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main frame = new Main();
            frame.setVisible(true); // Make the frame visible
        });
    }
}
