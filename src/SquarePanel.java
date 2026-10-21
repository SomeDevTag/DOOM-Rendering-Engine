import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Iterator;
import java.util.Scanner;

import javax.swing.JPanel;

public class SquarePanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    class point {
        public double x;
        public double y;
        public int blockIdentity;
        public int type;
        public double distance;
    }

    class NormalColor {
        public float r;
        public float g;
        public float b;
        public NormalColor(float rb, float gb, float bb) {
            r = rb;
            g = gb;
            b = bb;
        }
    }



    class BlockTexture{
        public NormalColor q1, q2, q3, q4;
        public BlockTexture(NormalColor qe1,NormalColor qe2,NormalColor qe3,NormalColor qe4){
            q1 = qe1;
            q2 = qe2;
            q3 = qe3;
            q4 = qe4;
        }
    }

    class BlockInformation{
        public BlockTexture texture;
        public NormalColor MapColor;
        public BlockInformation(NormalColor qe1,NormalColor qe2,NormalColor qe3,NormalColor qe4, NormalColor Map){
           texture  =  new BlockTexture(qe1, qe2, qe3, qe4);
           MapColor = Map;
        }
    }

    enum Direction {
        right, down, left, up
    }
    int[] blocks;
    final int GRIDSIZE = 20;
    int gridBlockSize;

    SquarePanel() {
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        gridBlockSize = (int) Math.floor(400 / GRIDSIZE);
        blocks = new int[GRIDSIZE * GRIDSIZE];
        // allocate the beginning array.
        for (int i = 0; i < GRIDSIZE; i++) {
            for (int j = 0; j < GRIDSIZE; j++) {
                if (i == GRIDSIZE - 1 || j == GRIDSIZE - 1 || i == 0 || j == 0)
                    blocks[(j * GRIDSIZE) + i] = 1;
                else
                    blocks[(j * GRIDSIZE) + i] = 0;
            }
        }
    }

    float FOV = 90;
    float lenswidth = 0.4f;
    int pixeldensity = 300;
    int pixelwidth = 3;
    float renderDistance = 10;
    float middleScreenAngle;
    double playerX = 5;
    double playerY = 2;
    double angle = 55;
    double PI = Math.PI;
    int lineLength = 3;
    int draggerx;
    boolean dragging = false;
    final Color SCANGREEN = new Color(50, 100, 50, 150);
    final Color DARKERGRAY = new Color(20, 20, 20, 80);

    //---color presets
    public final NormalColor white = new NormalColor(1, 1, 1);
    public final NormalColor red = new NormalColor(1, 0, 0);
    public final NormalColor blue = new NormalColor(0, 0, 1);
    public final NormalColor green = new NormalColor(0, 1, 0);
    public final NormalColor pink = new NormalColor(1, 1, 0);
    public final NormalColor yellow = new NormalColor(1, 0, 1);
    public final NormalColor dgray = new NormalColor(0.2f, 0.2f, 0.2f);
    public final NormalColor lgray = new NormalColor(0.6f, 0.6f, 0.6f);
    //---block presets
    public final BlockInformation blockTypes[] = {
            new BlockInformation(dgray, dgray, dgray, dgray, dgray),
            new BlockInformation(dgray, lgray, lgray, dgray, lgray),
            new BlockInformation(blue, white, white, blue, blue),
            new BlockInformation(dgray, pink, pink, dgray, pink),
            new BlockInformation(dgray, yellow, yellow, dgray, yellow),
            new BlockInformation(red, green, green, red, red),
            new BlockInformation(red, green, blue, yellow, white),
    };




    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        angle = angle % 360;
        // Draw map grid.
        g.setColor(Color.black);
        g.fillRect(0, 0, 1300, 500);
        g.setColor(Color.darkGray); // Set the color of the square
        int placeholder = 0;
        for (int i = 1; i < GRIDSIZE; i++) {
            g.drawLine((gridBlockSize) * i, 0, (gridBlockSize) * i, getHeight());
        }
        for (int i = 1; i < GRIDSIZE; i++) {
            g.drawLine(0, (gridBlockSize) * i, 400, (gridBlockSize) * i);
        }
//	     Render the blocks on the map

        for (int i = 0; i < GRIDSIZE; i++) {
            for (int j = 0; j < GRIDSIZE; j++) {
                int BlockIdentity = blocks[(j * GRIDSIZE) + i];
                if (BlockIdentity != 0) {
                    if (BlockIdentity == 1)
                        g.setColor(Color.lightGray);
                    else {
                        NormalColor tempColor = blockTypes[BlockIdentity].MapColor;
                        g.setColor(new Color((int)(tempColor.r * 255), (int)(tempColor.g * 255), (int)(tempColor.b * 255), 255));
                    }
                    g.fillRect(i * gridBlockSize, j * gridBlockSize, gridBlockSize, gridBlockSize);
                }
            }
        }
        g.setColor(Color.black);
        g.fillRect(400, 0, 900, 500);
        g.setColor(DARKERGRAY);
        g.fillRect(400, 200, 900, 200);
//	        Render player on map.
        double angles = angle * PI / 180;
//	        width of the angle
        double dx = ((Math.cos(angles) * lineLength + playerX) * gridBlockSize);
//	        Height of angle
        double dy = ((Math.sin(angles) * lineLength + playerY) * gridBlockSize);

        g.setColor(Color.red);
        g.fillOval((int) (playerX * (gridBlockSize) - gridBlockSize / 4),
                (int) (playerY * gridBlockSize - gridBlockSize / 4), gridBlockSize / 2, gridBlockSize / 2);

        double Cash1 = 0;
        double Cash2 = 0;
        double Cash3 = 0;
        int IdentityCash1;
        int IdentityCash2;

        NormalColor IdentityColorCash1;
        NormalColor IdentityColorCash2;
        double phangle = angle;
        for (int i = 0; i < pixeldensity; i++) {

            phangle = (phangle + lenswidth) % 360;
            if (phangle < 0)
                phangle += 360;
            angles = phangle * PI / 180;

            int CurrentBlockIdentity;
            double DA; // distance
            point VerticalHit = new point();
            point HorizontalHit = new point();
            VerticalHit.x = playerX;
            VerticalHit.y = playerY;
            HorizontalHit.x = playerX;
            HorizontalHit.y = playerY;

            try {
                // --- Partial Step Initialization for Floats ---
                double mapX = Math.floor(playerX);
                double mapY = Math.floor(playerY);

// Pre-calculate Vertical Hit start so the first loop iteration lands on an integer
                if (Math.sin(angles) != 0) {
                    if (Math.sin(angles) > 0) {
                        double distToFirstY = (mapY + 1) - playerY;
                        VerticalHit.y = mapY; // Loop adds 1 -> lands on mapY + 1
                        VerticalHit.x = playerX + (distToFirstY - 1) * (Math.cos(angles) / Math.sin(angles));
                    } else {
                        double distToFirstY = playerY - mapY;
                        VerticalHit.y = mapY + 1; // Loop subtracts 1 -> lands on mapY
                        VerticalHit.x = playerX - (distToFirstY - 1) * (Math.cos(angles) / Math.sin(angles));
                    }
                } else {
                    VerticalHit.x = playerX;
                    VerticalHit.y = playerY;
                }

// Pre-calculate Horizontal Hit start so the first loop iteration lands on an integer
                if (Math.cos(angles) != 0) {
                    if (Math.cos(angles) >= 0) {
                        double distToFirstX = (mapX + 1) - playerX;
                        HorizontalHit.x = mapX; // Loop adds 1 -> lands on mapX + 1
                        HorizontalHit.y = playerY + (distToFirstX - 1) * (Math.sin(angles) / Math.cos(angles));
                    } else {
                        double distToFirstX = playerX - mapX;
                        HorizontalHit.x = mapX + 1; // Loop subtracts 1 -> lands on mapX
                        HorizontalHit.y = playerY - (distToFirstX - 1) * (Math.sin(angles) / Math.cos(angles));
                    }
                } else {
                    HorizontalHit.x = playerX;
                    HorizontalHit.y = playerY;
                }
                // ===== Vertical Hit Scan =====
                try {
                    do {

                        boolean AmIScanningDiagonal = true;
                        if (Math.sin(angles) == 0)
                            AmIScanningDiagonal = false;
                        if (Math.sin(angles) > 0) {
                            if (AmIScanningDiagonal)
                                VerticalHit.x = (VerticalHit.x + Math.cos(angles) / Math.sin(angles));
                            VerticalHit.y = (VerticalHit.y + 1);
                        } else {
                            if (AmIScanningDiagonal)
                                VerticalHit.x = (VerticalHit.x - Math.cos(angles) / Math.sin(angles));
                            VerticalHit.y = (VerticalHit.y - 1);
                        }
                        int tempIndex = (int) VerticalHit.x + ((int) VerticalHit.y * GRIDSIZE);

                        if (tempIndex >= GRIDSIZE * GRIDSIZE || tempIndex < 0)
                            break;

                        if (((int) VerticalHit.y - 1) >= 0
                                && blocks[(int) VerticalHit.x + (((int) VerticalHit.y - 1) * GRIDSIZE)] != 0) {
                            VerticalHit.blockIdentity = blocks[(int) VerticalHit.x
                                    + (((int) VerticalHit.y - 1) * GRIDSIZE)];
                            break;
                        }

                    } while (blocks[(int) VerticalHit.x + ((int) VerticalHit.y * GRIDSIZE)] == 0 && VerticalHit.x >= 0
                            && VerticalHit.x < GRIDSIZE && VerticalHit.y >= 0 && VerticalHit.y < GRIDSIZE);

                    // Assign final block if none found in loop
                    if (VerticalHit.blockIdentity == 0) {
                        int tempIndex = (int) VerticalHit.x + ((int) VerticalHit.y * GRIDSIZE);
                        if (tempIndex >= 0 && tempIndex < blocks.length) {
                            VerticalHit.blockIdentity = blocks[tempIndex];
                        }
                    }

                } catch (Exception e) {
                }

                try {

                    // ===== Horizontal Hit Scan =====
                    do {

                        boolean AmIScanningDiagonal = true;
                        if (Math.cos(angles) == 0)
                            AmIScanningDiagonal = false;
                        if (Math.cos(angles) >= 0) {
                            if (AmIScanningDiagonal)
                                HorizontalHit.y = (HorizontalHit.y + Math.sin(angles) / Math.cos(angles));
                            HorizontalHit.x = (HorizontalHit.x + 1);
                        } else {
                            if (AmIScanningDiagonal)
                                HorizontalHit.y = (HorizontalHit.y - Math.sin(angles) / Math.cos(angles));
                            HorizontalHit.x = (HorizontalHit.x - 1);
                        }

                        int tempIndex = (int) HorizontalHit.x + ((int) HorizontalHit.y * GRIDSIZE);

                        if (tempIndex >= GRIDSIZE * GRIDSIZE || tempIndex < 0)
                            break;

                        if (((int) HorizontalHit.x - 1) >= 0
                                && blocks[((int) HorizontalHit.x - 1) + ((int) HorizontalHit.y * GRIDSIZE)] != 0) {
                            HorizontalHit.blockIdentity = blocks[((int) HorizontalHit.x - 1)
                                    + ((int) HorizontalHit.y * GRIDSIZE)];
                            break;
                        }

                    } while (blocks[(int) HorizontalHit.x + ((int) HorizontalHit.y * GRIDSIZE)] == 0
                            && HorizontalHit.x >= 0 && HorizontalHit.x < GRIDSIZE && HorizontalHit.y >= 0
                            && HorizontalHit.y < GRIDSIZE);
                    // Assign final block if none found in loop
                    if (HorizontalHit.blockIdentity == 0) {
                        int tempIndex = (int) HorizontalHit.x + ((int) HorizontalHit.y * GRIDSIZE);
                        if (tempIndex >= 0 && tempIndex < blocks.length) {
                            HorizontalHit.blockIdentity = blocks[tempIndex];
                        }
                    }
                } catch (Exception e) {
                }

                g.setColor(SCANGREEN);

                double collisionpoint;
                if (distance(VerticalHit.x, VerticalHit.y) < distance(HorizontalHit.x, HorizontalHit.y)) {
                    DA = distance(VerticalHit.x, VerticalHit.y);
                    CurrentBlockIdentity = VerticalHit.blockIdentity;
//					if(DA  < 10)
                    g.drawLine((int) (playerX * gridBlockSize), (int) (playerY * gridBlockSize),
                            (int) (VerticalHit.x * gridBlockSize), (int) (VerticalHit.y * gridBlockSize));
                    collisionpoint = VerticalHit.x;
                } else {
                    DA = distance(HorizontalHit.x, HorizontalHit.y);
                    CurrentBlockIdentity = HorizontalHit.blockIdentity;
//					if(DA  < 10)
                    g.drawLine((int) (playerX * gridBlockSize), (int) (playerY * gridBlockSize),
                            (int) (HorizontalHit.x * gridBlockSize), (int) (HorizontalHit.y * gridBlockSize));
                    collisionpoint = HorizontalHit.y;
                }
                Cash1 = Cash2;
                Cash2 = Cash3;
                Cash3 = DA;
                IdentityColorCash1 = ColorBringer(CurrentBlockIdentity, collisionpoint);
                IdentityColorCash2 = IdentityColorCash1;
                NormalColor broughtColor = IdentityColorCash1;
                float R = broughtColor.r;
                float G = broughtColor.g;
                float B = broughtColor.b;

                NormalColor broughtColor2 = IdentityColorCash2;

                if (Math.abs((Cash1 - Cash2) - (Cash3 - Cash2)) > 0.0003 && Cash2 < renderDistance) {

                    double colorvisibility =  (renderDistance - Cash2)/renderDistance;
                    if (Cash2 > Cash3 & Cash2 > Cash1) {
                        colorvisibility=(colorvisibility - 0.5f);
                        g.setColor(new Color(
                                (int)((255 * (float) broughtColor2.r)*colorvisibility),
                                (int)((255 * (float)  broughtColor2.g)*colorvisibility),
                                (int)((255 *  (float) broughtColor2.b)*colorvisibility),
                                (int)(200 *colorvisibility)));
                        g.fillRect(400 + ((i - 1) * pixelwidth), 200 - (400 - ((int) (Cash2 * 35))) / 2, pixelwidth,
                                400 - ((int) (Cash2 * 35)));
                    } else if (Cash2 < Cash3 & Cash2 < Cash1) {
                        colorvisibility+=0.05f;
                        g.setColor(new Color(
                                (int)((255 * (float) broughtColor2.r)*colorvisibility),
                                (int)((255 * (float)  broughtColor2.g)*colorvisibility),
                                (int)((255 *  (float) broughtColor2.b)*colorvisibility),
                                (int)(255 *colorvisibility)));

                        g.fillRect(400 + ((i - 1) * pixelwidth), 200 - (400 - ((int) (Cash2 * 35))) / 2, pixelwidth,
                                400 - ((int) (Cash2 * 35)));
                    }
                }

                if(DA < renderDistance) {
                    double colorvisibility =  (renderDistance - DA)/renderDistance;
                    Color tempColorPixel	= new Color(
                            (int)((255 * (float)R)*colorvisibility),
                            (int)((255 * (float) G)*colorvisibility),
                            (int)((255 *  (float)B)*colorvisibility),
                            (int)(255 ));


                    g.setColor(tempColorPixel);
                }else
                    g.setColor(Color.BLACK);

                g.fillRect(400 + (i * pixelwidth), 200 - (400 - ((int) (DA * 35))) / 2, pixelwidth,
                        400 - ((int) (DA * 35)));

            } catch (Exception e) {}
        }
        middleScreenAngle = ((float) (angle + pixeldensity * (lenswidth / 2))) % 360;
        g.setColor(Color.pink);
        g.fillRect(400 + (int) (pixeldensity / 2) * pixelwidth - 1, 199, 2, 2);

    }

    public double distance(double x, double y) {
        return (double) (Math.sqrt(Math.pow((playerX - x), 2) + Math.pow((playerY - y), 2)));
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {

        int targetX, targetY;
        double moveSpeed = 1;
//			System.out.print("boom" );
        switch (e.getKeyChar()) {
            case 'L':
            case 'l':
                angle += 5;
                angle = angle % 360;
                break;
            case 'J':
            case 'j':
                angle -= 5;
                angle = angle % 360;
                break;
            case 'w':
            case 'W':
                moveOnDirection(Direction.up);
                break;
            case 's':
            case 'S':
                moveOnDirection(Direction.down);
                break;
            case 'a':
            case 'A':
                moveOnDirection(Direction.left);
                break;
            case 'd':
            case 'D':
                moveOnDirection(Direction.right);
                break;
            case 'm':
                lenswidth += 0.05f;
                break;
            case 'n':
                lenswidth -= 0.05f;
                break;
        }
        repaint();
    }

    private void moveOnDirection(Direction D) {
        double moveSpeed = 0.25;

        double normalizedAngle = (middleScreenAngle % 360 + 360) % 360;

        //CONVERT TO RADIANS
        double rad = Math.toRadians(normalizedAngle);

        double cosA = Math.cos(rad);
        double sinA = Math.sin(rad);

        switch (D) {
            case up:
                double nextX = playerX + cosA * moveSpeed;
                double nextY = playerY + sinA * moveSpeed;
                if (isFree(nextX, playerY)) playerX = nextX;
                if (isFree(playerX, nextY)) playerY = nextY;
                break;

            case down:
                double backX = playerX - cosA * moveSpeed;
                double backY = playerY - sinA * moveSpeed;
                if (isFree(backX, playerY)) playerX = backX;
                if (isFree(playerX , backY)) playerY = backY;
                break;

            case left:
                double leftX = playerX + sinA * moveSpeed;
                double leftY = playerY - cosA * moveSpeed;
                if (isFree(leftX, playerY)) playerX = leftX;
                if (isFree(playerX, leftY)) playerY = leftY;
                break;

            case right:
                double rightX = playerX - sinA * moveSpeed;
                double rightY = playerY + cosA * moveSpeed;
                if (isFree(rightX, playerY)) playerX = rightX;
                if (isFree(playerX, rightY)) playerY = rightY;
                break;
        }
    }

    private NormalColor ColorBringer(int TypeOfBlock, double hitPosition) {

      double textureOffset = hitPosition%1;
      if(textureOffset < 0.25){return blockTypes[TypeOfBlock].texture.q1;}
      else if(textureOffset < 0.5){return blockTypes[TypeOfBlock].texture.q2;}
      else if(textureOffset < 0.75){return blockTypes[TypeOfBlock].texture.q3;}
      else {return blockTypes[TypeOfBlock].texture.q4;}
    }

    private boolean isFree(double x, double y) {

        double radius = 0.05;

        for (double xOff = -radius; xOff <= radius; xOff += radius * 2) {
            for (double yOff = -radius; yOff <= radius; yOff += radius * 2) {

                int gridX = (int) Math.floor(x + xOff);
                int gridY = (int) Math.floor(y + yOff);

                if (gridX < 0 || gridX >= GRIDSIZE || gridY < 0 || gridY >= GRIDSIZE)
                    return false;

                if (blocks[gridY * GRIDSIZE + gridX] != 0)
                    return false;
            }
        }
        return true;
    }
    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        // build with click
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (e.getX() < 400) {
                int x = (int) (e.getX() / gridBlockSize);
                int y = (int) (e.getY() / gridBlockSize);
                blocks[x + y * GRIDSIZE] = (blocks[x + y * GRIDSIZE] + 1) % blockTypes.length;

            }
            //delete block on right click
        }else if (e.getButton() == MouseEvent.BUTTON3) {
            if (e.getX() < 400) {
                int x = (int) (e.getX() / gridBlockSize);
                int y = (int) (e.getY() / gridBlockSize);
                blocks[x + y * GRIDSIZE] = 0;
            }
        }
        //teleports on middle mouse click
        else if (e.getButton() == MouseEvent.BUTTON2) {
            if (e.getX() < 400) {
                int x = (int) (e.getX() / gridBlockSize);
                int y = (int) (e.getY() / gridBlockSize);
                playerX = x;
                playerY = y;
            }
        }
        draggerx = e.getX();
        repaint();
    }
    @Override
    public void mouseReleased(MouseEvent e) {
        if (dragging)
            dragging = false;
        draggerx = e.getX();
        repaint();
    }
    @Override
    public void mouseEntered(MouseEvent e) {
    }
    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        angle = (angle + ((draggerx - e.getX()) / GRIDSIZE)) % 360;
        draggerx = e.getX();
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

}