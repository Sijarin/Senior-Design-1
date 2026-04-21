package com.finvision.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.*;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    public void sendPasswordResetEmail(String toEmail, String username) throws IOException {
        Email from = new Email(fromEmail, "FinVision");
        Email to = new Email(toEmail);
        String subject = "FinVision – Password Reset Request";
        Content content = new Content("text/html",
                "<h2>Password Reset</h2>" +
                        "<p>Hi <strong>" + username + "</strong>,</p>" +
                        "<p>We received a request to reset your FinVision password.</p>" +
                        "<p>Visit the app and use your security question or PIN to complete the reset.</p>" +
                        "<p>If you didn't request this, ignore this email.</p>" +
                        "<br><p>— The FinVision Team</p>");

        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        sg.api(request);
    }

    public void sendWelcomeEmail(String toEmail, String firstName) throws IOException {
        Email from = new Email(fromEmail, "FinVision");
        Email to = new Email(toEmail);
        String subject = "Welcome to FinVision!";
        Content content = new Content("text/html",
                "<h2>Welcome, " + firstName + "!</h2>" +
                        "<p>Your FinVision account is ready. Start by setting your monthly budget to unlock insights, alerts, and goal tracking.</p>"
                        +
                        "<p>— The FinVision Team</p>");

        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        sg.api(request);
    }

    public void sendPasswordChangedEmail(String toEmail, String username) throws IOException {
        Email from = new Email(fromEmail, "FinVision");
        Email to = new Email(toEmail);
        String subject = "FinVision – Your Password Was Changed";
        Content content = new Content("text/html",
                "<h2>Password Changed</h2>" +
                        "<p>Hi <strong>" + username + "</strong>,</p>" +
                        "<p>Your FinVision password was successfully changed.</p>" +
                        "<p>If you did not make this change, please contact support immediately.</p>" +
                        "<br><p>— The FinVision Team</p>");

        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        sg.api(request);
    }
}
