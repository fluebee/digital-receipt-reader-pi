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
    private String scriptPath = "/home/pi/Desktop/Digital Receipt/raspberrypi/python/example_rw_ntag2.py";

    /**
     * Method to write the given id to the tag. If the id can not be formated to a
     * hex value than it will error and exit the program. Otherwise it will write
     * the tag and continue running.
     * 
     * @param receiptId The id to write the nfc tag.
     */
    public void write(int receiptId) {
        printConsole(String.format("Executing Python script with Receipt ID '%d'...", receiptId));
        executeScript(formatIntToHex(receiptId));
        printConsole("Python Write to Tag Complete!");
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
    private void executeScript(String[] args) {
        doesFileExist();
        String[] cmd = { "python3", scriptPath, args[0], args[1], args[2], args[3] };

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
    private void doesFileExist() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(scriptPath));
        } catch (Exception e) {
            printConsole("File does not exist");
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
