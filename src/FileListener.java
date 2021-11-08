import java.io.IOException;
import java.nio.file.Paths;

import main.watch.WatchDir;

/**
 * Main class that will listen for file changes to the cups file to see if a new
 * receipt has been created (Print Job). If the file that has been inserted does
 * not start with d then it will ignore it. If the file is not an ENTRY_CREATE
 * then it will ignore it as well.
 * 
 * @author Seth Hancock
 * @since November 4, 2021
 */
public class FileListener {

    /**
     * Main class that will run the {@link WatchDir} class to listen to file changes
     * in the folder that is passed in.
     * 
     * @param args The arguements to run with the watch.
     * @throws IOException If the file can not be found.
     */
    public static void main(String[] args) throws Exception {
        // register directory and process its events
        new WatchDir(Paths.get(args[0])).processEvents();
    }
}
