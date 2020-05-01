package com.thatcherdev.attendancebot;

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message;


public class EmailSender {

	private final String senderEmail;
	private final String senderPassword;
	private final String receiverEmail;

	public EmailSender(String senderEmail, String senderPassword, String receiverEmail) {
		this.senderEmail = senderEmail;
		this.senderPassword = senderPassword;
		this.receiverEmail = receiverEmail;
	}

	/**
	 * Checks credentials {@code address} and {@code password} to be valid.
	 *
	 * @param email    G-Mail address
	 * @param password supposed password of {@code address}
	 * @return boolean if credentials are valid
	 */
	public static boolean checkCreds(String email, String password) {
		try {
			Properties properties = new Properties();
			properties.put("mail.smtp.host", "smtp.gmail.com");
			properties.put("mail.smtp.port", "465");
			properties.put("mail.smtp.auth", "true");
			properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(email, password);
				}
			});
			Transport transport = session.getTransport("smtp");
			transport.connect(email, password);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Sends email with content {@code content} from {@link #senderEmail}to {@link #receiverEmail}.
	 *
	 * @param content content of email to send
	 * @throws MessagingException
	 */
	public void sendEmail(String content) throws MessagingException {
		Properties properties = new Properties();
		properties.put("mail.smtp.host", "smtp.gmail.com");
		properties.put("mail.smtp.port", "465");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(senderEmail, senderPassword);
			}
		});
		Message message = new MimeMessage(session);
		message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(receiverEmail));
		message.setText(content);
		Transport.send(message);
	}
}