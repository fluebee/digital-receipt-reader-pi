package main.cloudinary;

import java.io.IOException;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * File uploader class that will upload the receipts to cloudinary so that they
 * can be stored in the S3 bucket and be retrieved by the mobile app.
 * 
 * @author Sam butler
 * @since Novemeer 4, 2021
 */
public class ReceiptCloud {
    private final String CLOUD_NAME = "hwxm9amax";
    private final String API_KEY = "656249988229398";
    private final String API_SECRET = "NO6Ydnn_UIFwAzanYJL3Xm0xkb8";

    private Cloudinary cloud;

    /**
     * Default constructor for when the class is invoked, then the cloudinary can be
     * initialized and used to upload files.
     */
    public ReceiptCloud() {
        cloud = new Cloudinary(ObjectUtils.asMap("cloud_name", CLOUD_NAME, "api_key", API_KEY, "api_secret", API_SECRET,
                "secure", true));
    }

    /**
     * Method that will upload the given file to cloudinary. If the file can not be
     * uploaded then it will print a warnring saying the file can not be uploaded,
     * otherwise it will print a success message.
     * 
     * @param file
     */
    public void upload(String file, String publicId) {
        try {
            cloud.uploader().upload(file, ObjectUtils.asMap("public_id", publicId));
        } catch (IOException e) {
            System.out.println("WARN: Cloudinary Upload Failed!");
        }
    }
}
