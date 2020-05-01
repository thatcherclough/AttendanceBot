package com.thatcherdev.attendancebot;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class Bot {

	private final String email;
	private final String password;
	private final String googleClassLink;
	private final Map<String, String> cssSelectorsWithActions;
	private final int defaultWait;

	public Bot(String chromedriverExecutable, String email, String password, String googleClassLink, Map<String, String> cssSelectorsWithActions, int defaultWait) {
		System.setProperty("webdriver.chrome.driver", chromedriverExecutable);
		this.email = email;
		this.password = password;
		this.googleClassLink = googleClassLink;
		this.cssSelectorsWithActions = cssSelectorsWithActions;
		this.defaultWait = defaultWait;
	}

	/**
	 * Starts bot.
	 * <p>
	 * Logs into Google Classroom with {@link #email} and {@link #password}. Goes to
	 * Google Classroom page {@link #googleClassLink} and clicks on a post with the
	 * current month and day of the month in the title. Searches through the attachments
	 * of this post to find a Google Forms link. Opens this link and fills out the form
	 * according to {@link #cssSelectorsWithActions}.
	 * </p>
	 */
	public void start() {
		while (true) {
			WebDriver driver = null;
			try {
				driver = new ChromeDriver();
				driver.get("https://classroom.google.com/u/0/h");
				Thread.sleep(defaultWait);

				try {
					login(email, password, defaultWait, driver);
					Thread.sleep(defaultWait);
				} catch (Exception e) {
					if (driver.findElement(By.cssSelector("*")).getText().contains("Couldn't find your Google Account") || driver.findElement(By.cssSelector("*")).getText().contains("Wrong password"))
						throw new Exception("Incorrect credentials");
					else
						throw e;
				}

				driver.get(googleClassLink);
				Thread.sleep(defaultWait);

				try {
					WebElement post = getPostForDate(LocalDate.now().getMonth().toString(), Integer.toString(LocalDate.now().getDayOfMonth()), driver);
					post.click();
					Thread.sleep(defaultWait);
				} catch (Exception e) {
					if (e.getMessage() == null)
						throw new Exception("Post for today's date not found");
					else
						throw e;
				}

				String googleFormsURL = getGoogleFormsURLFromPostAttachments(driver);
				driver.get(googleFormsURL);
				Thread.sleep(defaultWait);

				try {
					fillOutForm(cssSelectorsWithActions, defaultWait / 10, driver);
					Thread.sleep(defaultWait);
				} catch (Exception e) {
					if (driver.findElement(By.cssSelector("*")).getText().contains("You've already responded"))
						throw new Exception("Already checked in");
					else
						throw e;
				}
				AttendanceBot.log("Checked in for " + email);
				break;
			} catch (Exception e) {
				String logMessage = "Could not check in for " + email;
				if (e.getMessage() != null && e.getMessage().contains("\n"))
					logMessage = logMessage + ": " + e.getMessage().substring(0, e.getMessage().indexOf("\n"));
				else if (e.getMessage() != null)
					logMessage = logMessage + ": " + e.getMessage();
				if (!(logMessage.contains("Incorrect credentials") || logMessage.contains("Already checked in")))
					logMessage = logMessage + ". Trying again...";
				AttendanceBot.log(logMessage);

				if (logMessage.contains("Incorrect credentials") || logMessage.contains("Already checked in"))
					break;
			} finally {
				if (driver != null)
					driver.close();
			}
		}
	}

	/**
	 * Logs into Google Classroom with {@code email} and {@code password}
	 *
	 * @param email    email for Google Classroom account
	 * @param password password to {@code email}
	 * @param wait     default wait between actions
	 * @param driver   WebDriver
	 * @throws InterruptedException
	 */
	private static void login(String email, String password, int wait, WebDriver driver) throws InterruptedException {
		WebElement emailInput = driver.findElement(By.cssSelector("#identifierId"));
		WebElement nextButton = driver.findElement(By.cssSelector(".RveJvd"));
		emailInput.sendKeys(email);
		Thread.sleep(wait / 10);
		nextButton.click();

		Thread.sleep(wait);

		WebElement passwordInput = driver.findElement(By.cssSelector(".I0VJ4d > div:nth-child(1) > input:nth-child(1)"));
		WebElement submitButton = driver.findElement(By.cssSelector("#passwordNext > span:nth-child(3) > span:nth-child(1)"));
		passwordInput.sendKeys(password);
		Thread.sleep(wait / 10);
		submitButton.click();
	}

	/**
	 * Searches through a Google Class post stream for a post with month {@code month} and day of month {@code day} in the title.
	 *
	 * @param month  current month
	 * @param day    current day of month
	 * @param driver WebDriver
	 * @return the WebElement of the post with {@code month} and {@code} day in the title
	 */
	private static WebElement getPostForDate(String month, String day, WebDriver driver) {
		WebElement ret = null;

		List<WebElement> posts = driver.findElement(By.cssSelector("#ow43 > div:nth-child(2)")).findElements(By.cssSelector("*"));
		for (WebElement post : posts)
			if (post.getText().toLowerCase().contains(month.toLowerCase()) && post.getText().contains(day)) {
				ret = post;
				break;
			}
		return ret;
	}

	/**
	 * Searches through the attachments of a Google Classroom post for a Google Forms URL.
	 *
	 * @param driver WebDriver
	 * @return the Google Forms URL attached to the current Google Classroom post
	 */
	private static String getGoogleFormsURLFromPostAttachments(WebDriver driver) {
		String ret = null;

		List<WebElement> postAttachments = driver.findElement(By.cssSelector("#yDmH0d > div.v7wOcf.ZGnOx > div.kdAl3b > div > div.fJ1Vac > div.EE538 > div.KdU5eb.fIXHld" +
				".SvPv8d")).findElements(By.cssSelector("*"));
		for (WebElement element : postAttachments)
			if (element.getAttribute("href") != null && element.getAttribute("href").contains("docs.google.com/forms")) {
				ret = element.getAttribute("href");
				break;
			}
		return ret;
	}

	/**
	 * Cycles through {@code cssSelectorsWithActions}. For each entry, the key is a
	 * cssSelector and the value is either 'click' or another string. If the value
	 * is 'click', the WebElement with the cssSelector of the key is clicked, otherwise
	 * the value string is typed into the WebElement with the cssSelector of the key.
	 *
	 * @param cssSelectorsWithActions cssSelectors along with wither 'click' or another string
	 * @param wait                    default wait between actions
	 * @param driver                  WebDriver
	 * @throws InterruptedException
	 */
	private static void fillOutForm(Map<String, String> cssSelectorsWithActions, int wait, WebDriver driver) throws InterruptedException {
		for (Map.Entry<String, String> entry : cssSelectorsWithActions.entrySet()) {
			WebElement element = driver.findElement(By.cssSelector(entry.getKey()));

			String action = entry.getValue();
			if (action.equals("click"))
				element.click();
			else
				element.sendKeys(action);

			Thread.sleep(wait);
		}
	}
}