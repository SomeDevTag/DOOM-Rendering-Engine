import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JPanel;

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

    enum Direction {
        FORWARD, BACK, LEFT, RIGHT
    }

    final int GRID_SIZE = 20;
    int[] map;
    int cellSize;

    RenderPanel() {
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        cellSize = (int) Math.floor(400 / GRID_SIZE);
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
    }

    float rayAngleStep = 0.4f; // degrees between rays; the field of view is rayCount * rayAngleStep
    int rayCount = 300;
    int columnWidth = 3;
    float renderDistance = 10;
    float viewCenterAngle;
    double playerX = 5;
    double playerY = 2;
    double playerAngle = 55;
    int lastMouseX;
    final Color RAY_COLOR = new Color(50, 100, 50, 150);
    final Color FLOOR_COLOR = new Color(20, 20, 20, 80);

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
        playerAngle = playerAngle % 360;
        // Draw map grid.
        g.setColor(Color.black);
        g.fillRect(0, 0, 1300, 500);
        g.setColor(Color.darkGray);
        for (int i = 1; i < GRID_SIZE; i++) {
            g.drawLine(cellSize * i, 0, cellSize * i, getHeight());
        }
        for (int i = 1; i < GRID_SIZE; i++) {
            g.drawLine(0, cellSize * i, 400, cellSize * i);
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
                    g.fillRect(i * cellSize, j * cellSize, cellSize, cellSize);
                }
            }
        }
        // Clear the 3D view and draw the floor.
        g.setColor(Color.black);
        g.fillRect(400, 0, 900, 500);
        g.setColor(FLOOR_COLOR);
        g.fillRect(400, 200, 900, 200);
        // Draw the player on the map.
        g.setColor(Color.red);
        g.fillOval((int) (playerX * cellSize - cellSize / 4),
                (int) (playerY * cellSize - cellSize / 4), cellSize / 2, cellSize / 2);

        // Distances of the last three rays, used to detect wall edges and corners.
        double prevPrevDist = 0;
        double prevDist = 0;
        double currentDist = 0;
        double rayAngle = playerAngle;
        for (int i = 0; i < rayCount; i++) {

            rayAngle = (rayAngle + rayAngleStep) % 360;
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
                    g.drawLine((int) (playerX * cellSize), (int) (playerY * cellSize),
                            (int) (yStepHit.x * cellSize), (int) (yStepHit.y * cellSize));
                    textureCoord = yStepHit.x;
                } else {
                    hitDist = distanceFromPlayer(xStepHit.x, xStepHit.y);
                    hitBlockId = xStepHit.blockId;
                    g.drawLine((int) (playerX * cellSize), (int) (playerY * cellSize),
                            (int) (xStepHit.x * cellSize), (int) (xStepHit.y * cellSize));
                    textureCoord = xStepHit.y;
                }
                prevPrevDist = prevDist;
                prevDist = currentDist;
                currentDist = hitDist;
                FloatColor wallColor = textureColorAt(hitBlockId, textureCoord);

                // A kink in the distance curve means the previous column sits on a wall edge:
                // darken it if it is a far (inner) corner, brighten it if it is a near (outer) corner.
                if (Math.abs((prevPrevDist - prevDist) - (currentDist - prevDist)) > 0.0003 && prevDist < renderDistance) {

                    double brightness = (renderDistance - prevDist) / renderDistance;
                    if (prevDist > currentDist & prevDist > prevPrevDist) {
                        brightness = brightness - 0.5f;
                        g.setColor(new Color(
                                (int) ((255 * wallColor.r) * brightness),
                                (int) ((255 * wallColor.g) * brightness),
                                (int) ((255 * wallColor.b) * brightness),
                                (int) (200 * brightness)));
                        g.fillRect(400 + ((i - 1) * columnWidth), 200 - (400 - ((int) (prevDist * 35))) / 2, columnWidth,
                                400 - ((int) (prevDist * 35)));
                    } else if (prevDist < currentDist & prevDist < prevPrevDist) {
                        brightness += 0.05f;
                        g.setColor(new Color(
                                (int) ((255 * wallColor.r) * brightness),
                                (int) ((255 * wallColor.g) * brightness),
                                (int) ((255 * wallColor.b) * brightness),
                                (int) (255 * brightness)));
                        g.fillRect(400 + ((i - 1) * columnWidth), 200 - (400 - ((int) (prevDist * 35))) / 2, columnWidth,
                                400 - ((int) (prevDist * 35)));
                    }
                }

                // Draw this ray's wall column, fading to black with distance.
                if (hitDist < renderDistance) {
                    double brightness = (renderDistance - hitDist) / renderDistance;
                    g.setColor(new Color(
                            (int) ((255 * wallColor.r) * brightness),
                            (int) ((255 * wallColor.g) * brightness),
                            (int) ((255 * wallColor.b) * brightness),
                            255));
                } else
                    g.setColor(Color.BLACK);

                g.fillRect(400 + (i * columnWidth), 200 - (400 - ((int) (hitDist * 35))) / 2, columnWidth,
                        400 - ((int) (hitDist * 35)));

            } catch (Exception e) {}
        }
        viewCenterAngle = ((float) (playerAngle + rayCount * (rayAngleStep / 2))) % 360;
        // Crosshair.
        g.setColor(Color.pink);
        g.fillRect(400 + (rayCount / 2) * columnWidth - 1, 199, 2, 2);

    }

    public double distanceFromPlayer(double x, double y) {
        return Math.sqrt(Math.pow((playerX - x), 2) + Math.pow((playerY - y), 2));
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyChar()) {
            case 'L':
            case 'l':
                playerAngle += 5;
                playerAngle = playerAngle % 360;
                break;
            case 'J':
            case 'j':
                playerAngle -= 5;
                playerAngle = playerAngle % 360;
                break;
            case 'w':
            case 'W':
                move(Direction.FORWARD);
                break;
            case 's':
            case 'S':
                move(Direction.BACK);
                break;
            case 'a':
            case 'A':
                move(Direction.LEFT);
                break;
            case 'd':
            case 'D':
                move(Direction.RIGHT);
                break;
            case 'm':
                rayAngleStep += 0.05f;
                break;
            case 'n':
                rayAngleStep -= 0.05f;
                break;
        }
        repaint();
    }

    private void move(Direction direction) {
        double moveSpeed = 0.25;

        double normalizedAngle = (viewCenterAngle % 360 + 360) % 360;
        double rad = Math.toRadians(normalizedAngle);

        double cosA = Math.cos(rad);
        double sinA = Math.sin(rad);

        // Move on each axis separately so the player slides along walls.
        switch (direction) {
            case FORWARD:
                double nextX = playerX + cosA * moveSpeed;
                double nextY = playerY + sinA * moveSpeed;
                if (isFree(nextX, playerY)) playerX = nextX;
                if (isFree(playerX, nextY)) playerY = nextY;
                break;

            case BACK:
                double backX = playerX - cosA * moveSpeed;
                double backY = playerY - sinA * moveSpeed;
                if (isFree(backX, playerY)) playerX = backX;
                if (isFree(playerX, backY)) playerY = backY;
                break;

            case LEFT:
                double leftX = playerX + sinA * moveSpeed;
                double leftY = playerY - cosA * moveSpeed;
                if (isFree(leftX, playerY)) playerX = leftX;
                if (isFree(playerX, leftY)) playerY = leftY;
                break;

            case RIGHT:
                double rightX = playerX - sinA * moveSpeed;
                double rightY = playerY + cosA * moveSpeed;
                if (isFree(rightX, playerY)) playerX = rightX;
                if (isFree(playerX, rightY)) playerY = rightY;
                break;
        }
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
        double radius = 0.05;

        for (double xOff = -radius; xOff <= radius; xOff += radius * 2) {
            for (double yOff = -radius; yOff <= radius; yOff += radius * 2) {

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
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getX() < 400) {
            int x = e.getX() / cellSize;
            int y = e.getY() / cellSize;
            if (e.getButton() == MouseEvent.BUTTON1) {
                // Left click cycles the cell through the block types.
                map[x + y * GRID_SIZE] = (map[x + y * GRID_SIZE] + 1) % blockTypes.length;
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                // Right click clears the cell.
                map[x + y * GRID_SIZE] = 0;
            } else if (e.getButton() == MouseEvent.BUTTON2) {
                // Middle click teleports the player.
                playerX = x;
                playerY = y;
            }
        }
        lastMouseX = e.getX();
        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        lastMouseX = e.getX();
        repaint();
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        // Dragging the mouse turns the view.
        playerAngle = (playerAngle + ((lastMouseX - e.getX()) / GRID_SIZE)) % 360;
        lastMouseX = e.getX();
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

}
