package org.kentfieldschools.appleidbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;

public class AppleIdMailboxProcessor {
	
	Pattern emailPattern = Pattern.compile("recently\\s+selected\\s+([^\\s]+)");
	Pattern linkPattern = Pattern.compile("Verify\\s+now\\s+([^\\s]+)");
	String fileName = null; 
	String accountEmailAddress = null;
	String accountPassword = null;
	FileWriter writer = null;

	AppleIdMailboxProcessor(String email, String password, String fname) {
		try {
			readProperties();
		} catch (IOException ex) {
			
		}
		if (fname != null && !fname.isEmpty()) {
			fileName = fname;
		}
		if (email != null && !email.isEmpty()) {
			accountEmailAddress = email;
		}
		if (password != null && !password.isEmpty()) {
			accountPassword = password;
		}
	}

	private void readProperties() throws IOException {
		String propFileName = "config.properties"; 
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName); 
		if (inputStream != null) {
			Properties prop = new Properties();
			prop.load(inputStream);
			fileName = prop.getProperty("csvFile");
			System.out.println("fileName set to " + fileName);
			accountEmailAddress = prop.getProperty("accountEmailAddress");
			System.out.println("email set to " + accountEmailAddress);
			accountPassword = prop.getProperty("accountPassword");
			System.out.println("password set to " + accountPassword);
		} else {
			System.out.println("could not load " + propFileName);
		}
	}
	
	private String getCsvPath() {
		String homeDir = System.getProperty("user.home");
		String filePath = homeDir + File.separator + "Downloads" + File.separator + fileName;
		return filePath;
	}
	
	public void parseInboxToFile() throws IOException, MessagingException {
		writer = new FileWriter(getCsvPath(), false);

		Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imaps");
		Session session = Session.getInstance(props, null);
		Store store = session.getStore();
		store.connect("imap.gmail.com", accountEmailAddress, accountPassword);
		Folder inbox = store.getFolder("INBOX");
		inbox.open(Folder.READ_ONLY);
		for (int i = 0; i < inbox.getMessageCount(); i++) {
			Message msg = inbox.getMessage(inbox.getMessageCount() - i);
			String subject = msg.getSubject();
			if (subject.equals("Verify your Apple ID.")) {
				// System.out.println(msg);
				Multipart multi = (Multipart) msg.getContent();
				int parts = multi.getCount();
				for (int j = 0; j < parts; j++) {
					BodyPart thePart = multi.getBodyPart(j);
					String contentType = thePart.getContentType();
					if (contentType.startsWith("TEXT/PLAIN")) {
						this.writePartToFile(thePart);
					}
				}
			}
		}
		writer.close();
	}

	private void writePartToFile(BodyPart thePart) throws IOException,
			MessagingException {
		InputStream is = thePart.getInputStream();
		StringBuilder inputStringBuilder = new StringBuilder();
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(is, "UTF-8"));
		String line = bufferedReader.readLine();
		while (line != null) {
			inputStringBuilder.append(line);
			inputStringBuilder.append('\n');
			line = bufferedReader.readLine();
		}
		String s = inputStringBuilder.toString();
		Matcher emailMatcher = emailPattern.matcher(s);
		Matcher linkMatcher = linkPattern.matcher(s);
		if (emailMatcher.find() && linkMatcher.find()) {
			String emailAddress = emailMatcher.group(1);
			String linkUrl = linkMatcher.group(1);
			System.out.println(emailAddress + "," + linkUrl);

			writer.write(emailAddress + "," + linkUrl + "\n");
		} else {
			System.out.println("no matches in " + s);
		}
	}

	public static void main(String[] args) {
		AppleIdMailboxProcessor confirm = new AppleIdMailboxProcessor(null, null, null);
		try {
			confirm.parseInboxToFile();
		} catch (Exception mex) {
			mex.printStackTrace();
		}
	}
}
