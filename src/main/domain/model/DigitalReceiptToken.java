package main.domain.model;

import java.util.Date;

/**
 * Authentication token to be used within the app.
 *
 * @author Sam Butler
 * @since July 31, 2021
 */
public class DigitalReceiptToken {

    private String token;

    private Date createDate;

    private User user;

    public DigitalReceiptToken() {
    }

    public DigitalReceiptToken(String t, Date creation, User u) {
        token = t;
        createDate = creation;
        user = u;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}