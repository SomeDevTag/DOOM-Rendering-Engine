import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.Timer;

public class RenderPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    class RayHit {
        public double x;
        public double y;
        public int blockId;
    }

    // RGB color with components in the 0..1 range.
    class FloatColor {
        public float r;
        public float g;
        public float b;
        public FloatColor(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    // A wall face is split into four vertical stripes, each with its own color.
    class WallTexture {
        public FloatColor stripe1, stripe2, stripe3, stripe4;
        public WallTexture(FloatColor stripe1, FloatColor stripe2, FloatColor stripe3, FloatColor stripe4) {
            this.stripe1 = stripe1;
            this.stripe2 = stripe2;
            this.stripe3 = stripe3;
            this.stripe4 = stripe4;
        }
    }

    class BlockType {
        public WallTexture texture;
        public FloatColor mapColor;
        public BlockType(FloatColor stripe1, FloatColor stripe2, FloatColor stripe3, FloatColor stripe4, FloatColor mapColor) {
            texture = new WallTexture(stripe1, stripe2, stripe3, stripe4);
            this.mapColor = mapColor;
        }
    }

    //---layout, in pixels: the top-down map on the left, the 3D view on the right
    static final int MAP_SIZE = 400;
    static final int RAY_COUNT = 300;
    static final int COLUMN_WIDTH = 3;
    static final int VIEW_X = MAP_SIZE;
    static final int VIEW_WIDTH = RAY_COUNT * COLUMN_WIDTH;
    static final int VIEW_HEIGHT = MAP_SIZE;
    static final int HORIZON_Y = VIEW_HEIGHT / 2;
    static final int PANEL_WIDTH = VIEW_X + VIEW_WIDTH;
    static final int PANEL_HEIGHT = VIEW_HEIGHT;

    //---world
    static final int GRID_SIZE = 20;
    static final int CELL_SIZE = MAP_SIZE / GRID_SIZE;
    static final float RENDER_DISTANCE = 10; // in cells; walls fade to black by this distance
    static final int WALL_SHRINK_PER_CELL = 35; // wall columns lose this many pixels of height per cell of distance
    static final double PLAYER_RADIUS = 0.05;

    //---controls
    static final double MOVE_SPEED = 3; // cells per second
    static final double TURN_SPEED = 120; // degrees per second
    static final double MOUSE_TURN_PER_PIXEL = 0.3; // degrees
    static final float FOV_CHANGE = 0.05f; // change in degrees between rays per N/M press
    static final float MIN_RAY_ANGLE_STEP = 0.05f;
    static final int TICK_MS = 16; // about 60 updates per second
    static final double MAX_TICK_SECONDS = 0.1; // stops a stalled tick from jumping the player

    //---edge highlighting
    static final double EDGE_THRESHOLD = 0.0003;
    static final double INNER_CORNER_DARKEN = 0.5;
    static final double OUTER_CORNER_BRIGHTEN = 0.05;
    static final int INNER_CORNER_ALPHA = 200;

    static final Color RAY_COLOR = new Color(50, 100, 50, 150);
    static final Color FLOOR_COLOR = new Color(20, 20, 20, 80);

    int[] map;
    float rayAngleStep = 0.4f; // degrees between rays; the field of view is RAY_COUNT * rayAngleStep
    double playerX = 5;
    double playerY = 2;
    double playerAngle = 115; // degrees, the direction the player faces (the middle of the view)
    final Set<Integer> heldKeys = new HashSet<>();
    long lastTickNanos = System.nanoTime();
    int lastMouseX;
    boolean turningWithMouse;

    RenderPanel() {
        this.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        // Forget held keys when the window loses focus, so the player doesn't keep walking.
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                heldKeys.clear();
            }
        });
        map = new int[GRID_SIZE * GRID_SIZE];
        // Start with an empty room surrounded by walls.
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (i == GRID_SIZE - 1 || j == GRID_SIZE - 1 || i == 0 || j == 0)
                    map[(j * GRID_SIZE) + i] = 1;
                else
                    map[(j * GRID_SIZE) + i] = 0;
            }
        }
        new Timer(TICK_MS, e -> tick()).start();
    }

    //---color presets
    final FloatColor white = new FloatColor(1, 1, 1);
    final FloatColor red = new FloatColor(1, 0, 0);
    final FloatColor blue = new FloatColor(0, 0, 1);
    final FloatColor green = new FloatColor(0, 1, 0);
    final FloatColor yellow = new FloatColor(1, 1, 0);
    final FloatColor magenta = new FloatColor(1, 0, 1);
    final FloatColor darkGray = new FloatColor(0.2f, 0.2f, 0.2f);
    final FloatColor lightGray = new FloatColor(0.6f, 0.6f, 0.6f);
    //---block presets, indexed by the values stored in map (0 = empty)
    final BlockType blockTypes[] = {
            new BlockType(darkGray, darkGray, darkGray, darkGray, darkGray),
            new BlockType(darkGray, lightGray, lightGray, darkGray, lightGray),
            new BlockType(blue, white, white, blue, blue),
            new BlockType(darkGray, yellow, yellow, darkGray, yellow),
            new BlockType(darkGray, magenta, magenta, darkGray, magenta),
            new BlockType(red, green, green, red, red),
            new BlockType(red, green, blue, magenta, white),
    };

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw map grid.
        g.setColor(Color.black);
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(Color.darkGray);
        for (int i = 1; i < GRID_SIZE; i++) {
            g.drawLine(CELL_SIZE * i, 0, CELL_SIZE * i, MAP_SIZE);
        }
        for (int i = 1; i < GRID_SIZE; i++) {
            g.drawLine(0, CELL_SIZE * i, MAP_SIZE, CELL_SIZE * i);
        }
        // Draw the blocks on the map.
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                int blockId = map[(j * GRID_SIZE) + i];
                if (blockId != 0) {
                    if (blockId == 1)
                        g.setColor(Color.lightGray);
                    else {
                        FloatColor mapColor = blockTypes[blockId].mapColor;
                        g.setColor(new Color((int) (mapColor.r * 255), (int) (mapColor.g * 255), (int) (mapColor.b * 255), 255));
                    }
                    g.fillRect(i * CELL_SIZE, j * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }
        }
        // Clear the 3D view and draw the floor.
        g.setColor(Color.black);
        g.fillRect(VIEW_X, 0, VIEW_WIDTH, VIEW_HEIGHT);
        g.setColor(FLOOR_COLOR);
        g.fillRect(VIEW_X, HORIZON_Y, VIEW_WIDTH, VIEW_HEIGHT - HORIZON_Y);
        // Draw the player on the map.
        g.setColor(Color.red);
        g.fillOval((int) (playerX * CELL_SIZE - CELL_SIZE / 4),
                (int) (playerY * CELL_SIZE - CELL_SIZE / 4), CELL_SIZE / 2, CELL_SIZE / 2);

        // Distances of the last three rays, used to detect wall edges and corners.
        double prevPrevDist = 0;
        double prevDist = 0;
        double currentDist = 0;
        for (int i = 0; i < RAY_COUNT; i++) {

            // Rays fan out evenly on both sides of the direction the player faces.
            double rayAngle = (playerAngle + (i - RAY_COUNT / 2) * rayAngleStep) % 360;
            if (rayAngle < 0)
                rayAngle += 360;
            double rad = rayAngle * Math.PI / 180;

            int hitBlockId;
            double hitDist;
            // yStepHit walks the ray across horizontal grid lines (y = whole number),
            // xStepHit across vertical grid lines (x = whole number).
            RayHit yStepHit = new RayHit();
            RayHit xStepHit = new RayHit();
            yStepHit.x = playerX;
            yStepHit.y = playerY;
            xStepHit.x = playerX;
            xStepHit.y = playerY;

            try {
                double cellX = Math.floor(playerX);
                double cellY = Math.floor(playerY);

                // Start one step back so the first loop iteration lands on a whole-number y.
                if (Math.sin(rad) != 0) {
                    if (Math.sin(rad) > 0) {
                        double distToFirstY = (cellY + 1) - playerY;
                        yStepHit.y = cellY;
                        yStepHit.x = playerX + (distToFirstY - 1) * (Math.cos(rad) / Math.sin(rad));
                    } else {
                        double distToFirstY = playerY - cellY;
                        yStepHit.y = cellY + 1;
                        yStepHit.x = playerX - (distToFirstY - 1) * (Math.cos(rad) / Math.sin(rad));
                    }
                } else {
                    yStepHit.x = playerX;
                    yStepHit.y = playerY;
                }

                // Start one step back so the first loop iteration lands on a whole-number x.
                if (Math.cos(rad) != 0) {
                    if (Math.cos(rad) >= 0) {
                        double distToFirstX = (cellX + 1) - playerX;
                        xStepHit.x = cellX;
                        xStepHit.y = playerY + (distToFirstX - 1) * (Math.sin(rad) / Math.cos(rad));
                    } else {
                        double distToFirstX = playerX - cellX;
                        xStepHit.x = cellX + 1;
                        xStepHit.y = playerY - (distToFirstX - 1) * (Math.sin(rad) / Math.cos(rad));
                    }
                } else {
                    xStepHit.x = playerX;
                    xStepHit.y = playerY;
                }

                // ===== Step across horizontal grid lines =====
                try {
                    do {
                        boolean sloped = Math.sin(rad) != 0;
                        if (Math.sin(rad) > 0) {
                            if (sloped)
                                yStepHit.x = yStepHit.x + Math.cos(rad) / Math.sin(rad);
                            yStepHit.y = yStepHit.y + 1;
                        } else {
                            if (sloped)
                                yStepHit.x = yStepHit.x - Math.cos(rad) / Math.sin(rad);
                            yStepHit.y = yStepHit.y - 1;
                        }
                        int index = (int) yStepHit.x + ((int) yStepHit.y * GRID_SIZE);

                        if (index >= GRID_SIZE * GRID_SIZE || index < 0)
                            break;

                        if (((int) yStepHit.y - 1) >= 0
                                && map[(int) yStepHit.x + (((int) yStepHit.y - 1) * GRID_SIZE)] != 0) {
                            yStepHit.blockId = map[(int) yStepHit.x + (((int) yStepHit.y - 1) * GRID_SIZE)];
                            break;
                        }

                    } while (map[(int) yStepHit.x + ((int) yStepHit.y * GRID_SIZE)] == 0 && yStepHit.x >= 0
                            && yStepHit.x < GRID_SIZE && yStepHit.y >= 0 && yStepHit.y < GRID_SIZE);

                    // Take the block the ray stopped on if none was found in the loop.
                    if (yStepHit.blockId == 0) {
                        int index = (int) yStepHit.x + ((int) yStepHit.y * GRID_SIZE);
                        if (index >= 0 && index < map.length) {
                            yStepHit.blockId = map[index];
                        }
                    }

                } catch (Exception e) {
                }

                // ===== Step across vertical grid lines =====
                try {
                    do {
                        boolean sloped = Math.cos(rad) != 0;
                        if (Math.cos(rad) >= 0) {
                            if (sloped)
                                xStepHit.y = xStepHit.y + Math.sin(rad) / Math.cos(rad);
                            xStepHit.x = xStepHit.x + 1;
                        } else {
                            if (sloped)
                                xStepHit.y = xStepHit.y - Math.sin(rad) / Math.cos(rad);
                            xStepHit.x = xStepHit.x - 1;
                        }

                        int index = (int) xStepHit.x + ((int) xStepHit.y * GRID_SIZE);

                        if (index >= GRID_SIZE * GRID_SIZE || index < 0)
                            break;

                        if (((int) xStepHit.x - 1) >= 0
                                && map[((int) xStepHit.x - 1) + ((int) xStepHit.y * GRID_SIZE)] != 0) {
                            xStepHit.blockId = map[((int) xStepHit.x - 1) + ((int) xStepHit.y * GRID_SIZE)];
                            break;
                        }

                    } while (map[(int) xStepHit.x + ((int) xStepHit.y * GRID_SIZE)] == 0
                            && xStepHit.x >= 0 && xStepHit.x < GRID_SIZE && xStepHit.y >= 0
                            && xStepHit.y < GRID_SIZE);

                    // Take the block the ray stopped on if none was found in the loop.
                    if (xStepHit.blockId == 0) {
                        int index = (int) xStepHit.x + ((int) xStepHit.y * GRID_SIZE);
                        if (index >= 0 && index < map.length) {
                            xStepHit.blockId = map[index];
                        }
                    }
                } catch (Exception e) {
                }

                // Use the nearer of the two hits and draw the ray on the map.
                g.setColor(RAY_COLOR);
                double textureCoord;
                if (distanceFromPlayer(yStepHit.x, yStepHit.y) < distanceFromPlayer(xStepHit.x, xStepHit.y)) {
                    hitDist = distanceFromPlayer(yStepHit.x, yStepHit.y);
                    hitBlockId = yStepHit.blockId;
                    g.drawLine((int) (playerX * CELL_SIZE), (int) (playerY * CELL_SIZE),
                            (int) (yStepHit.x * CELL_SIZE), (int) (yStepHit.y * CELL_SIZE));
                    textureCoord = yStepHit.x;
                } else {
                    hitDist = distanceFromPlayer(xStepHit.x, xStepHit.y);
                    hitBlockId = xStepHit.blockId;
                    g.drawLine((int) (playerX * CELL_SIZE), (int) (playerY * CELL_SIZE),
                            (int) (xStepHit.x * CELL_SIZE), (int) (xStepHit.y * CELL_SIZE));
                    textureCoord = xStepHit.y;
                }
                prevPrevDist = prevDist;
                prevDist = currentDist;
                currentDist = hitDist;
                FloatColor wallColor = textureColorAt(hitBlockId, textureCoord);

                // A kink in the distance curve means the previous column sits on a wall edge:
                // darken it if it is a far (inner) corner, brighten it if it is a near (outer) corner.
                if (Math.abs((prevPrevDist - prevDist) - (currentDist - prevDist)) > EDGE_THRESHOLD && prevDist < RENDER_DISTANCE) {

                    double brightness = (RENDER_DISTANCE - prevDist) / RENDER_DISTANCE;
                    if (prevDist > currentDist & prevDist > prevPrevDist) {
                        brightness = brightness - INNER_CORNER_DARKEN;
                        g.setColor(new Color(
                                (int) ((255 * wallColor.r) * brightness),
                                (int) ((255 * wallColor.g) * brightness),
                                (int) ((255 * wallColor.b) * brightness),
                                (int) (INNER_CORNER_ALPHA * brightness)));
                        drawWallColumn(g, i - 1, prevDist);
                    } else if (prevDist < currentDist & prevDist < prevPrevDist) {
                        brightness += OUTER_CORNER_BRIGHTEN;
                        g.setColor(new Color(
                                (int) ((255 * wallColor.r) * brightness),
                                (int) ((255 * wallColor.g) * brightness),
                                (int) ((255 * wallColor.b) * brightness),
                                (int) (255 * brightness)));
                        drawWallColumn(g, i - 1, prevDist);
                    }
                }

                // Draw this ray's wall column, fading to black with distance.
                if (hitDist < RENDER_DISTANCE) {
                    double brightness = (RENDER_DISTANCE - hitDist) / RENDER_DISTANCE;
                    g.setColor(new Color(
                            (int) ((255 * wallColor.r) * brightness),
                            (int) ((255 * wallColor.g) * brightness),
                            (int) ((255 * wallColor.b) * brightness),
                            255));
                } else
                    g.setColor(Color.BLACK);

                drawWallColumn(g, i, hitDist);

            } catch (Exception e) {}
        }
        // Crosshair.
        g.setColor(Color.pink);
        g.fillRect(VIEW_X + (RAY_COUNT / 2) * COLUMN_WIDTH - 1, HORIZON_Y - 1, 2, 2);

    }

    // Draws screen column `column` of the 3D view for a wall `distance` cells away.
    private void drawWallColumn(Graphics g, int column, double distance) {
        int height = VIEW_HEIGHT - (int) (distance * WALL_SHRINK_PER_CELL);
        g.fillRect(VIEW_X + column * COLUMN_WIDTH, HORIZON_Y - height / 2, COLUMN_WIDTH, height);
    }

    public double distanceFromPlayer(double x, double y) {
        return Math.sqrt(Math.pow((playerX - x), 2) + Math.pow((playerY - y), 2));
    }

    // Runs every TICK_MS: applies the held movement and turning keys.
    private void tick() {
        long now = System.nanoTime();
        double seconds = Math.min((now - lastTickNanos) / 1e9, MAX_TICK_SECONDS);
        lastTickNanos = now;
        if (!heldKeys.isEmpty() && update(seconds))
            repaint();
    }

    // Moves and turns the player for `seconds` of held keys. Returns true if anything changed.
    boolean update(double seconds) {
        double turn = 0;
        if (isHeld(KeyEvent.VK_J, KeyEvent.VK_LEFT)) turn -= 1;
        if (isHeld(KeyEvent.VK_L, KeyEvent.VK_RIGHT)) turn += 1;
        double forward = 0;
        if (isHeld(KeyEvent.VK_W, KeyEvent.VK_UP)) forward += 1;
        if (isHeld(KeyEvent.VK_S, KeyEvent.VK_DOWN)) forward -= 1;
        double strafe = 0;
        if (isHeld(KeyEvent.VK_D)) strafe += 1;
        if (isHeld(KeyEvent.VK_A)) strafe -= 1;
        if (turn == 0 && forward == 0 && strafe == 0)
            return false;

        turnBy(turn * TURN_SPEED * seconds);

        if (forward != 0 || strafe != 0) {
            double rad = Math.toRadians(playerAngle);
            // Divide by the input's length so moving diagonally isn't faster.
            double step = MOVE_SPEED * seconds / Math.hypot(forward, strafe);
            double dx = (Math.cos(rad) * forward - Math.sin(rad) * strafe) * step;
            double dy = (Math.sin(rad) * forward + Math.cos(rad) * strafe) * step;
            // Move on each axis separately so the player slides along walls.
            if (isFree(playerX + dx, playerY)) playerX += dx;
            if (isFree(playerX, playerY + dy)) playerY += dy;
        }
        return true;
    }

    private boolean isHeld(int... keyCodes) {
        for (int keyCode : keyCodes)
            if (heldKeys.contains(keyCode))
                return true;
        return false;
    }

    private void turnBy(double degrees) {
        playerAngle = ((playerAngle + degrees) % 360 + 360) % 360;
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        heldKeys.add(e.getKeyCode());
        switch (e.getKeyCode()) {
            case KeyEvent.VK_M:
                rayAngleStep += FOV_CHANGE;
                repaint();
                break;
            case KeyEvent.VK_N:
                rayAngleStep = Math.max(MIN_RAY_ANGLE_STEP, rayAngleStep - FOV_CHANGE);
                repaint();
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    // Picks the texture stripe for where along the wall face the ray hit.
    private FloatColor textureColorAt(int blockId, double hitPosition) {
        double textureOffset = hitPosition % 1;
        if (textureOffset < 0.25) return blockTypes[blockId].texture.stripe1;
        else if (textureOffset < 0.5) return blockTypes[blockId].texture.stripe2;
        else if (textureOffset < 0.75) return blockTypes[blockId].texture.stripe3;
        else return blockTypes[blockId].texture.stripe4;
    }

    // True if a small square around (x, y) touches no wall.
    private boolean isFree(double x, double y) {
        for (double xOff = -PLAYER_RADIUS; xOff <= PLAYER_RADIUS; xOff += PLAYER_RADIUS * 2) {
            for (double yOff = -PLAYER_RADIUS; yOff <= PLAYER_RADIUS; yOff += PLAYER_RADIUS * 2) {

                int gridX = (int) Math.floor(x + xOff);
                int gridY = (int) Math.floor(y + yOff);

                if (gridX < 0 || gridX >= GRID_SIZE || gridY < 0 || gridY >= GRID_SIZE)
                    return false;

                if (map[gridY * GRID_SIZE + gridX] != 0)
                    return false;
            }
        }
        return true;
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (e.getX() < MAP_SIZE && e.getY() < MAP_SIZE) {
            int x = e.getX() / CELL_SIZE;
            int y = e.getY() / CELL_SIZE;
            if (e.getButton() == MouseEvent.BUTTON1) {
                // Left click cycles the cell through the block types.
                map[x + y * GRID_SIZE] = (map[x + y * GRID_SIZE] + 1) % blockTypes.length;
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                // Right click clears the cell.
                map[x + y * GRID_SIZE] = 0;
            } else if (e.getButton() == MouseEvent.BUTTON2 && map[x + y * GRID_SIZE] == 0) {
                // Middle click teleports the player to the middle of an empty cell.
                playerX = x + 0.5;
                playerY = y + 0.5;
            }
        }
        // Dragging only turns the view when it starts in the 3D view, so editing the map doesn't spin you around.
        turningWithMouse = e.getX() >= VIEW_X;
        lastMouseX = e.getX();
        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        turningWithMouse = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!turningWithMouse)
            return;
        // Drag the view like grabbing the scene: drag left to look right.
        turnBy((lastMouseX - e.getX()) * MOUSE_TURN_PER_PIXEL);
        lastMouseX = e.getX();
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

}
