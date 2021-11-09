package main.watch;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

import main.cloudinary.ReceiptCloud;
import main.domain.APIClient;
import main.domain.model.Receipt;
import main.python.NfcTagWriter;

/**
 * Example to watch a directory (or tree) for changes to files. This code has
 * been modified to only act on creation of files starting with the letter 'd'
 * 
 * @author Seth Hancock
 * @since November 4, 2021
 */
@SuppressWarnings("all")
public class WatchDir {

    private PrintStream baseStream;
    private PrintStream disabledStream;
    private ReceiptCloud receiptCloud;
    private APIClient apiclient;
    private NfcTagWriter nfcTagWriter;

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private boolean trace = false;

    /**
     * Default constructor that creates a WatchService and registers the given
     * directory that was passed in from the terminal.
     * 
     * @param dir The directory that needs to be watched.
     */
    public WatchDir(Path dir) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();

        initalizeClients();
        register(dir);

        this.trace = true; // enable trace after initial registration
        printConsole("Listening to Directory...");
    }

    /**
     * Helper method that will initalize the clients that are being used within the
     * class.
     */
    private void initalizeClients() {
        this.baseStream = System.out;
        initDisabledStream();

        disableLogging();
        this.receiptCloud = new ReceiptCloud();
        this.nfcTagWriter = new NfcTagWriter();

        printConsole("INFO: Authenticating User Client...");
        this.apiclient = new APIClient("pi@admin.com", "piadmin");
        printConsole(String.format("Authentication Complete for user %s!\n", "pi@admin.com"));
    }

    /**
     * Register the given directory with the WatchService
     * 
     * @param dir The directory to register.
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
     * Process all events for keys queued to the watcher
     * 
     * @throws IOException
     */
    public void processEvents() {
        boolean loop = true;
        while (loop) {

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

            processReceipt(key);
            loop = resetKey(key);
        }
    }

    /**
     * Once a receipt (Print Job) is added to the directory. This will process that
     * receipt so that it can be inserted into Cloudinary and the database. This
     * will only process receipts that have a {@link Kind} type of ENTRY_CREATE
     * 
     * @param k The key that was generated for the watch.
     * @throws IOException
     */
    private void processReceipt(WatchKey k) {
        Path dir = keys.get(k);

        for (WatchEvent<?> event : k.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            // Context for directory entry event is the file name of entry
            WatchEvent<Path> ev = cast(event);
            Path child = dir.resolve(ev.context());
            String fileName = child.getFileName().toString().toLowerCase();

            // Skip files that do not start with the letter 'd' and are not ENTRY_CREATE
            if ((fileName.charAt(0) != 'd') || (kind != ENTRY_CREATE) || kind == OVERFLOW)
                continue;

            // Print out event
            printConsole(String.format("%s for %s\n", event.kind().name(), child));

            // Store Receipt Data
            storeReceipt(String.format("receipt_%d_%d", apiclient.getAutoIncrement(), generateKey()), child.toString());
        }
    }

    /**
     * Run the commands to store the receipt into the Cloudinary S3 bucket, insert
     * it into the database, and then write the receipt id to the tag.
     * 
     * @param pid      The unique public id of the receipt.
     * @param filePath The path to receipt.
     */
    private void storeReceipt(String pId, String filePath) {
        // 1. Store receipt in S3 bucket
        uploadFile(filePath, pId);

        // 2. Store receipt into database
        Receipt receipt = insertReceiptToDatabase(pId);

        // 3. Write id to NFC tag
        enableLogging();
        nfcTagWriter.write(receipt.getId());
        disableLogging();
    }

    /**
     * This will reset the keys. It will remove the current key that was just
     * processed. If there are no more accessible directories then the method will
     * return false, otherwise it will return true so it can keep processing.
     * 
     * @param k The key that was just processed.
     * @return {@link Boolean} if it should keep processing files.
     */
    private boolean resetKey(WatchKey k) {
        boolean valid = k.reset();
        if (!valid) {
            keys.remove(k);

            // all directories are inaccessible
            if (keys.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * This will upload the file to the cloudinary website in teh S3 bucket so that
     * it can be accessed via id.
     * 
     * @param file     The file to be stored.
     * @param publicId The public id to store the file under.
     */
    private void uploadFile(String file, String publicId) {
        printConsole("Cloudinary uploading...");
        receiptCloud.upload(file, publicId);
        printConsole("Cloudinary Upload Complete!");
    }

    /**
     * This will insert the generated publicId into the database. Once it has
     * inserted it will then return the receipt object so that id can be used to
     * transmit to the reader.
     * 
     * @param publicId The public id to be inserted.
     * @return {@link Receipt} of the generated receipt data.
     */
    private Receipt insertReceiptToDatabase(String publicId) {
        printConsole(String.format("Inserting '%s' to Database...", publicId));
        Receipt receipt = apiclient.insertReceipt(publicId);
        printConsole("Insert Receipt Complete!");

        return receipt;
    }

    /**
     * Generates a random 10 digit value that is used to append to strings for
     * hashing and authentication.
     * 
     * @return {@link long} of the generated salt value.
     */
    private long generateKey() {
        return (long) Math.floor(Math.random() * 9_000_000_000L) + 1_000_000_000L;
    }

    /**
     * This will print the given string to the console. It will enable it so it can
     * print to the console and the disable it so that no other logs from third part
     * libaries are printing to the console.
     * 
     * @param str The string to display to the console.
     */
    private void printConsole(String str) {
        enableLogging();
        System.out.println(String.format("[WatchDir] INFO digital-receipt-reader-pi: %s", str));
        disableLogging();
    }

    /**
     * Disable the logging to the console when third party libaries are called.
     */
    private void disableLogging() {
        System.setOut(disabledStream);
    }

    /**
     * Enable the console log.
     */
    private void enableLogging() {
        System.setOut(baseStream);
    }

    /**
     * Method to init the disable stream object.
     */
    private void initDisabledStream() {
        disabledStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // Disable console
            }
        });
    }

    /**
     * Cast even method to cast data to a {@link WatchEvent} object
     * 
     * @param <T>   Object of the watch event.
     * @param event The even passed in to change.
     * @return {@link WatchEvent} of that even object.
     */
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }
}