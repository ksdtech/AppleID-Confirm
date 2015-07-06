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
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Flags.Flag;
import javax.mail.search.FlagTerm;

import org.kentfieldschools.appleidbot.*;

public class AppleIdMailboxProcessor {
	
	Pattern emailPattern = Pattern.compile("recently\\s+selected\\s+([^\\s]+)");
	Pattern linkPattern = Pattern.compile("Verify\\s+now\\s+([^\\s]+)");
	String fileName = null; 
	String accountEmailAddress = null;
	String accountPassword = null;
	String appleIdPassword = null;
	FileWriter writer = null;
	AppleIdBrowserBot browser = null;
	String EMPTY_ARRAY[] = { };

	AppleIdMailboxProcessor(String email, String password, String applePassword, String fname) {
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
		if (applePassword != null && !applePassword.isEmpty()) {
			appleIdPassword = applePassword;
		}
		browser = new AppleIdBrowserBot(null, null);
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
			System.out.println("account password set to " + accountPassword);
			appleIdPassword = prop.getProperty("appleIdPassword");
			System.out.println("Apple ID password set to " + appleIdPassword);
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
		inbox.open(Folder.READ_WRITE);
		
		Message messages[] = inbox.search(new FlagTerm(
                    new Flags(Flag.SEEN), false));
		for (int i = 0; i < messages.length; i++) {
			Message msg = messages[i];
			String subject = msg.getSubject();
			if (subject.equals("Verify your Apple ID.")) {
				// System.out.println(msg);
				// This marks the message as SEEN
				Multipart multi = (Multipart) msg.getContent();
				boolean markAsUnseen = true;
				int parts = multi.getCount();
				for (int j = 0; j < parts; j++) {
					BodyPart thePart = multi.getBodyPart(j);
					String contentType = thePart.getContentType();
					if (contentType.startsWith("TEXT/PLAIN")) {
						String [] emailAndLink = this.getEmailAndLink(thePart);
						if (emailAndLink.length == 2) {
							int msgnum = msg.getMessageNumber();
							String emailAddress = emailAndLink[0];
							String linkUrl = emailAndLink[1];
							AppleIdBrowserBot.ConfirmationResults results = browser.confirmAppleId(linkUrl, appleIdPassword);
							System.out.println("Message Number " + String.valueOf(msgnum) + ", Apple ID " + emailAddress + ", results were " + results);
							markAsUnseen = false;
						}
					}
				}
				if (markAsUnseen) {
					msg.setFlag(Flag.SEEN, false);
				}
			}
		}
		writer.close();
		browser.close();
	}

	private String [] getEmailAndLink(BodyPart thePart) throws IOException,
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
			String retval[] = {
				emailMatcher.group(1),
				linkMatcher.group(1) 
			};
			return retval;
		} else {
			System.out.println("no matches in " + s);
			return EMPTY_ARRAY;
		}
	}
	
	private String [] writePartToFile(BodyPart thePart) throws IOException,
			MessagingException {
			String [] emailAndLink = this.getEmailAndLink(thePart);
			if (emailAndLink.length == 2) {
				String emailAddress = emailAndLink[0];
				String linkUrl = emailAndLink[1];
	
				System.out.println(emailAddress + "," + linkUrl);
				writer.write(emailAddress + "," + linkUrl + "\n");
			}
			return emailAndLink;
    }

	public static void main(String[] args) {
		AppleIdMailboxProcessor confirm = new AppleIdMailboxProcessor(null, null, null, null);
		try {
			confirm.parseInboxToFile();
		} catch (Exception mex) {
			mex.printStackTrace();
		}
	}
}
