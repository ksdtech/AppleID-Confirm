package org.kentfieldschools.appleidbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import org.watij.webspec.dsl.Tag;
import org.watij.webspec.dsl.WebSpec;

public class AppleIdBrowserBot {
	private static Logger LOGGER = Logger.getLogger(AppleIdBrowserBot.class
			.getSimpleName());

	public enum ConfirmationResults {
		PARSE_FAILED, ALREADY_VERIFIED, EXPIRED_AND_RESEND_FAILED, EXPIRED_AND_RESENT, CONFIRMATION_FAILED, CONFIRMATION_SUCCEEDED
	};

	WebSpec spec = null;
	String appleIdPassword = null;
	String fileName = null;

	public AppleIdBrowserBot(String fname, String password) {
		try {
			readProperties();
		} catch (Exception ex) {
			System.out.println("failed to read properties");
		}
		if (fname != null && !fname.isEmpty()) {
			fileName = fname;
		}
		if (password != null && !password.isEmpty()) {
			appleIdPassword = password;
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
			appleIdPassword = prop.getProperty("appleIdPassword");
			System.out.println("password set to " + appleIdPassword);
		} else {
			System.out.println("could not load " + propFileName);
		}
	}
	
	private String getCsvPath() {
		String homeDir = System.getProperty("user.home");
		String filePath = homeDir + File.separator + "Downloads" + File.separator + fileName;
		return filePath;
	}
	
	private WebSpec getSpec() {
		if (spec == null) {
			spec = new WebSpec().mozilla();
		}
		return spec;
	}

	private ConfirmationResults confirmAppleId(String url, String password) {

		ConfirmationResults results = ConfirmationResults.PARSE_FAILED;
		boolean parsed = true;
		
		getSpec().open(url);
		Tag columnLastTag = spec.find("div").with("className",
				"/column last main/");
		if (!columnLastTag.exists()) {
			LOGGER.info("no column last");
			parsed = false;
		}

		Tag introTag = null;
		if (parsed) {
			introTag = columnLastTag.find("p").with("className", "/intro/");
			if (!introTag.exists()) {
				LOGGER.info("no intro");
				parsed = false;
			}
		}

		if (parsed) {
			String introText = introTag.get.innerText();
			if (introText.contains("has already been verified") == true) {
				// then email address is already verified -- skip it
				results = ConfirmationResults.ALREADY_VERIFIED;

			} else if (introText.contains("Your verification link has expired")) {
				// then need to click resend button
				Tag link = columnLastTag.find("a").with("className",
						"/btn bigblue center/");
				if (!link.exists()) {
					LOGGER.info("no button link");
					parsed = false;
				}

				if (parsed) {
					link.click();
					results = ConfirmationResults.EXPIRED_AND_RESENT;
				}
			} else {
				// email address ready to be verified
				Tag button = null;
				Tag link = null;
				String appleId = null;

				Tag emailAddressTag = introTag.find("b");
				if (emailAddressTag.exists()) {
					appleId = emailAddressTag.get.innerText();
					LOGGER.info("appleId is " + appleId);
				} else {
					LOGGER.info("no email address");
					parsed = false;
				}

				if (parsed) {
					button = spec.findWithId("signInHyperLink");
					if (button.exists()) {
						link = button.parent("a");
						if (!link.exists()) {
							LOGGER.info("no button link");
							parsed = false;
						}
					} else {
						LOGGER.info("no button");
						parsed = false;
					}
				}

				if (parsed) {
					Tag appleIdField = spec.findWithId("accountname");
					if (appleIdField.exists()) {
						appleIdField.set.value(appleId);
					} else {
						LOGGER.info("no accountname");
						parsed = false;
					}
				}

				if (parsed) {
					Tag passwordField = spec.findWithId("accountpassword");
					if (passwordField.exists()) {
						passwordField.set.value(password);
					} else {
						LOGGER.info("no accountpassword");
						parsed = false;
					}
				}

				if (parsed) {
					button.click();
					// link.click();
					results = ConfirmationResults.CONFIRMATION_SUCCEEDED;
				}
			}
		}
		getSpec().closeAll();
		return results;
	}

	public void processFile() throws IOException {
		FileReader fr = new FileReader(getCsvPath());
		BufferedReader reader = new BufferedReader(fr);

		String line;
		while ((line = reader.readLine()) != null) {
			String[] parts = line.split(",", 2);
			if (parts.length == 2) {
				String appleId = parts[0];
				String url = parts[1];
				System.out.println(appleId);
				if (!appleId.isEmpty() && !url.isEmpty()) {
					ConfirmationResults results = confirmAppleId(parts[1],
							appleIdPassword);
					System.out.println(appleId + "," 
							+ results.toString());
				}
			}
		}
		fr.close();
	}

	public static void main(String[] args) throws IOException {
		AppleIdBrowserBot bot = new AppleIdBrowserBot(null, null);
		bot.processFile();
	}

}
