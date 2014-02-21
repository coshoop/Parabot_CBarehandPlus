package Hunter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import org.parabot.core.ui.components.LogArea;
import org.parabot.environment.api.interfaces.Paintable;
import org.parabot.environment.api.utils.Time;
import org.parabot.environment.input.Mouse;
import org.parabot.environment.scripts.Category;
import org.parabot.environment.scripts.Script;
import org.parabot.environment.scripts.ScriptManifest;
import org.parabot.environment.scripts.framework.Strategy;
import org.rev317.api.events.MessageEvent;
import org.rev317.api.events.listeners.MessageListener;
import org.rev317.api.methods.Camera;
import org.rev317.api.methods.Game;
import org.rev317.api.methods.Inventory;
import org.rev317.api.methods.Menu;
import org.rev317.api.methods.Npcs;
import org.rev317.api.methods.Players;
import org.rev317.api.methods.Skill;
import org.rev317.api.wrappers.hud.Item;
import org.rev317.api.wrappers.interactive.Npc;
import org.rev317.api.wrappers.scene.Tile;

/**
 * Implings in PK honor are clustered in groups. Simply stand around the center
 * and select which impling you want to hunt. This script will remember where
 * you started and stay within a circle around that tile. Note: Anti-random is
 * ~NOT~ currently built in to this script.
 *
 * @author Collin 11
 */
@ScriptManifest(author = "Collin 11", category = Category.AGILITY, description = "Hunts any kind of impling and alchs some loots", name = "CBarehandPlusAlchGUI", servers = {"PKHonor"}, version = 3.1)
public class CBarehandPlus extends Script implements Paintable, MessageListener {

    // General variables
    static ArrayList<Strategy> strategies = new ArrayList<>();
    static int huntingImpID = 0;
    static String huntingImpName = null;

    // Location variables
    static Tile startLocation = null;
    static boolean noImpFlag = false;
    static Tile walkToTile = null;
    final static int huntingRadius = 15;

    // Paint/exp variables
    static Paint_System paint;
    static Npc currentImp = null;
    static int totalCatchCount = 0;
    static int startingHunterXp = 0;
    static int startingHunterLvl = 0;
    static final Skill HUNTER = Skill.CONSTRUCTION;
    static int startingGoldAmount;
    static final int GOLD_ID = 995;

    @Override
    public boolean onExecute() {
        // Login error
        if (!Game.isLoggedIn()) {
            LogArea.error("Please log in before starting the script.");
            return false;
        }

        // Get impling selection
        ImpSelectFrame selectImpling = new ImpSelectFrame();
        while (huntingImpName == null) {
            // impID == -1 iff the ImpSelectFrame window is closed before
            // a selection is made.
            if (huntingImpID == -1) {
                return false;
            }
            Time.sleep(100);
        }
        
        // Create a new Paint_System
        paint = new Paint_System(547, 465, 190, 9, Paint_System.ANCHOR_UP, "CBarehandPlus");

        // Record some starting reference variables
        startingGoldAmount = Inventory.getCount(true, GOLD_ID);
        startingHunterXp = HUNTER.getExperience();
        startingHunterLvl = HUNTER.getLevel();
        startLocation = Players.getLocal().getLocation();

        // Add strategies and start
        strategies.add(new AlchingStrat());
        strategies.add(new LocationStrat());
        strategies.add(new HunterStrat());
        provide(strategies);
        LogArea.log("CBarehandPlus has started.");
        return true;
    }

    @Override
    public void onFinish() {
        LogArea.log("Thank you for using CBarehandPlus.");
        LogArea.error("Total caught:" + totalCatchCount);
    }

    /**
     * High alch specific items in your inventory
     */
    private static class AlchingStrat implements Strategy {

        private static final int NATURE_RUNE = 561;
        private static final int FIRE_RUNE = 554;
        private static final int[] alchIDs = 
        {
            // Zombie
            7592, 7593, 7594, 7595, 7596,
            // Royal
            17286, 17284, 17280, 17282, 17278, 17294,
            // Dragonstone
            1631,
        };

        private static boolean hasRunes() {
            return Game.isLoggedIn() && Inventory.getCount(NATURE_RUNE) > 0
                    && Inventory.getCount(FIRE_RUNE) > 0;
        }

        private static boolean hasAlchItems() {
            return Game.isLoggedIn() && Inventory.getCount(alchIDs) > 0;
        }

        @Override
        public boolean activate() {
            return Game.isLoggedIn() && hasRunes() && hasAlchItems();

        }

        @Override
        public void execute() {
            // Switch to magic tab
            Menu.interact("Magic", new Point(740, 187));
            Time.sleep(500);
            // Cast high alch
            Menu.interact("Cast", new Point(666, 339));
            Time.sleep(1000);

            // Get item to alch
            Item[] alchItems = Inventory.getItems(alchIDs);
            if (alchItems.length > 0) {
                // High alch the first alching item in your inventory
                alchItems[0].interact("Cast");
                Time.sleep(1000);
                Mouse.getInstance().click(new Point(641, 184), true);
            } // It's possible that you don't have any items any more.
            else {
                // In this case, Get rid of the use-alch by clicking
                // somewhere on the chat log
                Mouse.getInstance().click(new Point(300, 400), true);
                Time.sleep(500);
            }
        }

    }

    /**
     * When the HunterStrat can't find any more imps it will register a flag
     * which will allow the LocationStrat to run. LocationStrat will move the
     * play back toward the Tile which the bot started at.
     */
    private static class LocationStrat implements Strategy {

        @Override
        public boolean activate() {
            return Game.isLoggedIn() && noImpFlag;
        }

        @Override
        public void execute() {
            int goalX = startLocation.getX();
            int goalY = startLocation.getY();
            int currentX = Players.getLocal().getLocation().getX();
            int currentY = Players.getLocal().getLocation().getY();
            int difX = goalX - currentX;
            int difY = goalY - currentY;
            
            if (difX == 0 && difY == 0) {
                rotateCamera(30);
            }
            
            int walkToX;
            int walkToY;
            // Calculate where I need to walk to
            if (Math.abs(difX) < 3) {
                walkToX = goalX;
            } else {
                walkToX = currentX + 3 * (difX > 0 ? 1 : -1);
            }
            if (Math.abs(difY) < 3) {
                walkToY = goalY;
            } else {
                walkToY = currentY + 3 * (difY > 0 ? 1 : -1);
            }

            // Create the walk-to tile and walk there
            walkToTile = new Tile(walkToX, walkToY);
            Menu.interact("Walk here", walkToTile.toScreen());

            // Figure out if we need to walk again or not
            if (distance(walkToTile, startLocation) < huntingRadius) {
                // We're in range. Don't automatically walk again.
                noImpFlag = false;
            }
        }
    }

    /**
     * Hunts imps
     */
    private static class HunterStrat implements Strategy {

        /**
         * Gets the nearest imp on screen. returns null if there 
         * is no imp on screen.
         */
        public Npc getNearestImp() {
            Npc[] imps = Npcs.getNearest(huntingImpID);
            if (imps == null || (imps != null && imps.length == 0)) {
                return null;
            } else {
                try {
                    Npc imp = imps[0];
                    Tile impLocation = imp.getLocation();
                    if (!inPlayScreenBounds(impLocation.toScreen())) {
                        return null;
                    }
                    return imp;
                } catch (Exception e) {
                    return null;
                }
            }
        }

        @Override
        public boolean activate() {
            return Game.isLoggedIn() && (!Inventory.isFull());
        }

        @Override
        public void execute() {
            if (Players.getLocal().getAnimation() == -1) {
                for (int i = 0; i < 100; i++) {
                    Time.sleep(10);
                    if (Players.getLocal().getAnimation() != -1
                            || Players.getLocal().isWalking()) {
                        // Currently hunting
                        Time.sleep(500);
                        return;
                    }
                }

                Npc imp = getNearestImp();
                if (imp == null) {
                    noImpFlag = true;
                } else {
                    try {
                        currentImp = imp;
                        imp.interact("catch");
                        walkToTile = null;
                    } catch (Exception e) { /* Imp moved off screen. */ }
                }
            }
        }
    }

    @Override
    public void messageReceived(MessageEvent m) {
        System.out.println(m.getSender() + " : " + m.getMessage() + " : " + m.getType());
        if (m.getType() != MessageEvent.TYPE_PLAYER) {
            if (m.getMessage().startsWith("You successfully catch a ")) {
                totalCatchCount++;
            }
        }
    }

    // Graphics variables
    static final int BOX_SIZE = 5;

    @Override
    public void paint(Graphics g) {
        /*
         INDICATOR PAINT
         */
        if (walkToTile != null) {
            drawBox(g, walkToTile.toScreen(), Color.BLUE);
        }
        if (startLocation != null) {
            drawBox(g, startLocation.toScreen(), Color.MAGENTA);
        }
        if (currentImp != null) {
            drawBox(g, currentImp.getCenterPointOnScreen(), Color.red);
        }
        
        // Paint stats
        int goldGained = Inventory.getCount(true, GOLD_ID) - startingGoldAmount;
        int lvlsGained = HUNTER.getLevel() - startingHunterLvl;
        int xpGained = HUNTER.getExperience() - startingHunterXp;
        
        paint.startPaint(g);
        paint.paintTime("Time ran: ");
        paint.paintStat("Hunting: " + huntingImpName);
        paint.paintStat("Imps Caught: ", totalCatchCount);
        paint.paintStat("Gold gained: ", goldGained);
        paint.paintStatPerHour("Gold/Hour: ", goldGained);
        paint.paintStat("Hunter Level: " + HUNTER.getLevel() + "(" + lvlsGained + ")");
        paint.paintStat("Xp gained: ", xpGained);
        paint.paintStatPerHour("Xp/hour: ", xpGained);
        paint.paintLevelProgressBar("Hunter", HUNTER);
    }
    
    // Helper method for paint. Paints a box of the given color
    // around the given point
    private static void drawBox(Graphics g, Point p, Color c)
    {
        if (inPlayScreenBounds(p))
        {
            g.setColor(c);
            g.drawRect(p.x - BOX_SIZE, p.y - BOX_SIZE, BOX_SIZE * 2, BOX_SIZE * 2);
        }
    }

    /**
     * JFrame used to select the imp that will be hunted
     */
    public static class ImpSelectFrame extends JFrame {

        private JList<String> dataList;
        private ImpSelectFrame THIS = this;

        private final String[] names
                = {
                    "Baby (17)",
                    "Young (22)",
                    "Gormet (28)",
                    "Earth (36)",
                    "Essence (42)",
                    "Electic (50)",
                    "Spirit (54)",
                    "Nature (58)",
                    "Magpie (65)",
                    "Ninja (74)",
                    "Pirate (76)",
                    "Dragon (83)",
                    "Zombie (87)",
                    "Kingly (91)"
                };

        private final int[] nameIDs
                = {
                    6055, //Baby
                    6056, //Young
                    6057, //Gormet
                    6058, //Earth
                    6059, //Essence
                    6060, // Electic
                    6341, //Spirit
                    6061, //Nature
                    6062, //Magpie
                    6053, //Ninja
                    6340, //Pirate
                    6054, //Dragon
                    6342, //Zombie
                    6343 //King
                };

        public ImpSelectFrame() {
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setResizable(false);
            setLocation(0, 0); // **** I want to change this to setRelativeTo(...)
            setSize(300, 400);
            setLayout(new BorderLayout());

            // Create our list
            dataList = new JList<>(names);
            JScrollPane scrollPane = new JScrollPane(dataList);
            this.add(scrollPane, BorderLayout.CENTER);

            // Button
            JButton button = new JButton("Start!");
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int selectedIndex = dataList.getSelectedIndex();
                    if (selectedIndex == -1) {
                        JOptionPane.showMessageDialog(THIS, "Please select an impling.");
                    } else {
                        huntingImpName = dataList.getSelectedValue();
                        huntingImpID = nameIDs[selectedIndex];
                        dispose();
                    }
                }
            });
            this.add(button, BorderLayout.SOUTH);

            /**
             * If this window is closed before an imp can be selected, I wanted
             * to flag the script to make sure it knows. So by setting impID=-1,
             * I can check and stop the script if I have impID=-1.
             */
            this.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    // Check to see if a selection was made.
                    if (huntingImpID == 0) {
                        huntingImpID = -1;
                        JOptionPane.showMessageDialog(THIS, "The script has stopped.");
                    }
                }
            });
            setVisible(true);
        }
    }

    /**
     * Determines whether or not the given point is in the players play screen.
     * That is, the part of the screen which contains the map (not minimap)
     * Areas such as the chat box and inventory are considered out of bounds.
     */
    public static boolean inPlayScreenBounds(Point p) {
        return p.x >= 0 && p.x <= 515 && p.y >= 0 && p.y <= 338;
    }

    /**
     * Calculates the distance between tiles a and b using the Pythagorean
     * theorem.
     */
    private static double answer;

    public static double distance(Tile a, Tile b) {
        answer = Math.sqrt(Math.pow(a.getX() - b.getX(), 2)
                + Math.pow(a.getY() - b.getY(), 2));
        return answer;
    }

    /**
     * Rotates the camera the given number of degrees.
     *
     * @param degrees
     */
    public static void rotateCamera(int degrees) {
        int deg = Camera.getAngle() + degrees;
        if (deg > 360) {
            deg %= 360;
        }
        while (deg < 0) {
            deg += 360;
        }
        Camera.setRotation(deg);
    }

    /**
     * Paints a dynamic GUI. Create an instance of this class in your onExecute().
     * In your paint method, you may call any of the setter methods or paint methods.
     * Before calling a paint method, it is required that you call the 
     * method "void startPaint(Graphics g)" and pass your Paint methods Graphics
     * object.
     * @author Collin 11
     * @version 1.0
     * @since 1/31/2014
     */
    public static final class Paint_System
    {
        // Instance variables
        private int recX, recY, wid, height, mode;
        
        // Paint variables
        private Color backgroundColor = new Color(83, 77, 52, 150);
        private Color forgroundColor = Color.green;
        private int x, y;
        private Graphics g;
        private final long startTime;
        private String title;
        
        public static final int ANCHOR_DOWN = 0;
        public static final int ANCHOR_UP = 1;
        private static final int BUF = 5, SPACE = 15;
        private static final Font FONT = new Font("Verdana", Font.PLAIN, 12);
        private static final DecimalFormat C_FORMAT = new DecimalFormat("#,###");
        private static final Font TITLE_FONT = new Font("Verdana", Font.BOLD, 12);
        
        /**
         * Initializes a new Paint_System with the given variables
         * @param x X-position
         * @param y Y-position
         * @param widPX the width in pixels
         * @param itemHeight the number of items you wish to add (you may add an
         * item by using any method that begins with 'paint')
         * @param anchorType ANCHOR_DOWN anchors the lower-left corner of this
         * GUI to the point given. ANCHOR_UP anchors the upper-left corner of
         * this GUI to the point given.
         */
        public Paint_System(int x, int y, int widPX, int itemHeight, int anchorType, String title)
        {
            startTime = System.currentTimeMillis();
            this.recX = x + BUF;
            changeItemHeight(itemHeight+1);
            this.recY = y - height + BUF;
            this.wid = widPX;
            this.mode = anchorType;
            this.title = title;
        }
        
        /**
         * Change the number of items you plan on adding
         */
        public void changeItemHeight(int height)
        {
            this.height = height*SPACE + 2*BUF;
        }
        
        /**
         * Resets variables. Call this method before calling any other 
         * paint methods of this class.
         */
        public void startPaint(Graphics graphics)
        {
            g = graphics;
            x = recX;
            y = recY + SPACE - 4;
            paintBackground();
            paintTitle();
        }
        
        /**
         * Set the GUI background color to the given color
         */
        public void setBackgroundColor(Color color)
        {
            backgroundColor = color;
        }
        
        /**
         * Set the GUI text color to the given color
         */
        public void setForgroundColor(Color color)
        {
            forgroundColor = color;
        }
        
        /**
         * Paint the background
         */
        private void paintBackground()
        {
            g.setColor(backgroundColor);
            g.fillRoundRect(recX - BUF, recY - BUF, wid, height, BUF, BUF);
            g.setColor(Color.BLACK);
            g.drawRoundRect(recX - BUF, recY - BUF, wid, height, BUF, BUF);
        }
        
        /**
         * Paint the given String
         */
        public void paintStat(String label)
        {
            g.setColor(forgroundColor);
            g.drawString(label, x, y+1);
            movePaintLocation();
        }
        
        /**
         * Paints the title using a special font
         */
        public void paintTitle()
        {
            g.setFont(TITLE_FONT);
            paintStat(title);
            g.setFont(FONT);
            g.setColor(forgroundColor);
        }
        
        /**
         * Paints the given stat and its label
         */
        public void paintStat(String label, long stat)
        {
            paintStat(label + format(stat));
        }
        
        /**
         * Paints the given stat as a per hour variable and its label
         */
        public void paintStatPerHour(String label, long stat)
        {
            double factor = 3600000.0 / ((System.currentTimeMillis() - startTime));
            paintStat(label, (int)(stat*factor));
        }
        
        /**
         * Paints the current run time in HH:MM:SS format
         */
        public void paintTime(String title)
        {
            paintStat(title + formatRunTime(startTime));
        }
        
        /**
         * Paints a visual level progress for the given Skill
         * Displays the name given, the % done for the skills current level,
         * and the experience until leveling.
         * @param name The name you wish to display as the title of this
         * progress bar.
         * @param skill The skill that this progress bar should be for.
         */
        public void paintLevelProgressBar(String name, Skill skill)
        {
            int tempY = y-(SPACE-4);
            int barWid = (wid-2*BUF);
            int xpForNextLevel = skill.getExperienceByLevel(skill.getLevel() + 1);
            int xpForCurrentLevel = skill.getExperienceByLevel(skill.getLevel());
            double percentDone;
            if (skill.getLevel() != 99) {
                percentDone = (skill.getExperience() - xpForCurrentLevel)
                        / (double) (xpForNextLevel - xpForCurrentLevel);
            } else {
                percentDone = 1;
            }
            int xpToLevel = skill.getRemaining();
            int greenpx = (int) (barWid * percentDone);
            g.setColor(new Color(0, 255, 0, 100));
            g.fillRect(x, tempY, greenpx, SPACE-1);
            g.setColor(new Color(255, 0, 0, 50));
            g.fillRect(x + greenpx, tempY, barWid - greenpx, SPACE-1);
            g.setColor(Color.black);
            g.drawRect(x, tempY, barWid, SPACE-1);
            g.drawString(name + " " + (int) (percentDone * 100) + "% (" + 
                    format(xpToLevel) + " xp)", x + BUF, tempY + SPACE - 3);
            movePaintLocation();
        }
        
        /**
         * Updates the location painting location depending on the MODE
         */
        private void movePaintLocation()
        {
            y += SPACE;
        }
        
        /**
         * Formats the given long to String "hh:mm:ss" format
         */
        private long tempMillis, tempHours, tempMinutes, tempSeconds;
        public String formatRunTime(long since) {
            DecimalFormat nf = new DecimalFormat("00");
            tempMillis = System.currentTimeMillis() - since;
            tempHours = tempMillis / (1000 * 60 * 60);
            tempMillis -= tempHours * (1000 * 60 * 60);
            tempMinutes = tempMillis / (1000 * 60);
            tempMillis -= tempMinutes * (1000 * 60);
            tempSeconds = tempMillis / 1000;
            return nf.format(tempHours) + ":" + nf.format(tempMinutes) + ":"
                    + nf.format(tempSeconds);
        }
        
        /**
         * Formats the given number to common runescape format.
         * @param num Number to format
         * @return String format of the given number
         */
        public String format(long num)
        {
            String postfix = "";
            if (num >= 100_000_000)
            {
                num /= 1_000_000;
                postfix = "m";
            }
            else if (num >= 100_000)
            {
                num /= 1_000;
                postfix = "k";
            }
            return C_FORMAT.format(num)+postfix;
        }
        
    }
    
}
