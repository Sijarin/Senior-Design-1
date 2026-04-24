package com.finvision.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing a registered Finvision user.
 *
 * <p>Stores authentication credentials (username, BCrypt-hashed password and PIN),
 * account recovery data (security question/answer), and profile information
 * (display name, profile photo, virtual account/routing numbers).</p>
 *
 * <p>Stored in the {@code users} collection.</p>
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;
    private String email; // Optional — can be used later for notifications or password reset links
    private String securityQuestion;
    private String securityAnswer;

    // Profile fields
    private String name;
    private String accountNumber;
    private String routingNumber;
    private String profilePhoto; // base64 data URL
    private String pin; // BCrypt-hashed 4-digit PIN for account recovery

    // ---------- Constructors ----------
    public User() {}

    public User(String username, String password, String email, String securityQuestion, String securityAnswer) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.securityQuestion = securityQuestion;
        this.securityAnswer = securityAnswer;
    }

    // ---------- Getters and Setters ----------
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSecurityQuestion() {
        return securityQuestion;
    }

    public void setSecurityQuestion(String securityQuestion) {
        this.securityQuestion = securityQuestion;
    }

    public String getSecurityAnswer() {
        return securityAnswer;
    }

    public void setSecurityAnswer(String securityAnswer) {
        this.securityAnswer = securityAnswer;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getRoutingNumber() { return routingNumber; }
    public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }

    public String getProfilePhoto() { return profilePhoto; }
    public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
}
