package main.python;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Python class to execute the tag writer script to write files to a tag.
 * 
 * @author Seth Hancock
 * @since November 9, 2021
 */
public class NfcTagWriter {
    private String BASE_PATH = "/home/pi/Desktop/Digital Receipt/raspberrypi/python/";
    private String scriptTagWrite = BASE_PATH + "receipt__write_tag.py";
    private String scriptTagClear = BASE_PATH + "receipt__clear_tag.py";

    /**
     * Method to write the given id to the tag. If the id can not be formated to a
     * hex value than it will error and exit the program. Otherwise it will write
     * the tag and continue running.
     * 
     * @param receiptId The id to write the nfc tag.
     */
    public void write(int receiptId) {
        printConsole(String.format("Writing Receipt ID '%d' to Tag...", receiptId));
        writeTagData(formatIntToHex(receiptId));
        printConsole("Write to Tag Complete!");

        printConsole("Waiting 30 seconds for Tag read.");
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            printConsole("ERROR: Could not sleep");
        }

        printConsole("Clearing Tag data...");
        clearTag();
        printConsole("Tag Clear Complete!");

    }

    /**
     * This will format the int value passed in, into 4 bytes that will be stored in
     * a string array.
     * 
     * @param v The value to parse as a 4 byte hex.
     * @return {@link String[]} of the hex values.
     */
    private String[] formatIntToHex(int v) {
        String[] hexValues = new String[4];

        hexValues[0] = Integer.toHexString((v & 0xFF000000) >> 24);
        hexValues[1] = Integer.toHexString((v & 0x00FF0000) >> 16);
        hexValues[2] = Integer.toHexString((v & 0x0000FF00) >> 8);
        hexValues[3] = Integer.toHexString((v & 0x000000FF));

        return hexValues;
    }

    /**
     * Execute the python script with the given hex value arguments. If an error
     * occurs than it will break and print to the console that an error has occured.
     * 
     * @param args The hex values to write to the tag.
     */
    private void writeTagData(String[] args) {
        doesFileExist(scriptTagWrite);
        String[] cmd = { "python3", scriptTagWrite, args[0], args[1], args[2], args[3] };

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            outputScriptConsole(new BufferedReader(new InputStreamReader(p.getInputStream())));
        } catch (IOException e) {
            printConsole("Error Running Pythong Script!");
        }
    }

    /**
     * This will clear the receipt id off of the tag. This will get called once 30
     * seconds have passed.
     */
    private void clearTag() {
        doesFileExist(scriptTagClear);
        String[] cmd = { "python3", scriptTagClear };

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            outputScriptConsole(new BufferedReader(new InputStreamReader(p.getInputStream())));
        } catch (IOException e) {
            printConsole("Error Running Pythong Script!");
        }
    }

    /**
     * This will print out any logs that were printed out in the python script. If
     * there is an error than no logs will printed out.
     * 
     * @param br The buffered reader to loop through.
     * @throws IOException If the buffered reader can not be opened.
     */
    private void outputScriptConsole(BufferedReader br) throws IOException {
        String s = "";
        while ((s = br.readLine()) != null) { // read in the output from the python script
            printConsole(s);
        }
    }

    /**
     * Confirm the python path to the script exists. If it does not then it will
     * print to the console that file does not exist and exit the program.
     */
    private void doesFileExist(String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
        } catch (Exception e) {
            printConsole(String.format("File does not exist: %s", path));
            System.exit(1);
        }
    }

    /**
     * This will print the given string to the console. It will enable it so it can
     * print to the console and the disable it so that no other logs from third part
     * libaries are printing to the console.
     * 
     * @param str The string to display to the console.
     */
    private void printConsole(String str) {
        System.out.println(String.format("[WatchDir] INFO digital-receipt-reader-pi: %s", str));
    }
}
