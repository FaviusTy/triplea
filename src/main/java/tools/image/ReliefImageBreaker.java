package tools.image;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Panel;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import games.strategy.debug.ClientLogger;
import games.strategy.triplea.ui.mapdata.MapData;
import games.strategy.ui.Util;
import tools.map.making.ImageIoCompletionWatcher;

/**
 * Utility for breaking an image into seperate smaller images.
 * User must make a new directory called "newImages" and then run the utility
 * first.
 * To create sea zones only, he must choose "Y" at the prompt. To create
 * territories, he must choose "N" at the prompt.
 * sea zone images directory must be renamed to "seazone
 */
public class ReliefImageBreaker {
  private static String location = null;
  private static JFrame observer = new JFrame();
  private boolean m_seaZoneOnly;
  private MapData m_mapData;
  private static File s_mapFolderLocation = null;
  private static final String TRIPLEA_MAP_FOLDER = "triplea.map.folder";

  /**
   * Creates a new instance of ReliefImageBreaker and calls createMaps() method to start the computations.
   */
  public static void main(final String[] args) throws Exception {
    handleCommandLineArgs(args);
    JOptionPane.showMessageDialog(null,
        new JLabel("<html>" + "This is the ReliefImageBreaker, it is no longer used. "
            + "<br>It will take any image and finalized map folder, and will create cut out images of the relief art "
            + "<br>for each territory and sea zone."
            + "<br><br>TripleA no longer uses these, and instead uses reliefTiles (use the TileImageBreaker for that)."
            + "</html>"));
    final FileSave locationSelection = new FileSave("Where to save Relief Images?", null, s_mapFolderLocation);
    location = locationSelection.getPathString();
    if (s_mapFolderLocation == null && locationSelection.getFile() != null) {
      s_mapFolderLocation = locationSelection.getFile().getParentFile();
    }
    if (location == null) {
      System.out.println("You need to select a folder to save the tiles in for this to work");
      System.out.println("Shutting down");
      System.exit(0);
      return;
    }
    new ReliefImageBreaker().createMaps();
  }

  /**
   * One of the main methods that is used to create the actual maps. Calls on
   * various methods to get user input and create the maps.
   */
  public void createMaps() throws IOException {
    // ask user to input image location
    final Image map = loadImage();
    if (map == null) {
      System.out.println("You need to select a map image for this to work");
      System.out.println("Shutting down");
      System.exit(0);
    }
    // ask user wether it is sea zone only or not
    m_seaZoneOnly = doSeaZone();

    // ask user where the map is
    final String mapDir = getMapDirectory();
    if (mapDir == null || mapDir.equals("")) {
      System.out.println("You need to specify a map name for this to work");
      System.out.println("Shutting down");
      System.exit(0);
    }
    try {
      m_mapData = new MapData(mapDir);
      // files for the map.
    } catch (final NullPointerException npe) {
      System.out.println("Bad data given or missing text files, shutting down");
      System.exit(0);
    }
    for (final String territoryName : m_mapData.getTerritories()) {
      final boolean seaZone = Util.isTerritoryNameIndicatingWater(territoryName);
      if (!seaZone && m_seaZoneOnly) {
        continue;
      }
      if (seaZone && !m_seaZoneOnly) {
        continue;
      }
      processImage(territoryName, map);
    }
    System.out.println("All Finished!");
    System.exit(0);
  }

  /**
   * Asks user wether to do sea zones only or not
   *
   * @return java.lang.boolean TRUE to do seazones only.
   */
  private static boolean doSeaZone() {
    String ans = "";
    while (true) {
      ans = JOptionPane.showInputDialog(null, "Only Do Sea Zones? Enter [Y/N]");
      if (ans == null) {
        System.out.println("Cannot leave this blank!");
        System.out.println("Retry");
      } else {
        if (ans.equalsIgnoreCase("Y")) {
          return true;
        } else if (ans.equalsIgnoreCase("N")) {
          return false;
        } else {
          System.out.println("You must enter Y or N");
        }
      }
    }
  }

  /**
   * Asks the user to input a valid map name that will be used to form the map
   * directory in the core of TripleA in the class TerritoryData.
   * we need the exact map name as indicated in the XML game file ie."revised"
   * "classic" "pact_of_steel" of course, without the quotes.
   *
   * @return map name entered by the user (if any, null returned if canceled)
   */
  private static String getMapDirectory() {
    final String mapDir = JOptionPane.showInputDialog(null, "Enter the name of the map (ie. revised)");
    if (mapDir != null) {
      return mapDir;
    } else {
      return null;
    }
  }

  /**
   * Asks the user to select an image and then it loads it up into an Image
   * object and returns it to the calling class.
   *
   * @return java.awt.Image img the loaded image
   */
  private static Image loadImage() {
    System.out.println("Select the map");
    final String mapName = new FileOpen("Select The Map", s_mapFolderLocation, ".gif", ".png").getPathString();
    if (mapName != null) {
      final Image img = Toolkit.getDefaultToolkit().createImage(mapName);
      final MediaTracker tracker = new MediaTracker(new Panel());
      tracker.addImage(img, 1);
      try {
        tracker.waitForAll();
        return img;
      } catch (final InterruptedException e) {
        ClientLogger.logQuietly("interrupted while loading images", e);
        return loadImage();
      }
    } else {
      return null;
    }
  }

  private void processImage(final String territory, final Image map) throws IOException {
    final Rectangle bounds = m_mapData.getBoundingRect(territory);
    final int width = bounds.width;
    final int height = bounds.height;
    final BufferedImage alphaChannelImage = Util.createImage(bounds.width, bounds.height, true);
    final Iterator<Polygon> iter = m_mapData.getPolygons(territory).iterator();
    while (iter.hasNext()) {
      Polygon item = iter.next();
      item = new Polygon(item.xpoints, item.ypoints, item.npoints);
      item.translate(-bounds.x, -bounds.y);
      alphaChannelImage.getGraphics().fillPolygon(item);
    }
    final GraphicsConfiguration m_localGraphicSystem =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    final BufferedImage relief = m_localGraphicSystem.createCompatibleImage(width, height,
        m_seaZoneOnly ? Transparency.BITMASK : Transparency.TRANSLUCENT);
    relief.getGraphics().drawImage(map, 0, 0, width, height, bounds.x, bounds.y, bounds.x + width, bounds.y + height,
        observer);
    blankOutline(alphaChannelImage, relief);
    String outFileName = location + File.separator + territory;
    if (!m_seaZoneOnly) {
      outFileName += "_relief.png";
    } else {
      outFileName += ".png";
    }
    ImageIO.write(relief, "png", new File(outFileName));
    System.out.println("wrote " + outFileName);
  }

  /**
   * Sets the alpha channel to the same as that of the base image.
   */
  private static void blankOutline(final Image alphaChannelImage, final BufferedImage relief) {
    final Graphics2D gc = (Graphics2D) relief.getGraphics();

    final Composite prevComposite = gc.getComposite();
    gc.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
    /*
     * draw the image, and check for the possibility it doesn't complete now
     */
    final ImageIoCompletionWatcher watcher = new ImageIoCompletionWatcher();
    final boolean drawComplete = gc.drawImage(alphaChannelImage, 0, 0, watcher);
    // use the watcher to for the draw to finish
    if (!drawComplete) {
      watcher.waitForCompletion();
    }
    // cleanup
    gc.setComposite(prevComposite);
  }

  private static String getValue(final String arg) {
    final int index = arg.indexOf('=');
    if (index == -1) {
      return "";
    }
    return arg.substring(index + 1);
  }

  private static void handleCommandLineArgs(final String[] args) {
    // arg can only be the map folder location.
    if (args.length == 1) {
      String value;
      if (args[0].startsWith(TRIPLEA_MAP_FOLDER)) {
        value = getValue(args[0]);
      } else {
        value = args[0];
      }
      final File mapFolder = new File(value);
      if (mapFolder.exists()) {
        s_mapFolderLocation = mapFolder;
      } else {
        System.out.println("Could not find directory: " + value);
      }
    } else if (args.length > 1) {
      System.out.println("Only argument allowed is the map directory.");
    }
    // might be set by -D
    if (s_mapFolderLocation == null || s_mapFolderLocation.length() < 1) {
      final String value = System.getProperty(TRIPLEA_MAP_FOLDER);
      if (value != null && value.length() > 0) {
        final File mapFolder = new File(value);
        if (mapFolder.exists()) {
          s_mapFolderLocation = mapFolder;
        } else {
          System.out.println("Could not find directory: " + value);
        }
      }
    }
  }
}
