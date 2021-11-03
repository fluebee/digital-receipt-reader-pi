
/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import model.DigitalReceiptToken;
import model.Receipt;

/**
 * Example to watch a directory (or tree) for changes to files.
 */
// Example code modified to only act on creation of files starting with the
// letter 'd'
//
public class WatchDir {

    private static PrintStream baseStream;
    private static PrintStream disabledStream;

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final boolean recursive;
    private boolean trace = false;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    WatchDir(Path dir, boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();
        this.recursive = recursive;

        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }

    /**
     * Process all events for keys queued to the watcher
     * 
     * @throws IOException
     */
    void processEvents() {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // Skip files that do not start with the letter 'd' and are not of kind
                // ENTRY_CREATE
                // String fileName = child.getFileName().toString();
                if ((Character.toLowerCase(child.getFileName().toString().charAt(0)) != 'd') || (kind != ENTRY_CREATE))
                    continue;

                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);

                // On creation of a file, store a byte array of that file to the DB
                /*
                 * try { byte[] byteArray = getByteArrayFromFile(child.toString());
                 * System.out.println("Byte Array Created"); } catch (IOException x) {
                 * System.out.println("File not found"); continue; }
                 */

                // base url: https://digital-receipt-production.herokuapp.com/
                disableLogging();
                DigitalReceiptToken token = APIClient.authenticate();
                String randomFilePublicId = String.format("receipt_%d_%d", APIClient.getAutoIncrement(token.getToken()),
                        generateRandomKey());
                enableLogging();

                Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap("cloud_name", "hwxm9amax", "api_key",
                        "656249988229398", "api_secret", "NO6Ydnn_UIFwAzanYJL3Xm0xkb8", "secure", true));

                System.out.println("Cloudinary Uploading...");
                try {
                    cloudinary.uploader().upload(child.toString(), ObjectUtils.asMap("public_id", randomFilePublicId));
                    System.out.println("Cloudinary Success");
                } catch (IOException e) {
                    System.out.println("Cloudinary Fail");
                }

                System.out.println("Inserting Receipt to Database...");
                disableLogging();
                Receipt receipt = APIClient.insertReceipt(randomFilePublicId, token.getToken());
                enableLogging();
                System.out.println("Insert Receipt Complete");


                // Clean up code
                // receipt.getID(), format it to call the python code

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    static void usage() {
        System.err.println("usage: java WatchDir [-r] dir");
        System.exit(-1);
    }

    /**
     * Generates a random 10 digit value that is used to append to strings for
     * hashing and authentication.
     * 
     * @return {@link long} of the generated salt value.
     */
    public static long generateRandomKey() {
        return (long) Math.floor(Math.random() * 9_000_000_000L) + 1_000_000_000L;
    }

    private static void disableLogging() {
        System.setOut(disabledStream);
    }

    private static void enableLogging() {
        System.setOut(baseStream);
    }

    private static void initDisabledStream() {
        disabledStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // Disable console
            }
        });
    }

    public static void main(String[] args) throws IOException {
        baseStream = System.out;
        initDisabledStream();

        // parse arguments
        if (args.length == 0 || args.length > 2)
            usage();
        boolean recursive = false;
        int dirArg = 0;
        if (args[0].equals("-r")) {
            if (args.length < 2)
                usage();
            recursive = true;
            dirArg++;
        }

        // register directory and process its events
        Path dir = Paths.get(args[dirArg]);
        new WatchDir(dir, recursive).processEvents();
    }
}